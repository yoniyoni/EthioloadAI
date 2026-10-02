<?php

namespace App\Services;

/**
 * Single canonical Ethiopian city name -> [lat, lng] table, replacing three
 * independent hardcoded tables that disagreed with each other on coordinate
 * values for the same cities (VehicleController::CITY_COORDS, was treated as
 * authoritative since it's tied to the app's actual city-picker dropdown;
 * BackhaulService::$CITIES and AiController::$cityCoords had slightly
 * different values for the same cities and are now retired in favor of this).
 *
 * COORDS = VehicleController::CITY_COORDS's original 44 cities, plus 7 more
 * cities that only existed in CargoResource::cityCoords()'s "$extra" list
 * under those exact key spellings (its 8th extra city, "Adwa", was already
 * present in VehicleController::CITY_COORDS under the same name/value and is
 * not duplicated here), plus 4 more cities (Awash, Ziway, Wukro, Dangila)
 * that only existed in AiController's original private $cityCoords table.
 */
class CityCoordinates
{
    public const COORDS = [
        // ── Original VehicleController::CITY_COORDS (44 cities) ──
        'Addis Ababa'    => [9.0320,  38.7469],
        'Gondar'         => [12.6030, 37.4521],
        'Bahir Dar'      => [11.5931, 37.3911],
        'Mekele'         => [13.4967, 39.4697],
        'Hawassa'        => [7.0504,  38.4955],
        'Dire Dawa'      => [9.5931,  41.8661],
        'Jimma'          => [7.6731,  36.8346],
        'Adama'          => [8.5400,  39.2700],
        'Dessie'         => [11.1333, 39.6333],
        'Debre Birhan'   => [9.6833,  39.5333],
        'Debre Markos'   => [10.3333, 37.7167],
        'Debre Tabor'    => [11.8500, 38.0167],
        'Humera'         => [14.3000, 36.6000],
        'Metema'         => [12.8500, 36.2000],
        'Shire'          => [14.1000, 38.2833],
        'Axum'           => [14.1200, 38.7200],
        'Adwa'           => [14.1667, 38.9000],
        'Adigrat'        => [14.2667, 39.4500],
        'Lalibela'       => [12.0333, 39.0500],
        'Woldia'         => [11.8333, 39.6000],
        'Kombolcha'      => [11.0833, 39.7333],
        'Addis Zemen'    => [12.1333, 37.7833],
        'Woreta'         => [11.9167, 37.7000],
        'Injibara'       => [10.9833, 36.9833],
        'Motta'          => [11.0833, 37.8667],
        'Bure'           => [10.7000, 37.0667],
        'Finote Selam'   => [10.7000, 37.2667],
        'Arba Minch'     => [6.0333,  37.5500],
        'Assosa'         => [10.0667, 34.5333],
        'Gambela'        => [8.2500,  34.5833],
        'Hosaena'        => [7.5500,  37.8500],
        'Shashamane'     => [7.2000,  38.5833],
        'Dilla'          => [6.4167,  38.3167],
        'Yirgalem'       => [6.7500,  38.4000],
        'Asela'          => [7.9500,  39.1333],
        'Robe'           => [7.1167,  40.0000],
        'Goba'           => [7.0000,  39.9833],
        'Jijiga'         => [9.3500,  42.8000],
        'Harar'          => [9.3167,  42.1333],
        'Nekemte'        => [9.0833,  36.5500],
        'Gimbi'          => [9.1667,  35.8167],
        'Ambo'           => [8.9833,  37.8500],
        'Welkite'        => [8.2833,  37.7833],
        'Butajira'       => [8.1167,  38.3667],

        // ── Extra cities from CargoResource::cityCoords() not already above ──
        'Shashemene'     => [7.2000,  38.5833],
        'Asella'         => [7.9500,  39.1333],
        'Bale Robe'      => [7.1167,  40.0000],
        'Bishoftu'       => [8.7500,  38.9833],
        'Moyale'         => [3.5333,  39.0500],
        'Kebri Dahar'    => [6.7500,  44.2833],
        'Sodo'           => [6.8500,  37.7500],

        // ── Extra cities from AiController::$cityCoords not already above ──
        // (found missing by adversarial verification of this consolidation —
        // AiController::roadDistKm()'s tier-2 estimate silently fell through
        // to a much less accurate tier-3/default for these cities without
        // this addition.)
        'Awash'          => [8.9833,  40.1500],
        'Ziway'          => [7.9333,  38.7167],
        'Wukro'          => [13.7833, 39.6000],
        'Dangila'        => [11.2667, 36.8333],
    ];

    /** Lowercase alias/variant -> canonical key in COORDS. Merged from
     *  BackhaulService::$CITIES's name-variant lists and
     *  AiController::normalizeCity()'s alias list.
     *
     *  Note: three BackhaulService entries (maksegnit, holeta, sebeta) had no
     *  corresponding city in VehicleController::CITY_COORDS or
     *  CargoResource::cityCoords() and so have no canonical target here —
     *  they are intentionally omitted rather than invented.
     */
    private const ALIASES = [
        // Addis Ababa
        'addis ababa'          => 'Addis Ababa',
        'addis abeba'          => 'Addis Ababa',
        'addis'                => 'Addis Ababa',
        // Mekele
        'mekele'               => 'Mekele',
        'mekelle'              => 'Mekele',
        // Gondar
        'gondar'               => 'Gondar',
        'gonder'               => 'Gondar',
        // Bahir Dar
        'bahir dar'            => 'Bahir Dar',
        'bahar dar'            => 'Bahir Dar',
        'bahirdar'             => 'Bahir Dar',
        // Dire Dawa
        'dire dawa'            => 'Dire Dawa',
        'diredawa'             => 'Dire Dawa',
        // Hawassa
        'hawassa'              => 'Hawassa',
        'awasa'                => 'Hawassa',
        // Jimma
        'jimma'                => 'Jimma',
        'jima'                 => 'Jimma',
        // Metema
        'metema'               => 'Metema',
        // Humera
        'humera'               => 'Humera',
        'humer'                => 'Humera',
        // Shire
        'shire endaselassie'   => 'Shire',
        'shire indasilase'     => 'Shire',
        'shire'                => 'Shire',
        'endaselassie'         => 'Shire',
        // Adigrat
        'adigrat'              => 'Adigrat',
        // Axum
        'axum'                 => 'Axum',
        'aksum'                => 'Axum',
        // Adwa
        'adwa'                 => 'Adwa',
        // Addis Zemen
        'addis zemen'          => 'Addis Zemen',
        // Woreta
        'woreta'               => 'Woreta',
        // Debre Tabor
        'debre tabor'          => 'Debre Tabor',
        'debretabor'           => 'Debre Tabor',
        // Motta
        'motta'                => 'Motta',
        // Injibara
        'injibara'             => 'Injibara',
        // Debre Markos
        'debre markos'         => 'Debre Markos',
        'debremarkos'          => 'Debre Markos',
        // Dessie
        'dessie'               => 'Dessie',
        'desse'                => 'Dessie',
        // Woldia
        'woldia'               => 'Woldia',
        'woldiya'              => 'Woldia',
        // Debre Birhan
        'debre birhan'         => 'Debre Birhan',
        'debrebirhan'          => 'Debre Birhan',
        // Adama
        'adama / nazret'       => 'Adama',
        'adama'                => 'Adama',
        'nazret'               => 'Adama',
        // Bishoftu
        'bishoftu'             => 'Bishoftu',
        'debre zeyt'           => 'Bishoftu',
        'debre zeit'           => 'Bishoftu',
        'debrezeit'            => 'Bishoftu',
        // Harar
        'harar'                => 'Harar',
        'harer'                => 'Harar',
        // Jijiga
        'jijiga'               => 'Jijiga',
        'jigjiga'              => 'Jijiga',
        // Kebri Dahar
        'kebri dahar'          => 'Kebri Dahar',
        'kebridahar'           => 'Kebri Dahar',
        // Shashemene / Shashamane (two distinct COORDS entries, same values)
        'shashemene'           => 'Shashemene',
        'shashamane'           => 'Shashamane',
        // Dilla
        'dilla'                => 'Dilla',
        // Sodo
        'wolaita sodo'         => 'Sodo',
        'wolayta sodo'         => 'Sodo',
        'sodo / wolaita'       => 'Sodo',
        'sodo'                 => 'Sodo',
        'wolaita'              => 'Sodo',
        // Arba Minch
        'arba minch'           => 'Arba Minch',
        'arbaminch'            => 'Arba Minch',
        // Moyale
        'moyale'               => 'Moyale',
        // Nekemte
        'nekemte'              => 'Nekemte',
        'naqamte'              => 'Nekemte',
        // Assosa
        'assosa'               => 'Assosa',
        'asosa'                => 'Assosa',
        // Gambela
        'gambela'              => 'Gambela',
        'gambella'             => 'Gambela',
        // Bale Robe / Robe (two distinct COORDS entries, same values)
        'bale robe'            => 'Bale Robe',
        'balerobe'             => 'Bale Robe',
        'robe'                 => 'Robe',
        // Goba
        'goba'                 => 'Goba',
        // Hosaena — AiController::normalizeCity()'s ucwords() fallback
        // produces "Hosanna" for unrecognized input, which never matched
        // the canonical "Hosaena" spelling; alias it explicitly.
        'hosanna'              => 'Hosaena',
        'hosaena'              => 'Hosaena',
        // Awash
        'awash'                => 'Awash',
        // Ziway
        'ziway'                => 'Ziway',
        'zeway'                => 'Ziway',
        // Wukro
        'wukro'                => 'Wukro',
        // Dangila
        'dangila'              => 'Dangila',
    ];

    public static function get(string $cityName): ?array
    {
        return self::COORDS[$cityName] ?? null;
    }

    /** Resolves a free-text city name (possibly an alias/variant, possibly
     *  with a region suffix like "Adama, Oromia") to canonical coordinates. */
    public static function resolveFromText(string $text): ?array
    {
        $normalized = strtolower(trim(preg_split('/[,\/]/', trim($text))[0]));
        if (isset(self::ALIASES[$normalized])) {
            return self::COORDS[self::ALIASES[$normalized]] ?? null;
        }
        foreach (self::COORDS as $city => $coords) {
            if (stripos($text, $city) !== false) {
                return $coords;
            }
        }
        foreach (self::ALIASES as $alias => $canonical) {
            if (str_contains($normalized, $alias)) {
                return self::COORDS[$canonical] ?? null;
            }
        }
        return null;
    }

    /** Nearest known city name to a given point (for reverse "what city am I in" lookups). */
    public static function nearestCity(float $lat, float $lng): string
    {
        $best = null;
        $bestDist = PHP_FLOAT_MAX;
        foreach (self::COORDS as $city => [$clat, $clng]) {
            $dist = DistanceService::haversineKm($lat, $lng, $clat, $clng);
            if ($dist < $bestDist) {
                $bestDist = $dist;
                $best = $city;
            }
        }
        return $best ?? 'Addis Ababa';
    }
}
