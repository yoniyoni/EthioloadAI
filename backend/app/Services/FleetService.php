<?php

namespace App\Services;

use App\Models\Booking;
use App\Models\CargoRequest;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Support\Collection;

class FleetService
{
    /** Vehicles are considered 'online' if their last GPS ping was within this many minutes. */
    private const ONLINE_MINUTES = 60;

    /**
     * Search pending cargo requests by city name and/or a GPS point + radius.
     * With no city/lat/lng given, returns all pending cargo unfiltered (existing behavior).
     */
    public function searchCargo(?string $city, ?float $lat, ?float $lng, ?float $radiusKm): Collection
    {
        $query = CargoRequest::query()->where('status', 'pending');

        if ($city) {
            $needle = strtolower(trim($city));
            $query->where(function ($q) use ($needle) {
                $q->whereRaw('LOWER(pickup_location) LIKE ?', ["%{$needle}%"])
                  ->orWhereRaw('LOWER(city) LIKE ?', ["%{$needle}%"]);
            });
        }

        // Resolve a search origin: explicit lat/lng wins; otherwise, if a city
        // name was given, fall back to that city's center coordinates.
        $originLat = $lat;
        $originLng = $lng;
        if (($originLat === null || $originLng === null) && $city) {
            foreach (CityCoordinates::COORDS as $name => $coords) {
                if (stripos($city, $name) !== false) {
                    [$originLat, $originLng] = $coords;
                    break;
                }
            }
        }

        if ($originLat === null || $originLng === null) {
            // No origin point at all — browse mode, unchanged.
            return $query->latest()->get();
        }

        // An origin point is known. radiusKm stays genuinely optional here —
        // the fleet-dispatch UI has an explicit "Any distance" option
        // (radiusKm: null) that must keep meaning unlimited, not silently
        // gain a cap. When a radius IS supplied, ST_DWithin filters via the
        // cargo_requests_pickup_point_gist index; when it's not, the same
        // KNN `<->` operator still gives an index-accelerated distance sort
        // with no WHERE-clause cap — the "hybrid" case from the original
        // migration plan: KNN-only where hiding results would be wrong.
        $radiusMeters = $radiusKm !== null ? $radiusKm * 1000 : null;

        $tierA = (clone $query)
            ->whereNotNull('pickup_point')
            ->when($radiusMeters !== null, fn ($q) => $q->whereRaw(
                'ST_DWithin(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$originLng, $originLat, $radiusMeters]
            ))
            ->orderByRaw(
                'pickup_point <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$originLng, $originLat]
            )
            ->selectRaw(
                'cargo_requests.*, ST_Distance(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$originLng, $originLat]
            )
            ->get()
            ->each(function ($c) {
                $c->distance_km = round((float) $c->distance_km_pg, 1);
            });

        $tierB = (clone $query)
            ->whereNull('pickup_point')
            ->get()
            ->map(function ($c) use ($originLat, $originLng) {
                [$pLat, $pLng] = $this->resolvePickupCoords($c);
                if ($pLat === null || $pLng === null) {
                    $c->distance_km = null;
                    return $c;
                }
                $c->distance_km = round(DistanceService::haversineKm($originLat, $originLng, $pLat, $pLng), 1);
                return $c;
            });

        if ($radiusKm !== null) {
            $tierB = $tierB->filter(fn ($c) => $c->distance_km !== null && $c->distance_km <= $radiusKm);
        }

        return $tierA->concat($tierB)->sortBy(fn ($c) => $c->distance_km ?? PHP_INT_MAX)->values();
    }

    /**
     * Rank this fleet owner's drivers/vehicles as candidates for a given cargo:
     * online (recent GPS ping), available (no active booking), distance to
     * pickup, and — for vehicles currently busy on another trip — a best-effort
     * 'heading toward pickup' signal derived from their last two location pings.
     * Returns a plain array of associative arrays, sorted available-first then
     * nearest-first (nulls last).
     */
    public function rankCandidatesForCargo(User $owner, CargoRequest $cargo): array
    {
        $requiredCategory = ($cargo->service_type ?? 'intercity') === 'intracity' ? 'light' : 'heavy';
        [$pickupLat, $pickupLng] = $this->resolvePickupCoords($cargo);

        // Deliberately unbounded — a fleet owner needs to see and choose
        // among all their own vehicles regardless of distance, not have
        // distant ones hidden. No ST_DWithin radius filter here; when a
        // pickup point is known, PostGIS's ST_Distance replaces the PHP
        // Haversine call for the distance figure itself (geodesic, and
        // consistent with the other migrated endpoints), but every other
        // part of the ranking (availability-first, then nearest) is
        // unchanged.
        $vehiclesQuery = Vehicle::where('fleet_owner_id', $owner->id)
            ->where('vehicle_category', $requiredCategory)
            ->with('user');

        if ($pickupLat !== null && $pickupLng !== null) {
            $vehiclesQuery->selectRaw(
                'vehicles.*, ST_Distance(position, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$pickupLng, $pickupLat]
            );
        }

        $vehicles = $vehiclesQuery->get();

        $activeVehicleIds = Booking::whereIn('vehicle_id', $vehicles->pluck('id'))
            ->whereNotIn('booking_status', ['completed'])
            ->pluck('vehicle_id')
            ->unique();

        $candidates = $vehicles->map(function (Vehicle $v) use ($pickupLat, $pickupLng, $activeVehicleIds) {
            $online = $v->last_location_at && $v->last_location_at->gt(now()->subMinutes(self::ONLINE_MINUTES));
            $hasActiveBooking = $activeVehicleIds->contains($v->id);
            $available = $v->availability_status === 'available' && !$hasActiveBooking;

            $distanceKm = isset($v->distance_km_pg) ? round((float) $v->distance_km_pg, 1) : null;

            $headingTowardPickup = null;
            if (!$available && $pickupLat !== null && $pickupLng !== null) {
                $headingTowardPickup = $this->isHeadingToward($v, $pickupLat, $pickupLng);
            }

            return [
                'vehicle_id'            => $v->id,
                'plate_number'          => $v->plate_number,
                'truck_type'            => $v->truck_type,
                'vehicle_category'      => $v->vehicle_category,
                'driver_id'             => $v->user_id,
                'driver_name'           => $v->user?->full_name,
                'driver_phone'          => $v->user?->phone,
                'online'                => (bool) $online,
                'available'             => (bool) $available,
                'distance_km'           => $distanceKm,
                'heading_toward_pickup' => $headingTowardPickup,
                'current_city'          => $v->current_city,
                'last_location_at'      => $v->last_location_at?->toISOString(),
            ];
        });

        return $candidates
            ->sortBy([
                fn ($a, $b) => ($b['available'] <=> $a['available']),
                fn ($a, $b) => ($a['distance_km'] ?? PHP_INT_MAX) <=> ($b['distance_km'] ?? PHP_INT_MAX),
            ])
            ->values()
            ->all();
    }

    /** Prefer stored GPS coords; fall back to city-name lookup, same convention used elsewhere. */
    private function resolvePickupCoords(CargoRequest $c): array
    {
        if ($c->pickup_lat && $c->pickup_lng) {
            return [(float) $c->pickup_lat, (float) $c->pickup_lng];
        }
        $loc = $c->service_type === 'intracity' ? ($c->city ?? '') : ($c->pickup_location ?? '');
        foreach (CityCoordinates::COORDS as $name => $coords) {
            if (stripos($loc, $name) !== false) {
                return $coords;
            }
        }
        return [null, null];
    }

    /**
     * Best-effort: is this vehicle's last direction of travel (from its two
     * most recent trip breadcrumbs) pointed roughly toward the target point?
     * Returns null (unknown) if there isn't enough breadcrumb history.
     */
    private function isHeadingToward(Vehicle $v, float $targetLat, float $targetLng): ?bool
    {
        $trip = \App\Models\Trip::whereHas('booking', fn ($q) => $q->where('vehicle_id', $v->id))
            ->where('trip_status', 'ongoing')
            ->latest()
            ->first();

        $points = $trip?->route_data ?? [];
        if (count($points) < 2) {
            return null;
        }

        $prev = $points[count($points) - 2];
        $last = $points[count($points) - 1];
        if (!isset($prev['lat'], $prev['lng'], $last['lat'], $last['lng'])) {
            return null;
        }

        $travelBearing = self::bearingDeg((float) $prev['lat'], (float) $prev['lng'], (float) $last['lat'], (float) $last['lng']);
        $targetBearing = self::bearingDeg((float) $last['lat'], (float) $last['lng'], $targetLat, $targetLng);

        $diff = fmod(abs($travelBearing - $targetBearing), 360);
        $diff = $diff > 180 ? 360 - $diff : $diff;

        return $diff <= 45;
    }

    private static function bearingDeg(float $lat1, float $lng1, float $lat2, float $lng2): float
    {
        $phi1 = deg2rad($lat1); $phi2 = deg2rad($lat2);
        $deltaLambda = deg2rad($lng2 - $lng1);
        $y = sin($deltaLambda) * cos($phi2);
        $x = cos($phi1) * sin($phi2) - sin($phi1) * cos($phi2) * cos($deltaLambda);
        return fmod((rad2deg(atan2($y, $x)) + 360), 360);
    }

}
