<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\Payment;
use App\Models\Trip;
use App\Notifications\TripStatusUpdatedNotification;
use App\Notifications\TripPickedUpNotification;
use App\Events\TripLocationUpdated;
use Illuminate\Support\Facades\DB;

class TripService
{
    /**
     * @throws \Exception if a trip already exists for this booking — re-checked
     *                     under lock, since two near-simultaneous "start trip"
     *                     requests could otherwise both pass the controller's
     *                     pre-check and both create a Trip row.
     */
    public function startTrip(Booking $booking)
    {
        return DB::transaction(function () use ($booking) {
            $lockedBooking = Booking::whereKey($booking->id)->lockForUpdate()->firstOrFail();

            if (Trip::where('booking_id', $lockedBooking->id)->exists()) {
                throw new \Exception('Trip already exists for this booking.');
            }

            // One truck, one road at a time: a driver may hold a second
            // (queued) booking on the same vehicle — see BidService::
            // assertVehicleFree — but can't be driving both at once. This
            // is the one place that actually enforces that; everything
            // else (bidding, accepting, paying) is allowed to happen in
            // parallel with an ongoing trip.
            $otherOngoing = Trip::where('trip_status', 'ongoing')
                ->whereHas('booking', fn ($q) => $q->where('vehicle_id', $lockedBooking->vehicle_id))
                ->exists();
            if ($otherOngoing) {
                throw new \Exception('Finish your current trip before starting the next one.');
            }

            // Upfront-escrow gate: the shipper must have completed a
            // gateway-verified Chapa payment (PaymentService::
            // finalizeChapaPayment sets this to 'paid') before a driver can
            // start driving. Checks for the existence of a 'paid' row
            // specifically, rather than fetching "the" payment and checking
            // its status — a booking should never have more than one
            // Payment row in normal operation (both processPayment() and
            // initializeChapaPayment() reuse an existing row rather than
            // creating a second one), but this stays correct even if that
            // invariant is ever violated, instead of depending on default
            // row ordering to happen to surface the right one.
            $hasPaid = Payment::where('booking_id', $lockedBooking->id)
                ->where('payment_status', 'paid')
                ->exists();
            if (!$hasPaid) {
                throw new \Exception('Payment must be completed before this trip can start.');
            }

            // Eager load cargoRequest so we can read pickup_location / destination
            $lockedBooking->load('cargoRequest');

            $trip = Trip::create([
                'booking_id'     => $lockedBooking->id,
                'start_location' => $lockedBooking->cargoRequest->pickup_location ?? 'Unknown',
                'destination'    => $lockedBooking->cargoRequest->destination    ?? 'Unknown',
                'route_data'     => [],
                'trip_status'    => 'ongoing',
                'start_time'     => now(),
            ]);

            // Mark cargo as matched so it no longer shows as available
            if ($lockedBooking->cargoRequest) {
                $lockedBooking->cargoRequest->update(['status' => 'matched']);
            }

            // Notify shipper that the trip has started
            if ($lockedBooking->cargoRequest && $lockedBooking->cargoRequest->user) {
                $lockedBooking->cargoRequest->user->notify(new TripStatusUpdatedNotification($trip));
            }

            return $trip;
        });
    }

    public function updateLocation(Trip $trip, array $gpsData)
    {
        // Append each location ping to route_data
        $history   = $trip->route_data ?? [];
        $history[] = array_merge($gpsData, ['ts' => now()->toISOString()]);

        $trip->update(['route_data' => $history]);

        event(new TripLocationUpdated($trip, $gpsData));

        return $trip;
    }

    public function confirmPickup(Trip $trip)
    {
        if ($trip->picked_up_at !== null) {
            return $trip; // idempotent — already confirmed, no-op
        }

        $trip->update(['picked_up_at' => now()]);

        $trip->load('booking.cargoRequest.user');
        if ($trip->booking->cargoRequest && $trip->booking->cargoRequest->user) {
            $trip->booking->cargoRequest->user->notify(new TripPickedUpNotification($trip));
        }

        return $trip;
    }

    public function completeTrip(Trip $trip)
    {
        $trip->update([
            'trip_status' => 'completed',
            'end_time'    => now(),
        ]);

        // For multi-stop trips, recalculate commission on total_amount
        $totalAmount = $trip->total_amount;
        $bookingUpdates = ['booking_status' => 'delivered'];
        if ($trip->isMultiStop() && $totalAmount > 0) {
            $bookingUpdates['estimated_price'] = $totalAmount;
            $bookingUpdates['commission_fee']  = round($totalAmount * config('services.commission_rate'), 2);
        }
        $trip->booking->update($bookingUpdates);

        // Mark the primary cargo request as completed
        if ($trip->booking->cargoRequest) {
            $trip->booking->cargoRequest->update(['status' => 'completed']);
        }

        // Mark all stop cargo requests as completed
        $trip->tripStops()
            ->whereNotNull('cargo_request_id')
            ->with('cargoRequest')
            ->get()
            ->each(function ($stop) {
                $stop->cargoRequest?->update(['status' => 'completed']);
            });

        // Notify primary shipper
        if ($trip->booking->cargoRequest && $trip->booking->cargoRequest->user) {
            $trip->booking->cargoRequest->user->notify(new TripStatusUpdatedNotification($trip));
        }

        return $trip;
    }

    /**
     * Vehicle capacity still free on this ongoing trip: capacity minus the
     * primary booking's cargo weight minus every stop's cargo weight already
     * added (stop status doesn't matter here — 'completed' on a TripStop
     * means "picked up", not "dropped off", so that cargo stays on the
     * truck for the rest of the trip either way). Returns null if the
     * vehicle has no capacity recorded to compare against.
     */
    public function remainingCapacityTons(Trip $trip): ?float
    {
        $trip->loadMissing(['booking.vehicle', 'booking.cargoRequest', 'tripStops.cargoRequest']);

        $vehicle = $trip->booking?->vehicle;
        if (!$vehicle || $vehicle->capacity === null) {
            return null;
        }

        $committed = (float) ($trip->booking->cargoRequest->weight ?? 0);

        foreach ($trip->tripStops as $stop) {
            if ($stop->cargoRequest) {
                $committed += (float) $stop->cargoRequest->weight;
            }
        }

        return max(0.0, (float) $vehicle->capacity - $committed);
    }
}
