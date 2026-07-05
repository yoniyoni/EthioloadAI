<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class VehicleResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'user_id' => $this->user_id,
            'truck_type' => $this->truck_type,
            'plate_number' => $this->plate_number,
            'capacity' => $this->capacity,
            'current_city' => $this->current_city,
            'latitude' => $this->latitude,
            'longitude' => $this->longitude,
            'availability_status' => $this->availability_status,
            'rating' => $this->rating,
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
        ];
    }
}
