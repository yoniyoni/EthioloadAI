<?php

namespace App\Services;

use App\Models\Bid;
use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\Rating;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

class BidService
{
    public function __construct(private RoutingService $routing) {}

    /**
     * Driver or fleet owner places a bid on a cargo request.
     *
     * @throws \Exception
     */
    public function placeBid(array $validated, User $bidder, CargoRequest $cargo): Bid
    {
        if ($cargo->status !== 'pending') {
            throw new \Exception('This cargo is no longer accepting bids.');
        }

        if ($cargo->bid_deadline && now()->gt($cargo->bid_deadline)) {
            throw new \Exception('The bid deadline has passed. Bidding is closed for this cargo.');
        }

        if (($cargo->price_type ?? 'negotiable') === 'fixed') {
            throw new \Exception('This cargo has a fixed price. Use "Accept Price" to book it directly.');
        }

        // Intracity bids must include available_datetime
        $serviceType = $cargo->service_type ?? 'intercity';
        if ($serviceType === 'intracity') {
            if (empty($validated['available_datetime'])) {
                throw new \Exception('Please specify when you are available to do this job (available_datetime is required for intra-city bids).');
            }
            $availableDt = new \DateTime($validated['available_datetime']);
            if ($availableDt <= new \DateTime()) {
                throw new \Exception('available_datetime must be in the future.');
            }
        }

        $vehicle = Vehicle::findOrFail($validated['vehicle_id']);

        // Verify the bidder owns the vehicle
        if ($bidder->role === 'fleet_owner') {
            if ((int) $vehicle->fleet_owner_id !== $bidder->id) {
                throw new \Exception('This vehicle does not belong to your fleet.');
            }
        } else {
            if ((int) $vehicle->user_id !== $bidder->id) {
                throw new \Exception('This vehicle is not registered to your account.');
            }
        }

        // One bid per vehicle per cargo (enforces sealed bidding per truck, not per person)
        if (Bid::where('cargo_request_id', $cargo->id)->where('vehicle_id', $vehicle->id)->exists()) {
            throw new \Exception('A bid has already been placed for this vehicle on this cargo request.');
        }

        // Vehicle current location → cargo pickup (informational, intercity only)
        $distanceKm = null;
        if (
            $serviceType === 'intercity' &&
            $cargo->pickup_lat !== null && $cargo->pickup_lng !== null &&
            $vehicle->latitude !== null && $vehicle->longitude !== null
        ) {
            $distanceKm = $this->resolveDistanceKm(
                (float) $vehicle->latitude,
                (float) $vehicle->longitude,
                (float) $cargo->pickup_lat,
                (float) $cargo->pickup_lng,
            );
        }

        return Bid::create([
            'cargo_request_id'   => $cargo->id,
            'driver_id'          => $bidder->id,
            'vehicle_id'         => $vehicle->id,
            'amount'             => $validated['amount'],
            'note'               => $validated['note'] ?? null,
            'available_datetime' => ($serviceType === 'intracity') ? ($validated['available_datetime'] ?? null) : null,
            'status'             => 'pending',
            'distance_km'        => $distanceKm,
        ]);
    }

    /**
     * Driver registers interest in a fixed-price cargo offer.
     * Creates a bid at cargo->budget so the shipper can review all applicants
     * and pick the best one (ranked by rating). Cargo stays 'pending'.
     *
     * @throws \Exception
     */
    public function acceptFixedPrice(CargoRequest $cargo, User $driver, Vehicle $vehicle): Bid
    {
        if ($cargo->status !== 'pending') {
            throw new \Exception('This cargo is no longer available.');
        }

        if (Bid::where('cargo_request_id', $cargo->id)->where('driver_id', $driver->id)->exists()) {
            throw new \Exception('You have already accepted this offer.');
        }

        $distanceKm = null;
        if (
            $cargo->pickup_lat !== null && $cargo->pickup_lng !== null &&
            $vehicle->latitude !== null && $vehicle->longitude !== null
        ) {
            $distanceKm = $this->resolveDistanceKm(
                (float) $vehicle->latitude, (float) $vehicle->longitude,
                (float) $cargo->pickup_lat, (float) $cargo->pickup_lng,
            );
        }

        return Bid::create([
            'cargo_request_id' => $cargo->id,
            'driver_id'        => $driver->id,
            'vehicle_id'       => $vehicle->id,
            'amount'           => $cargo->budget,
            'note'             => null,
            'status'           => 'pending',
            'distance_km'      => $distanceKm,
        ]);
    }

    /**
     * Vehicle → pickup distance shown to the shipper on a bid. Prefers real
     * OSRM road distance; falls back to haversine×1.4 if OSRM is unreachable
     * — same convention as RoutingController's and AiController's fallbacks.
     */
    private function resolveDistanceKm(float $vehLat, float $vehLng, float $pickupLat, float $pickupLng): float
    {
        $route = $this->routing->getRoute($vehLat, $vehLng, $pickupLat, $pickupLng);
        if ($route !== null) {
            return round((float) $route['distance_km'], 1);
        }

        return round(
            DistanceService::haversineKm($vehLat, $vehLng, $pickupLat, $pickupLng) * DistanceService::ROAD_DISTANCE_INFLATION_FACTOR,
            1
        );
    }

    /**
     * Sort bids by:
     *  - intercity: amount ASC, then avg driver rating DESC (tiebreaker)
     *  - intracity:  amount ASC only (price is the only criterion)
     * Marks the top bid's is_recommended = true in-memory only (not persisted).
     *
     * @param  Collection<Bid> $bids
     * @return Collection<Bid>
     */
    public function rankBids(Collection $bids): Collection
    {
        if ($bids->isEmpty()) {
            return $bids;
        }

        $serviceType = $bids->first()->cargoRequest?->service_type ?? 'intercity';

        if ($serviceType === 'intracity') {
            $sorted = $bids->sortBy('amount')->values();
        } else {
            $driverIds = $bids->pluck('driver_id')->unique()->toArray();
            $ratings   = Rating::whereIn('driver_id', $driverIds)
                ->selectRaw('driver_id, AVG(rating) as avg_rating')
                ->groupBy('driver_id')
                ->pluck('avg_rating', 'driver_id');

            $sorted = $bids->sortBy([
                fn ($a, $b) => $a->amount <=> $b->amount,
                fn ($a, $b) => ($ratings[$b->driver_id] ?? 0) <=> ($ratings[$a->driver_id] ?? 0),
            ])->values();
        }

        $sorted->each(function (Bid $bid, int $i) {
            $bid->is_recommended = ($i === 0);
        });

        return $sorted;
    }

    /**
     * Either party sends a counter-offer on a bid.
     */
    public function counterBid(Bid $bid, User $actor, float $counterAmount, ?string $counterNote): Bid
    {
        $cargo     = $bid->cargoRequest;
        $isShipper = $cargo->user_id === $actor->id;
        $isDriver  = $bid->driver_id  === $actor->id;

        if (!$isShipper && !$isDriver) {
            throw new \Exception('Forbidden.');
        }

        if (in_array($bid->status, ['accepted', 'rejected', 'expired'])) {
            throw new \Exception('This bid can no longer be negotiated.');
        }

        if ($isShipper && $bid->status === 'countered' && $bid->counter_by === 'shipper') {
            throw new \Exception('Waiting for the driver to respond to your last counter-offer.');
        }

        if ($isDriver && ($bid->status !== 'countered' || $bid->counter_by !== 'shipper')) {
            throw new \Exception('No shipper counter-offer to respond to.');
        }

        $bid->update([
            'status'         => 'countered',
            'counter_amount' => $counterAmount,
            'counter_note'   => $counterNote,
            'counter_by'     => $isShipper ? 'shipper' : 'driver',
            'counter_at'     => now(),
        ]);

        return $bid->refresh();
    }

    /**
     * Accept the standing counter-offer — creates a booking at counter_amount.
     */
    public function acceptCounter(Bid $bid, User $actor): Booking
    {
        $cargo     = $bid->cargoRequest;
        $isShipper = $cargo->user_id === $actor->id;
        $isDriver  = $bid->driver_id  === $actor->id;

        if (!$isShipper && !$isDriver) {
            throw new \Exception('Forbidden.');
        }

        // Identity checks above are safe pre-transaction (actor/driver_id never change).
        // Status checks must happen again under lock below — another accept may land
        // between this line and the transaction starting.
        return DB::transaction(function () use ($bid, $isShipper, $isDriver) {
            // Lock cargo first, then bid — same order as acceptBid() to avoid deadlocks.
            $cargo = CargoRequest::whereKey($bid->cargo_request_id)->lockForUpdate()->firstOrFail();
            if ($cargo->status !== 'pending') {
                throw new \Exception('This cargo request is no longer available — it may have just been booked.');
            }

            $lockedBid = Bid::whereKey($bid->id)->lockForUpdate()->firstOrFail();
            if ($lockedBid->status !== 'countered') {
                throw new \Exception('No active counter-offer to accept.');
            }
            if ($isShipper && $lockedBid->counter_by !== 'driver') {
                throw new \Exception('No driver counter-offer to accept.');
            }
            if ($isDriver && $lockedBid->counter_by !== 'shipper') {
                throw new \Exception('No shipper counter-offer to accept.');
            }

            $this->assertVehicleFree($lockedBid->vehicle_id);

            $lockedBid->update(['status' => 'accepted', 'amount' => $lockedBid->counter_amount]);

            Bid::where('cargo_request_id', $cargo->id)
                ->where('id', '!=', $lockedBid->id)
                ->whereIn('status', ['pending', 'countered'])
                ->update(['status' => 'rejected']);

            $cargo->update(['status' => 'matched']);

            $commission = round((float) $lockedBid->counter_amount * config('services.commission_rate'), 2);

            return Booking::create([
                'cargo_id'        => $cargo->id,
                'vehicle_id'      => $lockedBid->vehicle_id,
                'driver_id'       => $lockedBid->driver_id,
                'bid_id'          => $lockedBid->id,
                'booking_status'  => 'accepted',
                'estimated_price' => $lockedBid->counter_amount,
                'commission_fee'  => $commission,
            ]);
        });
    }

    /**
     * Shipper directly accepts a bid — auto-rejects all other pending bids.
     *
     * @throws \Exception if the cargo/bid is no longer pending — re-checked under
     *                     lock so two near-simultaneous accepts on the same cargo
     *                     can't both create a Booking.
     */
    public function acceptBid(Bid $bid): Booking
    {
        return DB::transaction(function () use ($bid) {
            // Lock cargo first, then bid — same order as acceptCounter() to avoid deadlocks.
            $cargo = CargoRequest::whereKey($bid->cargo_request_id)->lockForUpdate()->firstOrFail();
            if ($cargo->status !== 'pending') {
                throw new \Exception('This cargo request is no longer available — it may have just been booked.');
            }

            $lockedBid = Bid::whereKey($bid->id)->lockForUpdate()->firstOrFail();
            if ($lockedBid->status !== 'pending') {
                throw new \Exception('This bid is no longer pending.');
            }

            $this->assertVehicleFree($lockedBid->vehicle_id);

            $lockedBid->update(['status' => 'accepted']);

            Bid::where('cargo_request_id', $cargo->id)
                ->where('id', '!=', $lockedBid->id)
                ->whereIn('status', ['pending', 'countered'])
                ->update(['status' => 'rejected']);

            $cargo->update(['status' => 'matched']);

            $commission = round((float) $lockedBid->amount * config('services.commission_rate'), 2);

            return Booking::create([
                'cargo_id'        => $cargo->id,
                'vehicle_id'      => $lockedBid->vehicle_id,
                'driver_id'       => $lockedBid->driver_id,
                'bid_id'          => $lockedBid->id,
                'booking_status'  => 'accepted',
                'estimated_price' => $lockedBid->amount,
                'commission_fee'  => $commission,
            ]);
        });
    }

    /**
     * Locks the vehicle row and throws once it already has two active
     * bookings — one being driven, one queued up behind it. A driver can
     * line up their next job while still finishing the current one (see
     * TripService::startTrip, which is what actually stops a vehicle
     * driving two loads at once), but can't stack a third. Must be called
     * from inside the same transaction as the cargo/bid lock above, after
     * those, to keep lock order (cargo → bid → vehicle) consistent
     * everywhere this runs and avoid deadlocks.
     */
    private function assertVehicleFree(int $vehicleId): void
    {
        Vehicle::whereKey($vehicleId)->lockForUpdate()->firstOrFail();

        $activeCount = Booking::where('vehicle_id', $vehicleId)
            ->whereNotIn('booking_status', ['cancelled', 'completed', 'delivered'])
            ->count();

        if ($activeCount >= 2) {
            throw new \Exception('This vehicle already has an active trip and a queued one — finish or deliver one before taking on another.');
        }
    }
}
