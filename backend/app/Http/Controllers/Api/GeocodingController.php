<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\CityCoordinates;
use App\Services\DistanceService;
use Illuminate\Http\Request;

class GeocodingController extends Controller
{
    /**
     * POST /geocode/nearest-city
     * Finds the nearest Ethiopian city from the canonical city lookup table.
     * Returns city name, distance to that city centre, and city-centre coords.
     */
    public function nearestCity(Request $request)
    {
        $request->validate([
            'lat' => 'required|numeric|between:-90,90',
            'lng' => 'required|numeric|between:-180,180',
        ]);

        $lat = (float) $request->input('lat');
        $lng = (float) $request->input('lng');

        $bestCity   = CityCoordinates::nearestCity($lat, $lng);
        $bestCoords = CityCoordinates::get($bestCity);
        $bestDist   = DistanceService::haversineKm($lat, $lng, $bestCoords[0], $bestCoords[1]);

        return response()->json([
            'city'        => $bestCity,
            'distance_km' => round($bestDist, 1),
            'lat'         => $bestCoords[0],
            'lng'         => $bestCoords[1],
        ]);
    }
}
