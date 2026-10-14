<?php

namespace App\Services;

/**
 * Single canonical source for great-circle distance calculations.
 * Replaces eight independently-duplicated Haversine implementations
 * that existed across the codebase (VehicleController, CargoRequestController,
 * GeocodingController, AiController, RoutingController, FleetService,
 * BidService, BackhaulService).
 */
class DistanceService
{
    private const EARTH_RADIUS_KM = 6371.0;

    /** Straight-line (great-circle) distance in km between two points. */
    public static function haversineKm(float $lat1, float $lng1, float $lat2, float $lng2): float
    {
        $dLat = deg2rad($lat2 - $lat1);
        $dLng = deg2rad($lng2 - $lng1);
        $a = sin($dLat / 2) ** 2
            + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLng / 2) ** 2;
        $c = 2 * atan2(sqrt($a), sqrt(1 - $a));
        return self::EARTH_RADIUS_KM * $c;
    }

    /**
     * Straight-line distance inflated by a fixed factor, as a coarse
     * road-distance approximation when no real routing-engine result is
     * available. Empirically derived from AiController's $distFromAA table
     * (actual measured road distances for ~35 Ethiopian corridors) compared
     * against straight-line distance for the same city pairs — the true
     * ratio for real corridors ranges ~1.24-1.76 (average ~1.47), reflecting
     * Ethiopia's mountainous terrain; this replaces two previously
     * inconsistent ad-hoc values (1.25 in RoutingController, 1.6 in
     * AiController) with one standardized, evidence-grounded constant.
     * Prefer RoutingService's actual road distance whenever it can be reached
     * — this is a fallback only.
     */
    public const ROAD_DISTANCE_INFLATION_FACTOR = 1.4;
}
