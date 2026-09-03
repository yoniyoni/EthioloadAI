<?php

namespace App\Services;

use App\Models\CargoRequest;
use App\Models\PlatformSetting;
use App\Models\Trip;
use Illuminate\Support\Collection;

/**
 * Mid-trip cargo consolidation: while a driver is already en route with
 * spare capacity (e.g. picked up 10 of 20 tons in Gondar), find other
 * shippers' pending cargo near the vehicle's current position that would
 * still fit. This is deliberately separate from BackhaulService, which
 * matches near a trip's *destination* against *full* vehicle capacity for
 * an after-delivery return load — a different point in time and a
 * different capacity comparison than "can I fit this right now".
 */
class ConsolidationService
{
    public function __construct(protected TripService $tripService) {}

    /**
     * Live-computed (not persisted/cached) — remaining capacity changes
     * every time a stop is added, so a cached recommendation would go
     * stale immediately. Returns a plain array; empty if the trip has no
     * vehicle, no known capacity, or the vehicle's position is unknown.
     *
     * $radiusKm lets the driver widen/narrow the search (e.g. 50/100/150km)
     * for this one request; when omitted, falls back to the admin-configured
     * platform default.
     */
    public function findCandidates(Trip $trip, ?float $radiusKm = null): array
    {
        $trip->loadMissing('booking.vehicle', 'booking.cargoRequest', 'tripStops');
        $vehicle = $trip->booking?->vehicle;

        if (!$vehicle || $vehicle->capacity === null) {
            return [];
        }

        $remaining = $this->tripService->remainingCapacityTons($trip);
        if ($remaining === null || $remaining <= 0) {
            return [];
        }

        // Prefer the vehicle's live GPS position (updated throughout the
        // trip by the existing driver-location-push flow); fall back to
        // resolving the trip's start-location city name.
        $originLat = $vehicle->latitude;
        $originLng = $vehicle->longitude;
        if ($originLat === null || $originLng === null) {
            $coords = CityCoordinates::resolveFromText($trip->start_location ?? '');
            if (!$coords) {
                return [];
            }
            [$originLat, $originLng] = $coords;
        }

        $excludedCargoIds = $trip->tripStops->pluck('cargo_request_id')->filter()->values();
        if ($trip->booking->cargo_id) {
            $excludedCargoIds->push($trip->booking->cargo_id);
        }

        // Same vehicle_category <-> service_type matching convention as
        // BackhaulService / FleetService.
        $requiredServiceType = $vehicle->vehicle_category === 'light' ? 'intracity' : 'intercity';

        $radiusKm     = $radiusKm ?? (float) PlatformSetting::get('spatial.consolidation_radius_km', 150);
        $radiusMeters = $radiusKm * 1000;

        $baseQuery = CargoRequest::where('status', 'pending')
            ->whereNotIn('id', $excludedCargoIds)
            ->where('weight', '<=', $remaining)
            ->where(function ($q) use ($requiredServiceType) {
                $q->where('service_type', $requiredServiceType);
                if ($requiredServiceType === 'intercity') {
                    $q->orWhereNull('service_type');
                }
            });

        $tierA = (clone $baseQuery)
            ->whereNotNull('pickup_point')
            ->whereRaw(
                'ST_DWithin(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)',
                [$originLng, $originLat, $radiusMeters]
            )
            ->orderByRaw(
                'pickup_point <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography',
                [$originLng, $originLat]
            )
            ->selectRaw(
                'cargo_requests.*, ST_Distance(pickup_point, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 AS distance_km_pg',
                [$originLng, $originLat]
            )
            ->with('user')
            ->get()
            ->each(function (CargoRequest $c) {
                $c->distance_km = round((float) $c->distance_km_pg, 1);
            });

        $tierB = (clone $baseQuery)
            ->whereNull('pickup_point')
            ->with('user')
            ->get()
            ->map(function (CargoRequest $c) use ($originLat, $originLng) {
                $loc = $c->service_type === 'intracity' ? ($c->city ?? '') : ($c->pickup_location ?? '');
                $coords = CityCoordinates::resolveFromText($loc);
                if (!$coords) {
                    $c->distance_km = null;
                    return $c;
                }
                [$pLat, $pLng] = $coords;
                $c->distance_km = round(DistanceService::haversineKm($originLat, $originLng, $pLat, $pLng), 1);
                return $c;
            })
            ->filter(fn (CargoRequest $c) => $c->distance_km !== null && $c->distance_km <= $radiusKm);

        $candidates = $tierA->concat($tierB)->sortBy('distance_km')->values();

        return $candidates->map(fn (CargoRequest $c) => [
            'id'              => $c->id,
            'pickup_location' => $c->service_type === 'intracity' ? $c->city : $c->pickup_location,
            'destination'     => $c->destination,
            'material_type'   => $c->material_type,
            'weight'          => (float) $c->weight,
            'urgency_level'   => $c->urgency_level,
            'budget'          => $c->budget !== null ? (float) $c->budget : null,
            'price_type'      => $c->price_type ?? 'negotiable',
            'distance_km'     => $c->distance_km,
            // Phone deliberately omitted — same reasoning as BidResource/
            // BookingResource: payment flows through the platform, so
            // there's no need for the driver to contact this shipper
            // directly, even at the pre-agreement candidate stage.
            'shipper_name'    => $c->user?->full_name ?? 'Unknown',
        ])->all();
    }
}
