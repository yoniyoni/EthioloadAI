<?php

namespace App\Http\Resources;

use App\Services\CityCoordinates;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class CargoResource extends JsonResource
{
    /**
     * Look up [lat, lng] for an Ethiopian city name.
     * Tries exact match, then strips after "/" or ",", then case-insensitive substring.
     * Backed by CityCoordinates::COORDS (single canonical source, replacing this
     * method's former local VehicleController::CITY_COORDS + $extra merge).
     */
    private static function cityCoords(string $city): ?array
    {
        $all = CityCoordinates::COORDS;

        $trimmed = trim($city);
        if ($trimmed === '' || strtolower($trimmed) === 'other') return null;

        if (isset($all[$trimmed])) return $all[$trimmed];

        // Strip after " / " or "," (e.g. "Adama / Nazret" → "Adama")
        $short = trim(preg_split('/\s*[\/,]\s*/', $trimmed)[0]);
        if ($short !== $trimmed && isset($all[$short])) return $all[$short];

        // Case-insensitive substring
        $lc = strtolower($trimmed);
        foreach ($all as $name => $coords) {
            if (stripos($name, $lc) !== false || stripos($lc, strtolower($name)) !== false) {
                return $coords;
            }
        }

        return null;
    }

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

        // Resolve pickup coordinates: stored GPS value takes priority, then city-name lookup.
        $pickupCoords = ($this->pickup_lat && $this->pickup_lng)
            ? [(float) $this->pickup_lat, (float) $this->pickup_lng]
            : self::cityCoords($this->pickup_location ?? '');

        // Destination always resolved from city-name lookup (never GPS-stored on cargo record).
        $destCoords = self::cityCoords($this->destination ?? '');

        return [
            'id'                  => $this->id,
            'user_id'             => $this->user_id,
            'service_type'        => $this->service_type ?? 'intercity',
            // intercity fields
            'pickup_location'     => $this->pickup_location,
            'pickup_lat'          => $pickupCoords ? $pickupCoords[0] : null,
            'pickup_lng'          => $pickupCoords ? $pickupCoords[1] : null,
            'destination'         => $this->destination,
            'destination_lat'     => $destCoords ? $destCoords[0] : null,
            'destination_lng'     => $destCoords ? $destCoords[1] : null,
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
