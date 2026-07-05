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

        // Haversine distance: vehicle current location → cargo pickup (informational, intercity only)
        $distanceKm = null;
        if (
            $serviceType === 'intercity' &&
            $cargo->pickup_latitude !== null && $cargo->pickup_longitude !== null &&
            $vehicle->latitude !== null && $vehicle->longitude !== null
        ) {
            $distanceKm = $this->haversine(
                (float) $vehicle->latitude,
                (float) $vehicle->longitude,
                (float) $cargo->pickup_latitude,
                (float) $cargo->pickup_longitude,
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
            $cargo->pickup_latitude !== null && $cargo->pickup_longitude !== null &&
            $vehicle->latitude !== null && $vehicle->longitude !== null
        ) {
            $distanceKm = $this->haversine(
                (float) $vehicle->latitude, (float) $vehicle->longitude,
                (float) $cargo->pickup_latitude, (float) $cargo->pickup_longitude,
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

        if ($bid->status !== 'countered') {
            throw new \Exception('No active counter-offer to accept.');
        }

        if ($isShipper && $bid->counter_by !== 'driver') {
            throw new \Exception('No driver counter-offer to accept.');
        }

        if ($isDriver && $bid->counter_by !== 'shipper') {
            throw new \Exception('No shipper counter-offer to accept.');
        }

        return DB::transaction(function () use ($bid) {
            $bid->update(['status' => 'accepted', 'amount' => $bid->counter_amount]);

            Bid::where('cargo_request_id', $bid->cargo_request_id)
                ->where('id', '!=', $bid->id)
                ->whereIn('status', ['pending', 'countered'])
                ->update(['status' => 'rejected']);

            $bid->cargoRequest()->update(['status' => 'matched']);

            $commission = round((float) $bid->counter_amount * 0.10, 2);

            return Booking::create([
                'cargo_id'        => $bid->cargo_request_id,
                'vehicle_id'      => $bid->vehicle_id,
                'driver_id'       => $bid->driver_id,
                'bid_id'          => $bid->id,
                'booking_status'  => 'accepted',
                'estimated_price' => $bid->counter_amount,
                'commission_fee'  => $commission,
            ]);
        });
    }

    /**
     * Shipper directly accepts a bid — auto-rejects all other pending bids.
     */
    public function acceptBid(Bid $bid): Booking
    {
        return DB::transaction(function () use ($bid) {
            $bid->update(['status' => 'accepted']);

            Bid::where('cargo_request_id', $bid->cargo_request_id)
                ->where('id', '!=', $bid->id)
                ->whereIn('status', ['pending', 'countered'])
                ->update(['status' => 'rejected']);

            $bid->cargoRequest()->update(['status' => 'matched']);

            $commission = round((float) $bid->amount * 0.10, 2);

            return Booking::create([
                'cargo_id'        => $bid->cargo_request_id,
                'vehicle_id'      => $bid->vehicle_id,
                'driver_id'       => $bid->driver_id,
                'bid_id'          => $bid->id,
                'booking_status'  => 'accepted',
                'estimated_price' => $bid->amount,
                'commission_fee'  => $commission,
            ]);
        });
    }

    /**
     * Haversine formula — straight-line distance between two coordinates in km.
     */
    private function haversine(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R  = 6371.0;
        $φ1 = deg2rad($lat1);
        $φ2 = deg2rad($lat2);
        $Δφ = deg2rad($lat2 - $lat1);
        $Δλ = deg2rad($lon2 - $lon1);

        $a = sin($Δφ / 2) ** 2 + cos($φ1) * cos($φ2) * sin($Δλ / 2) ** 2;
        return round(2 * $R * asin(sqrt($a)), 1);
    }
}
