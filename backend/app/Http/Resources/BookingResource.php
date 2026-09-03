<?php

namespace App\Http\Resources;

use App\Models\Rating;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class BookingResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $trip = $this->trip;

        // For shippers, find their specific stop in a multi-stop trip
        $myStop = null;
        if ($trip && $trip->isMultiStop() && $this->cargo_id) {
            $myStop = $trip->tripStops
                ? $trip->tripStops->firstWhere('cargo_request_id', $this->cargo_id)
                : null;
        }

        // Phone numbers stay hidden while a price is agreed but unpaid —
        // once the shipper's payment is verified, the platform's commission
        // is already locked in, so the disintermediation risk that
        // justified hiding them is gone, and revealing them closes the real
        // coordination gap (no in-app chat) that hiding them created.
        $isPaid = $this->payment?->payment_status === 'paid';

        return [
            'id'              => $this->id,
            'cargo_id'        => $this->cargo_id,
            'vehicle_id'      => $this->vehicle_id,
            'driver_id'       => $this->driver_id,
            'booking_status'  => $this->booking_status,
            'estimated_price' => $this->estimated_price,
            'commission_fee'  => $this->commission_fee,
            // Trip info
            'trip_id'              => $trip?->id,
            'trip_status'          => $trip?->trip_status,
            'trip_type'            => $trip?->trip_type ?? 'single',
            'trip_total_stops'     => $trip?->total_stops ?? 1,
            'trip_completed_stops' => $trip?->completed_stops ?? 0,
            'trip_total_amount'    => $trip?->total_amount,
            // Shipper's stop info (for multi-stop trips)
            'my_stop_order'  => $myStop?->stop_order,
            'my_stop_status' => $myStop?->status,
            'my_stop_location' => $myStop?->location_name,
            // Cargo route info
            'pickup_location' => $this->cargoRequest?->pickup_location,
            'destination'     => $this->cargoRequest?->destination,
            'material_type'   => $this->cargoRequest?->material_type,
            'weight'          => $this->cargoRequest?->weight,
            'urgency_level'   => $this->cargoRequest?->urgency_level,
            'service_type'    => $this->cargoRequest?->service_type ?? 'intercity',
            'city'            => $this->cargoRequest?->city,
            // Contact info — phone numbers only once payment clears (see
            // $isPaid above). Names are always visible, for context.
            'driver_name'   => $this->driver?->full_name,
            'driver_phone'  => $isPaid ? $this->driver?->phone : null,
            // Vehicle's last-known live position — lets the shipper see
            // where the driver is between "booking confirmed" and "trip
            // started" (TrackingScreen otherwise has nothing to show until
            // a Trip row exists). Same live-snapshot convention BidResource
            // already uses pre-acceptance.
            'driver_lat'    => $this->vehicle?->latitude !== null ? (float) $this->vehicle->latitude : null,
            'driver_lng'    => $this->vehicle?->longitude !== null ? (float) $this->vehicle->longitude : null,
            'driver_last_location_at' => $this->vehicle?->last_location_at?->toISOString(),
            'shipper_name'  => $this->cargoRequest?->user?->full_name,
            'shipper_phone' => $isPaid ? $this->cargoRequest?->user?->phone : null,
            'payment_method'    => $this->payment?->payment_method,
            'payment_status'    => $this->payment?->payment_status,
            'payout_status'     => $this->payment?->payout_status,
            'has_rating'        => Rating::where('booking_id', $this->id)
                                         ->where('rater_id', auth()->id())
                                         ->exists(),
            'shipper_rating'    => $this->rating?->rating,
            'rating_feedback'   => $this->rating?->feedback,
            'created_at'     => $this->created_at,
            'updated_at'     => $this->updated_at,
        ];
    }
}
