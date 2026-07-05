<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class GeocodingController extends Controller
{
    /**
     * POST /geocode/nearest-city
     * Finds the nearest Ethiopian city from the 44-city lookup table.
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

        $bestCity   = null;
        $bestDist   = PHP_FLOAT_MAX;
        $bestCoords = null;

        foreach (VehicleController::CITY_COORDS as $city => [$clat, $clng]) {
            $dist = self::haversineKm($lat, $lng, $clat, $clng);
            if ($dist < $bestDist) {
                $bestDist   = $dist;
                $bestCity   = $city;
                $bestCoords = [$clat, $clng];
            }
        }

        return response()->json([
            'city'        => $bestCity,
            'distance_km' => round($bestDist, 1),
            'lat'         => $bestCoords[0],
            'lng'         => $bestCoords[1],
        ]);
    }

    private static function haversineKm(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R    = 6371.0;
        $phi1 = deg2rad($lat1); $phi2 = deg2rad($lat2);
        $dphi = deg2rad($lat2 - $lat1);
        $dlam = deg2rad($lon2 - $lon1);
        $a    = sin($dphi / 2) ** 2 + cos($phi1) * cos($phi2) * sin($dlam / 2) ** 2;
        return 2 * $R * asin(sqrt($a));
    }
}
