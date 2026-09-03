<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class RoutingService
{
    private const USER_AGENT   = 'EthioLoadAI/1.0 (contact@ethioloadai.et)';
    private const TIMEOUT      = 5;

    /** Self-hosted OSRM base URL (docker/osrm/) — see config/services.php + OSRM_BASE_URL. */
    private string $osrmBase;

    /**
     * Self-hosted Nominatim base URL (docker-compose.yml `nominatim` service)
     * — see config/services.php + NOMINATIM_BASE_URL. Replaces the public
     * nominatim.openstreetmap.org instance, which caps usage at 1 req/s and
     * isn't meant for production traffic. Same shape/response as the public
     * API (same software, self-hosted), so searchPlace()/reverseGeocode()
     * needed no parsing changes — only where the request goes.
     */
    private string $nominatimBase;

    public function __construct()
    {
        $this->osrmBase = rtrim(config('services.osrm.url', env('OSRM_BASE_URL', 'http://localhost:5001')), '/');
        $this->nominatimBase = rtrim(config('services.nominatim.url', env('NOMINATIM_BASE_URL', 'http://localhost:8088')), '/');
    }

    /**
     * Get driving route between two points via self-hosted OSRM.
     * Successful results are cached 1 hour. Returns null on failure — never
     * cached, so the very next call retries OSRM live rather than replaying a
     * stale failure. Caller must fall back to haversine on a null return.
     * IMPORTANT: OSRM expects coordinates in lng,lat order.
     */
    public function getRoute(float $lat1, float $lng1, float $lat2, float $lng2): ?array
    {
        $key = sprintf('osrm_route:%.5f:%.5f:%.5f:%.5f', $lat1, $lng1, $lat2, $lng2);

        $cached = Cache::get($key);
        if ($cached !== null) {
            return $cached;
        }

        try {
            $coords   = "{$lng1},{$lat1};{$lng2},{$lat2}";
            $response = Http::timeout(self::TIMEOUT)
                ->get("{$this->osrmBase}/route/v1/driving/{$coords}", [
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

            $result = [
                'distance_km'  => round(($best['distance'] ?? 0) / 1000, 1),
                'duration_min' => (int) round(($best['duration'] ?? 0) / 60),
                'polyline'     => $best['geometry']['coordinates'] ?? [],
                'steps'        => $steps,
                'alternatives' => $alternatives,
                'source'       => 'osrm',
            ];

            Cache::put($key, $result, 3600);

            return $result;
        } catch (\Throwable $e) {
            Log::warning('OSRM getRoute failed', ['error' => $e->getMessage()]);
            return null;
        }
    }

    /**
     * Snap a position to the nearest road via self-hosted OSRM.
     * Successful results cached 30 minutes; failures are never cached (see getRoute()).
     * Returns ['lat', 'lng'] or null.
     */
    public function nearestRoad(float $lat, float $lng): ?array
    {
        $key = sprintf('osrm_nearest:%.5f:%.5f', $lat, $lng);

        $cached = Cache::get($key);
        if ($cached !== null) {
            return $cached;
        }

        try {
            $response = Http::timeout(self::TIMEOUT)
                ->get("{$this->osrmBase}/nearest/v1/driving/{$lng},{$lat}");

            if (!$response->successful()) return null;

            $loc = $response->json()['waypoints'][0]['location'] ?? null;
            if (!$loc || count($loc) < 2) return null;

            $result = ['lat' => $loc[1], 'lng' => $loc[0]]; // OSRM returns [lng,lat]

            Cache::put($key, $result, 1800);

            return $result;
        } catch (\Throwable $e) {
            Log::warning('OSRM nearestRoad failed', ['error' => $e->getMessage()]);
            return null;
        }
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
                    ->get($this->nominatimBase . '/search', [
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
                    ->get($this->nominatimBase . '/reverse', [
                        'lat'    => $lat,
                        'lon'    => $lng,
                        'format' => 'json',
                    ]);

                if (!$response->successful()) return null;

                $data = $response->json();
                if (empty($data['display_name'])) return null;

                // Settlement names take priority over a road ref — Nominatim
                // frequently tags a rural point with both (e.g. road="B30",
                // town="Dabat"); a highway number is useless as a place
                // label, so it's demoted to a last-resort fallback here.
                $addr  = $data['address'] ?? [];
                $short = $addr['suburb']       ?? $addr['neighbourhood']
                    ?? $addr['city']        ?? $addr['town']           ?? $addr['village']
                    ?? $addr['road']
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
