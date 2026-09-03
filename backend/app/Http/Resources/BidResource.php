<?php

namespace App\Http\Resources;

use App\Models\Rating;
use App\Models\Vehicle;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class BidResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        $driver  = $this->driver;
        $vehicle = $this->vehicle;

        // Is this bid's vehicle currently mid-trip? Lets the shipper factor
        // "free now" vs "wraps up their current job first" into their pick —
        // see BidService::assertVehicleFree / TripService::startTrip for the
        // matching backend rule (one active trip + one queued booking), and
        // Vehicle::busyVehicleIds for the shared "is busy" definition (also
        // used by the nearby-trucks endpoint).
        $driverBusy = $vehicle && Vehicle::busyVehicleIds([$vehicle->id])->isNotEmpty();

        // Completed trips = completed bookings where this user was the driver
        $tripCount = $driver
            ? $driver->bookings()->where('booking_status', 'completed')->count()
            : 0;

        // Average rating received by this driver
        $avgRating = $driver
            ? Rating::where('driver_id', $driver->id)->avg('rating')
            : null;

        $cargo = $this->cargoRequest;

        return [
            'id'               => $this->id,
            'cargo_request_id' => $this->cargo_request_id,
            'driver_id'        => $this->driver_id,
            'vehicle_id'       => $this->vehicle_id,
            'amount'           => $this->amount,
            'note'             => $this->note,
            'available_datetime' => $this->available_datetime?->toIso8601String(),
            'status'           => $this->status,
            'ai_score'         => $this->ai_score,
            'is_recommended'   => $this->is_recommended,
            'distance_km'      => $this->distance_km !== null ? round((float) $this->distance_km, 1) : null,
            'bidder_type'      => ($driver?->role === 'fleet_owner') ? 'fleet_owner' : 'driver',
            // Counter-offer fields
            'counter_amount'   => $this->counter_amount,
            'counter_note'     => $this->counter_note,
            'counter_by'       => $this->counter_by,
            'counter_at'       => $this->counter_at,
            // Driver info — phone deliberately omitted, see BookingResource.
            'driver_name'       => $driver?->full_name,
            'driver_rating'     => $avgRating ? round((float) $avgRating, 1) : null,
            'driver_trip_count' => $tripCount,
            // Vehicle info
            'truck_type'       => $vehicle?->truck_type,
            'vehicle_category' => $vehicle?->vehicle_category,
            'plate_number'     => $vehicle?->plate_number,
            'vehicle_capacity' => $vehicle?->capacity,
            // Vehicle's current live position (for the shipper's pre-trip
            // "where is this driver" map) — a live snapshot at query time,
            // not the position at bid placement.
            'driver_lat'       => $vehicle?->latitude !== null ? (float) $vehicle->latitude : null,
            'driver_lng'       => $vehicle?->longitude !== null ? (float) $vehicle->longitude : null,
            'driver_busy'      => $driverBusy,
            // Shipper name (phone deliberately omitted, see BookingResource)
            'shipper_name'      => $cargo?->user?->full_name,
            // Cargo summary (used on driver's "my bids" list)
            'cargo_service_type' => $cargo?->service_type ?? 'intercity',
            'cargo_pickup'       => $cargo?->pickup_location,
            'cargo_destination'  => $cargo?->destination,
            'cargo_city'         => $cargo?->city,
            'cargo_pickup_area'  => $cargo?->pickup_area,
            'cargo_dropoff_area' => $cargo?->dropoff_area,
            'cargo_material'     => $cargo?->material_type,
            'cargo_weight'       => $cargo?->weight,
            'cargo_budget'       => $cargo?->budget,
            'created_at'         => $this->created_at,
            'updated_at'         => $this->updated_at,
        ];
    }
}
