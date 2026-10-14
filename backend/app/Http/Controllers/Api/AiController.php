<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformSetting;
use App\Services\CityCoordinates;
use App\Services\DistanceService;
use App\Services\RoutingService;
use Illuminate\Http\Request;

class AiController extends Controller
{
    protected $aiEngine;

    public function __construct(
        \App\Services\AiEngineService $aiEngine,
        private RoutingService $routing,
    ) {
        $this->aiEngine = $aiEngine;
    }

    public function recommendTruck(Request $request)
    {
        $payload = $request->all();

        $vehicles  = \App\Models\Vehicle::with('user')
            ->where('availability_status', 'available')
            ->whereHas('user', fn ($q) => $q->where('verification_status', true))
            ->get();
        $driverIds = $vehicles->pluck('user_id')->filter()->unique()->values();

        // Real performance signal, batched (not N+1) — see localTruckRecommend()
        // / recommendation_service.py for how these feed the ranking. Ratings
        // are only counted where the SHIPPER was the rater (excludes a driver
        // rating their own shipper, which shares the same driver_id column).
        $avgRatings = \App\Models\Rating::whereIn('driver_id', $driverIds)
            ->whereColumn('rater_id', 'shipper_id')
            ->selectRaw('driver_id, AVG(rating) as avg_rating')
            ->groupBy('driver_id')
            ->pluck('avg_rating', 'driver_id');

        $bidStats = \App\Models\Bid::whereIn('driver_id', $driverIds)
            ->selectRaw("driver_id, COUNT(*) as total, SUM(CASE WHEN status = 'accepted' THEN 1 ELSE 0 END) as accepted")
            ->groupBy('driver_id')
            ->get()
            ->keyBy('driver_id');

        // Inject real available vehicles + driver names from DB
        $payload['truck_fleet'] = $vehicles
            ->map(function ($v) use ($avgRatings, $bidStats) {
                $stats = $bidStats->get($v->user_id);
                return [
                    'truck_id'        => $v->id,
                    'driver_name'     => $v->user?->full_name ?? ('Driver #' . $v->user_id),
                    'plate_number'    => $v->plate_number,
                    'capacity'        => (float) ($v->capacity ?? 20),
                    'base_location'   => strtolower($v->current_city ?? 'addis ababa'),
                    // null (not 0) when there's no history yet — both engines
                    // apply a neutral, cold-start-safe default rather than
                    // penalizing a driver for lacking a track record.
                    'avg_rating'      => isset($avgRatings[$v->user_id]) ? round((float) $avgRatings[$v->user_id], 2) : null,
                    'acceptance_rate' => ($stats && $stats->total > 0) ? round($stats->accepted / $stats->total, 2) : null,
                ];
            })
            ->toArray();

        $result = $this->aiEngine->recommendTruck($payload);

        if (!empty($result['error'])) {
            $result = $this->localTruckRecommend(
                pickup:    $request->input('pickup_location', ''),
                dest:      $request->input('destination', ''),
                weight:    (float) $request->input('weight', 10),
                material:  $request->input('material_type', 'general'),
                urgency:   $request->input('urgency_level', 'normal'),
                fleet:     $payload['truck_fleet'],
            );
        }

        return response()->json($result);
    }

    public function backhaulOpportunities(Request $request)
    {
        $payload = $request->all();
        
        // Dynamically fetch pending cargo from the database
        $payload['available_cargo'] = \App\Models\CargoRequest::where('status', 'pending')
            ->get()
            ->map(function ($cargo) {
                return [
                    'cargo_id' => $cargo->id,
                    'pickup_location' => $cargo->pickup_location,
                    'destination' => $cargo->destination,
                    'weight' => (float) $cargo->weight,
                    'price' => (float) $cargo->budget,
                ];
            })->toArray();

        $result = $this->aiEngine->backhaulOpportunities($payload);
        return response()->json($result);
    }

    public function predictPrice(Request $request)
    {
        $payload = array_merge($request->all(), [
            'rate_min' => (float) PlatformSetting::get('pricing.rate_min', 18),
            'rate_max' => (float) PlatformSetting::get('pricing.rate_max', 28),
        ]);
        $result = $this->aiEngine->predictPrice($payload);

        // Fallback: if AI engine is down, compute a local distance-based estimate
        if (!empty($result['error'])) {
            $from     = $request->input('from', $request->input('pickup_location', ''));
            $to       = $request->input('to', $request->input('destination', ''));
            $weight   = max((float) $request->input('weight', 10), 1);
            $urgency  = $request->input('urgency_level', 'normal');
            $material = $request->input('material_type', '');

            // Use OSRM real road distance whenever at least one side has real
            // coordinates — resolve the other side from its city name rather
            // than discarding good GPS data just because the caller only
            // sent coordinates for one end (e.g. GPS pickup + dropdown-picked
            // destination, which never carries its own lat/lng client-side).
            $osrmDistKm = null;
            $fromLat = $request->input('from_lat');
            $fromLng = $request->input('from_lng');
            $toLat   = $request->input('to_lat');
            $toLng   = $request->input('to_lng');

            if (!$fromLat || !$fromLng) {
                $fromCoords = CityCoordinates::resolveFromText($from);
                if ($fromCoords) [$fromLat, $fromLng] = $fromCoords;
            }
            if (!$toLat || !$toLng) {
                $toCoords = CityCoordinates::resolveFromText($to);
                if ($toCoords) [$toLat, $toLng] = $toCoords;
            }

            if ($fromLat && $fromLng && $toLat && $toLng) {
                $route = $this->routing->getRoute(
                    (float) $fromLat, (float) $fromLng,
                    (float) $toLat,   (float) $toLng
                );
                $osrmDistKm = $route['distance_km'] ?? null;
            }

            $result = $this->localPriceEstimate($from, $to, $weight, $urgency, $material, $osrmDistKm);
        }

        return response()->json($result);
    }

    // ── Road distances (km) from Addis Ababa — actual route measurements ──
    private static array $distFromAA = [
        'Adama'                => 99,
        'Adama / Nazret'       => 99,
        'Asella'               => 175,
        'Awash'                => 225,
        'Bahir Dar'            => 565,
        'Bishoftu'             => 50,
        'Bale Robe'            => 430,
        'Debre Birhan'         => 130,
        'Debre Markos'         => 300,
        'Dessie'               => 401,
        'Kombolcha'            => 395,
        'Dilla'                => 367,
        'Dire Dawa'            => 515,
        'Gambela'              => 769,
        'Goba'                 => 445,
        'Gondar'               => 738,
        'Harar'                => 526,
        'Hawassa'              => 275,
        'Humera'               => 950,
        'Jijiga'               => 633,
        'Jimma'                => 346,
        'Kebri Dahar'          => 840,
        'Mekele'               => 783,
        'Moyale'               => 770,
        'Nekemte'              => 331,
        'Shashemene'           => 250,
        'Shire / Endaselassie' => 900,
        'Shire'                => 900,
        'Axum'                 => 1020,
        'Adigrat'              => 870,
        'Sodo / Wolaita'       => 370,
        'Sodo'                 => 370,
        'Woldia'               => 521,
        'Assosa'               => 668,
        'Arba Minch'           => 505,
        'Ziway'                => 163,
        'Butajira'             => 170,
        'Hosanna'              => 230,
        'Lalibela'             => 696,
        'Debre Tabor'          => 667,
        'Wukro'                => 800,
        'Dangila'              => 580,
    ];

    /** Set by roadDistKm() on its most recent call — did that result come from real OSRM routing? */
    private bool $lastRoadDistUsedOsrm = false;

    /**
     * Road-distance estimate between two (already-normalized) city names,
     * used only when the caller didn't supply real GPS coordinates (see
     * predictPrice()'s $osrmDistKm fast path, which bypasses this entirely).
     * Tiered: real OSRM road route (via CityCoordinates-resolved city-centre
     * points) > hand-measured Addis-anchored table > haversine×inflation
     * estimate > coarse table-difference heuristic. Each tier only runs if
     * the one before it couldn't produce a number — OSRM being unreachable
     * (or a city not resolving to coordinates) falls through to the
     * pre-existing chain exactly as it behaved before this migration.
     */
    private function roadDistKm(string $nFrom, string $nTo): int
    {
        $this->lastRoadDistUsedOsrm = false;

        $aa = 'Addis Ababa';
        if ($nFrom === $aa && $nTo === $aa) return 0;

        $cf = CityCoordinates::get($nFrom);
        $ct = CityCoordinates::get($nTo);
        if ($cf && $ct) {
            $route = $this->routing->getRoute($cf[0], $cf[1], $ct[0], $ct[1]);
            if ($route !== null) {
                $this->lastRoadDistUsedOsrm = true;
                return max(10, (int) round($route['distance_km']));
            }
        }

        if ($nFrom === $aa || $nFrom === '') return self::$distFromAA[$nTo]  ?? 400;
        if ($nTo   === $aa || $nTo   === '') return self::$distFromAA[$nFrom] ?? 400;

        if ($cf && $ct) {
            $straight = DistanceService::haversineKm($cf[0], $cf[1], $ct[0], $ct[1]);
            return max(10, (int) round($straight * DistanceService::ROAD_DISTANCE_INFLATION_FACTOR));
        }

        $df = self::$distFromAA[$nFrom] ?? null;
        $dt = self::$distFromAA[$nTo]   ?? null;
        if ($df && $dt) {
            return (int) round(abs($df - $dt) + min($df, $dt) * 0.3);
        }
        return $df ?? ($dt ?? 400);
    }

    private function normalizeCity(string $raw): string
    {
        $s = trim($raw);
        // Strip region suffixes (e.g. "Adama, Oromia" → "Adama")
        $s = preg_split('/[,\/]/', $s)[0];
        $s = trim($s);
        // Known aliases (mapped to CityCoordinates' single-word canonical spellings)
        $aliases = [
            'addis'        => 'Addis Ababa',
            'nazret'       => 'Adama',
            'adama'        => 'Adama',
            'wolaita'      => 'Sodo',
            'endaselassie' => 'Shire',
        ];
        $lower = strtolower($s);
        foreach ($aliases as $needle => $canonical) {
            if (str_contains($lower, $needle)) return $canonical;
        }
        return ucwords($s);
    }

    private function localPriceEstimate(string $from, string $to, float $weight, string $urgency, string $material = '', ?float $osrmDistKm = null): array
    {
        $nFrom  = $this->normalizeCity($from);
        $nTo    = $this->normalizeCity($to);
        // Prefer real OSRM road distance; roadDistKm() itself now also tries
        // OSRM (via city-centre coordinates) before falling back to the
        // hand-measured table / haversine×1.4 estimate / heuristic.
        $distKm   = $osrmDistKm ?? $this->roadDistKm($nFrom, $nTo);
        $usedOsrm = $osrmDistKm !== null || $this->lastRoadDistUsedOsrm;

        $rateMin = (float) PlatformSetting::get('pricing.rate_min', 18);
        $rateMax = (float) PlatformSetting::get('pricing.rate_max', 28);

        $urgencyMultiplier = match ($urgency) {
            'express' => 1.4,
            'high'    => 1.2,
            default   => 1.0,
        };

        $materialMultiplier = $this->materialMultiplier($material);

        $baseMin = $distKm * $rateMin * $weight * $urgencyMultiplier * $materialMultiplier;
        $baseMax = $distKm * $rateMax * $weight * $urgencyMultiplier * $materialMultiplier;

        // Round to nearest 500 ETB
        $priceMin = (int) (round($baseMin / 500) * 500);
        $priceMax = (int) (round($baseMax / 500) * 500);

        return [
            'price_min'       => $priceMin,
            'price_max'       => $priceMax,
            'estimated_price' => (int) (($priceMin + $priceMax) / 2),
            'distance_km'     => $distKm,
            'currency'        => 'ETB',
            'confidence'      => $usedOsrm ? 0.88 : 0.80,
            'source'          => $usedOsrm ? 'local_estimate_osrm' : 'local_estimate',
        ];
    }

    private function materialMultiplier(string $material): float
    {
        $m = strtolower($material);

        $fragile = ['glass', 'electronic', 'ceramic', 'machinery part', 'medical'];
        foreach ($fragile as $kw) {
            if (str_contains($m, $kw)) return 1.25;
        }

        $perishable = ['vegetable', 'fruit', 'dairy', 'fish', 'meat', 'teff', 'grain', 'coffee'];
        foreach ($perishable as $kw) {
            if (str_contains($m, $kw)) return 1.15;
        }

        $bulk = ['cement', 'sand', 'gravel', 'scrap', 'construction', 'stone', 'aggregate'];
        foreach ($bulk as $kw) {
            if (str_contains($m, $kw)) return 0.9;
        }

        return 1.0;
    }

    public function predictEmptyReturn(Request $request)
    {
        $result = $this->aiEngine->predictEmptyReturn($request->all());

        if (!empty($result['error'])) {
            $result = $this->localEmptyReturnEstimate($request->input('destination', ''));
        }

        return response()->json($result);
    }

    public function optimizeRoute(Request $request)
    {
        $result = $this->aiEngine->optimizeRoute($request->all());
        return response()->json($result);
    }

    /**
     * Cold-start-safe defaults for drivers with no rating/bid history yet —
     * neutral-good, not zero, so a new driver isn't punished for lacking a
     * track record. 4.0/5 and 70% acceptance are reasonable "unproven but
     * assume decent" priors for a pilot-stage marketplace.
     */
    private const NEUTRAL_RATING           = 4.0;
    private const NEUTRAL_ACCEPTANCE_RATE  = 0.7;

    /**
     * Weighted-score truck ranking, informed by real driver performance
     * (rating, bid-acceptance rate) alongside trip fit (capacity, proximity,
     * urgency, material) — not just static trip attributes. Previously
     * capacity/proximity alone dominated once "big enough" trucks all hit
     * capacity_fit's 1.0 cap, which meant proximity to pickup was the only
     * real differentiator — and since most cargo originates near Addis
     * Ababa, the same Addis-based truck(s) won almost every call. Folding in
     * rating/acceptance gives the ranking a real, accumulating signal beyond
     * geography, and #_shuffleTiedScores() below stops equally-scored trucks
     * from always resolving to the same one via array order.
     */
    private function localTruckRecommend(string $pickup, string $dest, float $weight, string $material, string $urgency, array $fleet): array
    {
        $routeKm = $this->distBetween($pickup, $dest);

        $urgencyMul  = match ($urgency) { 'express' => 1.25, 'high' => 1.1, 'low' => 0.9, default => 1.0 };
        $materialMul = $this->materialMultiplier($material);

        $scored = array_map(function ($truck) use ($pickup, $weight, $routeKm, $urgencyMul, $materialMul) {
            $proximityKm    = $this->distBetween($truck['base_location'] ?? 'addis ababa', $pickup);
            $capacityFit    = min(($truck['capacity'] ?? 20) / max($weight, 1), 1.0);
            $proxScore      = 1.0 / (1.0 + $proximityKm / 200.0);
            $ratingScore    = ($truck['avg_rating'] ?? self::NEUTRAL_RATING) / 5.0;
            $acceptScore    = $truck['acceptance_rate'] ?? self::NEUTRAL_ACCEPTANCE_RATE;

            $score = round(
                $capacityFit * 0.20
                + $urgencyMul * 0.10
                + $proxScore  * 0.20
                + $materialMul * 0.10
                + $ratingScore * 0.20
                + $acceptScore * 0.20,
                2
            );
            $estPrice = (int) round($routeKm * $weight * 23 * $materialMul * $urgencyMul);

            return [
                'truck_id'        => $truck['truck_id'],
                'driver_name'     => $truck['driver_name'],
                'plate_number'    => $truck['plate_number'],
                'capacity'        => (float) ($truck['capacity'] ?? 20),
                'distance_km'     => round($proximityKm, 1),
                'estimated_price' => $estPrice,
                'score'           => min($score, 1.0),
            ];
        }, $fleet);

        return ['recommended_trucks' => $this->shuffleTiedScores($scored)];
    }

    /**
     * Sorts descending by score, but randomizes the order WITHIN each group
     * of equal scores instead of leaving PHP's stable sort to always resolve
     * ties to the same array-order winner. Ranking quality is unaffected —
     * a higher-scored truck never moves below a lower-scored one — but two
     * genuinely-tied trucks (e.g. same proximity/capacity/rating bracket)
     * now take turns being #1 across calls rather than one fixed truck
     * always winning by accident of DB row order.
     */
    private function shuffleTiedScores(array $scored): array
    {
        $groups = [];
        foreach ($scored as $item) {
            $groups[(string) $item['score']][] = $item;
        }

        krsort($groups, SORT_NUMERIC);

        $result = [];
        foreach ($groups as $group) {
            shuffle($group);
            array_push($result, ...$group);
        }

        return $result;
    }

    private function distBetween(string $cityA, string $cityB): int
    {
        return $this->roadDistKm(
            $this->normalizeCity($cityA),
            $this->normalizeCity($cityB),
        );
    }

    private function localEmptyReturnEstimate(string $destination): array
    {
        $risks = [
            'Addis Ababa'  => 0.10, 'Adama'         => 0.15, 'Bishoftu'   => 0.18,
            'Shashemene'   => 0.22, 'Hawassa'        => 0.25, 'Jimma'      => 0.30,
            'Dire Dawa'    => 0.35, 'Bahir Dar'      => 0.38, 'Gondar'     => 0.42,
            'Dessie'       => 0.45, 'Woldia'         => 0.48, 'Mekele'     => 0.50,
            'Nekemte'      => 0.50, 'Debre Markos'   => 0.55, 'Dilla'      => 0.55,
            'Sodo'         => 0.55, 'Arba Minch'     => 0.60, 'Harar'      => 0.40,
            'Jijiga'       => 0.65, 'Gambela'        => 0.75, 'Assosa'     => 0.72,
            'Moyale'       => 0.80, 'Metema'         => 0.85, 'Kebri Dahar'=> 0.88,
            'Debre Birhan' => 0.35, 'Awash'          => 0.30,
        ];

        $city = $this->normalizeCity($destination);
        $prob = $risks[$city] ?? 0.65;

        if ($prob < 0.30) {
            $risk = 'Low';
            $rec  = 'High availability of backhaul cargo. Proceed with standard pricing.';
        } elseif ($prob < 0.55) {
            $risk = 'Medium';
            $rec  = 'Moderate risk of empty return. Consider pre-booking a backhaul or adding a 5–10% markup.';
        } else {
            $pct  = round($prob * 100);
            $risk = 'High';
            $rec  = "{$pct}% chance of returning empty. Factor the round-trip cost into your rate (add 15–25%) or arrange backhaul before departing.";
        }

        return [
            'destination'              => $destination,
            'empty_return_probability' => $prob,
            'risk_level'               => $risk,
            'recommendation'           => $rec,
            'source'                   => 'local_estimate',
        ];
    }
}
