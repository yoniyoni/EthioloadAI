<?php

namespace App\Services;

use App\Models\BackhaulRecommendation;
use App\Models\CargoRequest;
use App\Models\PlatformSetting;
use App\Models\Trip;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\Log;

class BackhaulService
{
    /**
     * Generate and persist backhaul recommendations for a trip.
     * Returns the top-5 BackhaulRecommendation records with cargoRequest loaded.
     */
    public function recommendForTrip(Trip $trip): Collection
    {
        $trip->loadMissing('booking.cargoRequest', 'booking.vehicle');
        $booking = $trip->booking;

        if (!$booking) {
            return collect();
        }

        $driverId       = $booking->driver_id;
        $currentCargoId = $booking->cargo_id;
        $tripCargo      = $booking->cargoRequest;
        $vehicle        = $booking->vehicle;

        if (!$vehicle) {
            Log::warning("BackhaulService: no vehicle on booking #{$booking->id} for trip #{$trip->id} — cannot determine cargo eligibility");
            return collect();
        }

        // Prefer the trip's own cargo's stored destination coordinates (added
        // in an earlier PostGIS phase) over a fresh city-name text lookup.
        // Only fall back to the resolveCity() text match when the cargo (or
        // its coordinates) is missing.
        if ($tripCargo && $tripCargo->destination_lat !== null && $tripCargo->destination_lng !== null) {
            $destLat = (float) $tripCargo->destination_lat;
            $destLng = (float) $tripCargo->destination_lng;
        } else {
            $destCoords = $this->resolveCity($trip->destination);

            if (!$destCoords) {
                Log::warning("BackhaulService: no city match for destination '{$trip->destination}' (trip #{$trip->id})");
                return collect();
            }

            [$destLat, $destLng] = $destCoords;
        }

        // Radius (km) is admin-editable via PlatformSetting; read fresh on
        // every call so changes apply without a deploy. Both the ST_DWithin
        // filter below and the scoring formula's normalization divisor use
        // this same value, so the two always move together.
        $radiusKm     = (float) PlatformSetting::get('spatial.backhaul_radius_km', 200);
        $radiusMeters = $radiusKm * 1000;

        // Vehicle eligibility: only recommend cargo this vehicle can actually
        // carry. Mirrors the same service_type <-> vehicle_category matching
        // (and the same "heavy also accepts a null service_type" allowance)
        // already used throughout CargoRequestController, plus a capacity
        // cap — neither existed here before.
        $requiredServiceType = $vehicle->vehicle_category === 'light' ? 'intracity' : 'intercity';

        // Base filters shared by both tiers: pending cargo, excluding the
        // cargo already being hauled on this trip, and now vehicle-eligible.
        $baseQuery = CargoRequest::where('status', 'pending')
            ->when($currentCargoId, fn ($q) => $q->where('id', '!=', $currentCargoId))
            ->where('weight', '<=', $vehicle->capacity)
            ->where(function ($q) use ($requiredServiceType) {
                $q->where('service_type', $requiredServiceType);
                if ($requiredServiceType === 'intercity') {
                    $q->orWhereNull('service_type');
                }
            });

        // Tier A: cargo rows with a real stored pickup_point. PostGIS-
        // accelerated: ST_DWithin does the radius filter using the
        // cargo_requests_pickup_point_gist index; the KNN `<->` operator
        // does the index-accelerated distance sort.
        $tierA = (clone $baseQuery)
            ->whereNotNull('pickup_point')
            ->whereRaw(
                'ST_DWithin(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$destLng, $destLat, $radiusMeters]
            )
            ->orderByRaw(
                'pickup_point <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$destLng, $destLat]
            )
            ->orderBy('cargo_requests.created_at', 'desc')
            ->selectRaw(
                'cargo_requests.*, ST_Distance(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$destLng, $destLat]
            )
            ->get()
            ->each(function (CargoRequest $c) {
                $c->distance_km = round((float) $c->distance_km_pg, 1);
            });

        // Tier B: cargo rows without a stored pickup_point. Same
        // resolveCity()-based Haversine fallback as before, just isolated to
        // this smaller subset, filtered to the same radius in PHP (there's
        // no SQL geography column to filter on for these rows).
        $tierB = (clone $baseQuery)
            ->whereNull('pickup_point')
            ->get()
            ->map(function (CargoRequest $c) use ($destLat, $destLng) {
                $pickupCoords = $this->resolveCity($c->pickup_location);
                if (!$pickupCoords) {
                    $c->distance_km = null;
                    return $c;
                }
                [$pickLat, $pickLng] = $pickupCoords;
                $c->distance_km = round(DistanceService::haversineKm($destLat, $destLng, $pickLat, $pickLng), 1);
                return $c;
            })
            ->filter(fn (CargoRequest $c) => $c->distance_km !== null && $c->distance_km <= $radiusKm);

        $candidates = $tierA->concat($tierB);

        $scored = $candidates->map(function (CargoRequest $cargo) use ($radiusKm) {
            $distanceKm    = $cargo->distance_km;
            $distanceScore = max(0.0, 1.0 - ($distanceKm / $radiusKm));

            $urgencyScore = match (strtolower($cargo->urgency_level ?? 'normal')) {
                'urgent', 'express' => 1.0,
                'high', 'normal'    => 0.5,
                'low'               => 0.2,
                default             => 0.5,
            };

            $weightScore = min((float) $cargo->weight / 20.0, 1.0);

            $finalScore = ($distanceScore * 0.4) + ($urgencyScore * 0.3) + ($weightScore * 0.3);

            return [
                'cargo'       => $cargo,
                'score'       => round($finalScore, 3),
                'distance_km' => $distanceKm,
            ];
        })->filter()->sortByDesc('score')->take(5);

        return $scored->map(function (array $item) use ($trip, $driverId) {
            /** @var CargoRequest $cargo */
            $cargo = $item['cargo'];

            $priceRange = null;
            if ($cargo->budget) {
                $priceRange = [
                    'min' => round($cargo->budget * 0.8),
                    'max' => round($cargo->budget * 1.2),
                ];
            }

            $rec = BackhaulRecommendation::updateOrCreate(
                [
                    'trip_id'          => $trip->id,
                    'driver_id'        => $driverId,
                    'cargo_request_id' => $cargo->id,
                ],
                [
                    'score'  => $item['score'],
                    'status' => 'pending',
                    'metadata' => [
                        'distance_km'           => $item['distance_km'],
                        'urgency'               => $cargo->urgency_level,
                        'estimated_price_range' => $priceRange,
                        'pickup_location_name'  => $cargo->pickup_location,
                    ],
                ]
            );

            $rec->setRelation('cargoRequest', $cargo);

            return $rec;
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns [lat, lng] for a city string, or null if unknown.
     * Delegates to CityCoordinates' canonical alias/substring resolution
     * (single source of truth, replacing this class's former $CITIES table).
     */
    private function resolveCity(string $location): ?array
    {
        return CityCoordinates::resolveFromText($location);
    }
}
