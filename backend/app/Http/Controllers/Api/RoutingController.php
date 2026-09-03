<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\DistanceService;
use App\Services\RoutingService;
use Illuminate\Http\Request;

class RoutingController extends Controller
{
    public function __construct(private RoutingService $routing) {}

    /**
     * GET /routing/route?from_lat=&from_lng=&to_lat=&to_lng=
     * Returns OSRM route with polyline + steps, or haversine fallback.
     */
    public function route(Request $request)
    {
        $v = $request->validate([
            'from_lat' => 'required|numeric|between:-90,90',
            'from_lng' => 'required|numeric|between:-180,180',
            'to_lat'   => 'required|numeric|between:-90,90',
            'to_lng'   => 'required|numeric|between:-180,180',
        ]);

        $route = $this->routing->getRoute(
            (float) $v['from_lat'], (float) $v['from_lng'],
            (float) $v['to_lat'],   (float) $v['to_lng']
        );

        if ($route) {
            return response()->json($route);
        }

        // Haversine fallback when OSRM is unreachable
        $distKm = DistanceService::haversineKm(
            (float) $v['from_lat'], (float) $v['from_lng'],
            (float) $v['to_lat'],   (float) $v['to_lng']
        ) * DistanceService::ROAD_DISTANCE_INFLATION_FACTOR;

        return response()->json([
            'distance_km'  => round($distKm, 1),
            'duration_min' => max(1, (int) round($distKm / 60 * 60)),
            'polyline'     => [],
            'steps'        => [],
            'alternatives' => [],
            'source'       => 'haversine_fallback',
        ]);
    }

    /**
     * GET /routing/search?q=Mercato+Addis
     */
    public function search(Request $request)
    {
        $request->validate(['q' => 'required|string|max:200']);
        $places = $this->routing->searchPlace($request->input('q'));
        return response()->json(['places' => $places]);
    }

    /**
     * GET /routing/reverse?lat=&lng=
     */
    public function reverse(Request $request)
    {
        $v = $request->validate([
            'lat' => 'required|numeric|between:-90,90',
            'lng' => 'required|numeric|between:-180,180',
        ]);

        $result = $this->routing->reverseGeocode((float) $v['lat'], (float) $v['lng']);

        return response()->json($result ?? [
            'address'       => "{$v['lat']}, {$v['lng']}",
            'short_address' => 'Unknown location',
            'city'          => null,
        ]);
    }
}
