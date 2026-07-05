<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class RoutingService
{
    private const OSRM_ROUTE   = 'http://router.project-osrm.org/route/v1/driving';
    private const OSRM_NEAREST = 'http://router.project-osrm.org/nearest/v1/driving';
    private const NOM_BASE     = 'https://nominatim.openstreetmap.org';
    private const USER_AGENT   = 'EthioLoadAI/1.0 (contact@ethioloadai.et)';
    private const TIMEOUT      = 5;

    /**
     * Get driving route between two points via OSRM.
     * Cached 1 hour. Returns null on failure — caller must fall back to haversine.
     * IMPORTANT: OSRM expects coordinates in lng,lat order.
     */
    public function getRoute(float $lat1, float $lng1, float $lat2, float $lng2): ?array
    {
        $key = sprintf('osrm_route:%.5f:%.5f:%.5f:%.5f', $lat1, $lng1, $lat2, $lng2);

        return Cache::remember($key, 3600, function () use ($lat1, $lng1, $lat2, $lng2) {
            try {
                $coords   = "{$lng1},{$lat1};{$lng2},{$lat2}";
                $response = Http::timeout(self::TIMEOUT)
                    ->get(self::OSRM_ROUTE . "/{$coords}", [
                        'overview'     => 'full',
                        'geometries'   => 'geojson',
                        'alternatives' => 'true',
                        'steps'        => 'true',
                    ]);

                if (!$response->successful()) return null;

                $data   = $response->json();
                $routes = $data['routes'] ?? [];
                if (empty($routes)) return null;

                $best  = $routes[0];
                $steps = [];
                foreach ($best['legs'][0]['steps'] ?? [] as $step) {
                    if (empty($step['maneuver'])) continue;
                    $steps[] = [
                        'instruction' => $step['name'] ?? '',
                        'distance_m'  => (int) round($step['distance'] ?? 0),
                        'duration_s'  => (int) round($step['duration'] ?? 0),
                        'maneuver'    => $step['maneuver']['type'] ?? 'turn',
                        'bearing_before' => $step['maneuver']['bearing_before'] ?? 0,
                        'bearing_after'  => $step['maneuver']['bearing_after']  ?? 0,
                    ];
                }

                $alternatives = [];
                foreach (array_slice($routes, 1) as $alt) {
                    $alternatives[] = [
                        'distance_km'  => round(($alt['distance'] ?? 0) / 1000, 1),
                        'duration_min' => (int) round(($alt['duration'] ?? 0) / 60),
                        'polyline'     => $alt['geometry']['coordinates'] ?? [],
                    ];
                }

                return [
                    'distance_km'  => round(($best['distance'] ?? 0) / 1000, 1),
                    'duration_min' => (int) round(($best['duration'] ?? 0) / 60),
                    'polyline'     => $best['geometry']['coordinates'] ?? [],
                    'steps'        => $steps,
                    'alternatives' => $alternatives,
                    'source'       => 'osrm',
                ];
            } catch (\Throwable $e) {
                Log::warning('OSRM getRoute failed', ['error' => $e->getMessage()]);
                return null;
            }
        });
    }

    /**
     * Snap a position to the nearest road via OSRM.
     * Cached 30 minutes. Returns ['lat', 'lng'] or null.
     */
    public function nearestRoad(float $lat, float $lng): ?array
    {
        $key = sprintf('osrm_nearest:%.5f:%.5f', $lat, $lng);

        return Cache::remember($key, 1800, function () use ($lat, $lng) {
            try {
                $response = Http::timeout(self::TIMEOUT)
                    ->get(self::OSRM_NEAREST . "/{$lng},{$lat}");

                if (!$response->successful()) return null;

                $loc = $response->json()['waypoints'][0]['location'] ?? null;
                if (!$loc || count($loc) < 2) return null;

                return ['lat' => $loc[1], 'lng' => $loc[0]]; // OSRM returns [lng,lat]
            } catch (\Throwable $e) {
                Log::warning('OSRM nearestRoad failed', ['error' => $e->getMessage()]);
                return null;
            }
        });
    }

    /**
     * Search places in Ethiopia by name via Nominatim.
     * Cached 24 hours.
     */
    public function searchPlace(string $query): array
    {
        $key    = 'nom_search:' . md5(strtolower(trim($query)));
        $result = Cache::remember($key, 86400, function () use ($query) {
            try {
                $response = Http::timeout(self::TIMEOUT)
                    ->withHeaders(['User-Agent' => self::USER_AGENT])
                    ->get(self::NOM_BASE . '/search', [
                        'q'            => $query,
                        'format'       => 'json',
                        'limit'        => 5,
                        'countrycodes' => 'et',
                    ]);

                if (!$response->successful()) return [];

                return collect($response->json())->map(fn ($p) => [
                    'name'       => $p['display_name'] ?? '',
                    'short_name' => $p['name']         ?? explode(',', $p['display_name'] ?? '')[0],
                    'lat'        => (float) ($p['lat'] ?? 0),
                    'lng'        => (float) ($p['lon'] ?? 0),
                    'type'       => $p['type'] ?? '',
                ])->values()->all();
            } catch (\Throwable $e) {
                Log::warning('Nominatim searchPlace failed', ['error' => $e->getMessage()]);
                return [];
            }
        });

        return $result ?? [];
    }

    /**
     * Reverse geocode coordinates to a human-readable address via Nominatim.
     * Cached 6 hours.
     */
    public function reverseGeocode(float $lat, float $lng): ?array
    {
        $key = sprintf('nom_reverse:%.4f:%.4f', $lat, $lng);

        return Cache::remember($key, 21600, function () use ($lat, $lng) {
            try {
                $response = Http::timeout(self::TIMEOUT)
                    ->withHeaders(['User-Agent' => self::USER_AGENT])
                    ->get(self::NOM_BASE . '/reverse', [
                        'lat'    => $lat,
                        'lon'    => $lng,
                        'format' => 'json',
                    ]);

                if (!$response->successful()) return null;

                $data = $response->json();
                if (empty($data['display_name'])) return null;

                $addr  = $data['address'] ?? [];
                $short = $addr['suburb']       ?? $addr['neighbourhood'] ?? $addr['road']
                    ?? $addr['city']        ?? $addr['town']           ?? $addr['village']
                    ?? explode(',', $data['display_name'])[0];

                return [
                    'address'       => $data['display_name'],
                    'short_address' => trim($short),
                    'city'          => $addr['city'] ?? $addr['town'] ?? $addr['village'] ?? null,
                ];
            } catch (\Throwable $e) {
                Log::warning('Nominatim reverseGeocode failed', ['error' => $e->getMessage()]);
                return null;
            }
        });
    }
}
