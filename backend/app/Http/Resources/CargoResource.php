<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class CargoResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $user = $request->user();
        // Hide budget from drivers — sealed-bid: drivers should not anchor to the shipper's price
        $showBudget = true;

        return [
            'id'                  => $this->id,
            'user_id'             => $this->user_id,
            'service_type'        => $this->service_type ?? 'intercity',
            // intercity fields
            'pickup_location'     => $this->pickup_location,
            'pickup_lat'          => $this->pickup_lat,
            'pickup_lng'          => $this->pickup_lng,
            'destination'         => $this->destination,
            'material_type'       => $this->material_type,
            'weight'              => $this->weight,
            'urgency_level'       => $this->urgency_level,
            // intracity fields
            'city'                => $this->city,
            'pickup_area'         => $this->pickup_area,
            'dropoff_area'        => $this->dropoff_area,
            'preferred_date'      => $this->preferred_date?->toDateString(),
            'items_description'   => $this->items_description,
            'vehicle_type_needed' => $this->vehicle_type_needed,
            // shared
            'budget'              => $showBudget ? $this->budget : null,
            'price_type'          => $this->price_type ?? 'negotiable',
            'bid_deadline'        => $this->bid_deadline,
            'status'              => $this->status,
            'created_at'          => $this->created_at,
            'updated_at'          => $this->updated_at,
            // distance from driver — set by CargoRequestController::index() via withAttribute
            'distance_km'         => $this->distance_km ?? null,
        ];
    }
}
