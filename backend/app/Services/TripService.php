<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\Trip;
use App\Notifications\TripStatusUpdatedNotification;
use App\Events\TripLocationUpdated;

class TripService
{
    public function startTrip(Booking $booking)
    {
        // Eager load cargoRequest so we can read pickup_location / destination
        $booking->load('cargoRequest');

        $trip = Trip::create([
            'booking_id'     => $booking->id,
            'start_location' => $booking->cargoRequest->pickup_location ?? 'Unknown',
            'destination'    => $booking->cargoRequest->destination    ?? 'Unknown',
            'route_data'     => [],
            'trip_status'    => 'ongoing',
            'start_time'     => now(),
        ]);

        // Mark cargo as matched so it no longer shows as available
        if ($booking->cargoRequest) {
            $booking->cargoRequest->update(['status' => 'matched']);
        }

        // Notify shipper that the trip has started
        if ($booking->cargoRequest && $booking->cargoRequest->user) {
            $booking->cargoRequest->user->notify(new TripStatusUpdatedNotification($trip));
        }

        return $trip;
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
            $bookingUpdates['commission_fee']  = round($totalAmount * 0.10, 2);
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
}
