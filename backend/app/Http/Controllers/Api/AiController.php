<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformSetting;
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

        // Inject real available vehicles + driver names from DB
        $payload['truck_fleet'] = \App\Models\Vehicle::with('user')
            ->where('availability_status', 'available')
            ->get()
            ->map(fn ($v) => [
                'truck_id'      => $v->id,
                'driver_name'   => $v->user?->full_name ?? ('Driver #' . $v->user_id),
                'plate_number'  => $v->plate_number,
                'capacity'      => (float) ($v->capacity ?? 20),
                'base_location' => strtolower($v->current_city ?? 'addis ababa'),
            ])
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

            // Use OSRM real road distance when coordinates are supplied
            $osrmDistKm = null;
            $fromLat = $request->input('from_lat');
            $fromLng = $request->input('from_lng');
            $toLat   = $request->input('to_lat');
            $toLng   = $request->input('to_lng');
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

    // ── GPS coordinates (lat, lng) for Haversine city-to-city distances ──
    private static array $cityCoords = [
        'Addis Ababa'          => [9.0320,  38.7469],
        'Adama'                => [8.5400,  39.2700],
        'Adama / Nazret'       => [8.5400,  39.2700],
        'Asella'               => [8.0000,  39.1333],
        'Awash'                => [8.9833,  40.1500],
        'Bahir Dar'            => [11.5942, 37.3892],
        'Bishoftu'             => [8.7500,  38.9833],
        'Bale Robe'            => [7.1167,  40.0167],
        'Debre Birhan'         => [9.6833,  39.5167],
        'Debre Markos'         => [10.3500, 37.7333],
        'Dessie'               => [11.1333, 39.6333],
        'Kombolcha'            => [11.0833, 39.7333],
        'Dilla'                => [6.4167,  38.3333],
        'Dire Dawa'            => [9.5931,  41.8571],
        'Gambela'              => [8.2500,  34.5833],
        'Goba'                 => [7.0000,  39.9667],
        'Gondar'               => [12.6030, 37.4670],
        'Harar'                => [9.3125,  42.1181],
        'Hawassa'              => [7.0504,  38.4955],
        'Humera'               => [14.2730, 36.5820],
        'Jijiga'               => [9.3500,  42.8000],
        'Jimma'                => [7.6710,  36.8342],
        'Kebri Dahar'          => [6.7333,  44.2833],
        'Mekele'               => [13.4967, 39.4764],
        'Moyale'               => [3.5333,  39.0500],
        'Nekemte'              => [9.0833,  36.5500],
        'Shashemene'           => [7.2033,  38.5931],
        'Shire / Endaselassie' => [14.1002, 37.0668],
        'Axum'                 => [14.1267, 38.7289],
        'Adigrat'              => [14.2750, 39.4667],
        'Sodo / Wolaita'       => [6.8500,  37.7500],
        'Sodo'                 => [6.8500,  37.7500],
        'Woldia'               => [11.8167, 39.6000],
        'Assosa'               => [10.0667, 34.5333],
        'Arba Minch'           => [6.0333,  37.5500],
        'Ziway'                => [7.9333,  38.7167],
        'Butajira'             => [8.1333,  38.3667],
        'Hosanna'              => [7.5500,  37.8500],
        'Lalibela'             => [12.0317, 39.0472],
        'Debre Tabor'          => [11.8500, 38.0167],
        'Wukro'                => [13.7833, 39.6000],
        'Dangila'              => [11.2667, 36.8333],
    ];

    private static function haversineKm(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $R    = 6371.0;
        $phi1 = deg2rad($lat1);
        $phi2 = deg2rad($lat2);
        $dphi = deg2rad($lat2 - $lat1);
        $dlam = deg2rad($lon2 - $lon1);
        $a    = sin($dphi / 2) ** 2 + cos($phi1) * cos($phi2) * sin($dlam / 2) ** 2;
        return 2 * $R * asin(sqrt($a));
    }

    private static function roadDistKm(string $nFrom, string $nTo): int
    {
        $aa = 'Addis Ababa';
        if ($nFrom === $aa && $nTo === $aa) return 0;
        if ($nFrom === $aa || $nFrom === '') return self::$distFromAA[$nTo]  ?? 400;
        if ($nTo   === $aa || $nTo   === '') return self::$distFromAA[$nFrom] ?? 400;

        $cf = self::$cityCoords[$nFrom] ?? null;
        $ct = self::$cityCoords[$nTo]   ?? null;
        if ($cf && $ct) {
            $straight = self::haversineKm($cf[0], $cf[1], $ct[0], $ct[1]);
            return max(10, (int) round($straight * 1.6));
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
        // Known aliases
        $aliases = [
            'addis'   => 'Addis Ababa',
            'nazret'  => 'Adama / Nazret',
            'adama'   => 'Adama',
            'wolaita' => 'Sodo / Wolaita',
            'endaselassie' => 'Shire / Endaselassie',
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
        // Prefer real OSRM road distance over the haversine×1.6 estimate
        $distKm = $osrmDistKm ?? self::roadDistKm($nFrom, $nTo);

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
            'confidence'      => $osrmDistKm !== null ? 0.88 : 0.80,
            'source'          => $osrmDistKm !== null ? 'local_estimate_osrm' : 'local_estimate',
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

    private function localTruckRecommend(string $pickup, string $dest, float $weight, string $material, string $urgency, array $fleet): array
    {
        $routeKm = $this->distBetween($pickup, $dest);

        $urgencyMul  = match ($urgency) { 'express' => 1.25, 'high' => 1.1, 'low' => 0.9, default => 1.0 };
        $materialMul = $this->materialMultiplier($material);

        $scored = array_map(function ($truck) use ($pickup, $weight, $routeKm, $urgencyMul, $materialMul) {
            $proximityKm  = $this->distBetween($truck['base_location'] ?? 'addis ababa', $pickup);
            $capacityFit  = min(($truck['capacity'] ?? 20) / max($weight, 1), 1.0);
            $proxScore    = 1.0 / (1.0 + $proximityKm / 200.0);
            $score        = round($capacityFit * 0.40 + $urgencyMul * 0.15 + $proxScore * 0.30 + $materialMul * 0.15, 2);
            $estPrice     = (int) round($routeKm * $weight * 23 * $materialMul * $urgencyMul);

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

        usort($scored, fn ($a, $b) => $b['score'] <=> $a['score']);

        return ['recommended_trucks' => $scored];
    }

    private function distBetween(string $cityA, string $cityB): int
    {
        return self::roadDistKm(
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
