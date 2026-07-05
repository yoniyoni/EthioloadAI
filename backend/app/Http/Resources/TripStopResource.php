<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class TripStopResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        $cargo = $this->cargoRequest;

        return [
            'id'               => $this->id,
            'trip_id'          => $this->trip_id,
            'cargo_request_id' => $this->cargo_request_id,
            'stop_order'       => $this->stop_order,
            'location_name'    => $this->location_name,
            'pickup_lat'       => $this->pickup_lat,
            'pickup_lng'       => $this->pickup_lng,
            'agreed_price'     => $this->agreed_price,
            'agreed_price_formatted' => 'ETB ' . number_format((float) $this->agreed_price, 0, '.', ','),
            'status'           => $this->status,
            'notes'            => $this->notes,
            'arrived_at'       => $this->arrived_at,
            'completed_at'     => $this->completed_at,
            // Cargo summary for display — shipper privacy ensured at route level
            'cargo_material'   => $cargo?->material_type,
            'cargo_weight'     => $cargo?->weight,
            'cargo_pickup'     => $cargo?->pickup_location,
            'cargo_destination'=> $cargo?->destination,
            'shipper_name'     => $cargo?->user?->full_name,
            'created_at'       => $this->created_at,
            'updated_at'       => $this->updated_at,
        ];
    }
}
