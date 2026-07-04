"""
Shared geographic utilities for Ethiopian city distance calculations.

Road distances from Addis Ababa are sourced from real route data.
City-to-city distances (not involving Addis) use the Haversine formula
on verified GPS coordinates + a road factor of 1.6 (Ethiopian highland
roads average ~60% longer than straight-line due to terrain).
"""
import math
import re

# ---------------------------------------------------------------------------
# Road distances from Addis Ababa (km) — actual road measurements
# ---------------------------------------------------------------------------
DIST_FROM_AA: dict[str, int] = {
    'adama':          99,
    'nazret':         99,
    'asella':        175,
    'awash':         225,
    'bahir dar':     565,
    'bishoftu':       50,
    'debre zeit':     50,
    'bale robe':     430,
    'debre birhan':  130,
    'debre markos':  300,
    'dessie':        401,
    'kombolcha':     395,
    'dilla':         367,
    'dire dawa':     515,
    'gambela':       769,
    'goba':          445,
    'gondar':        738,
    'harar':         526,
    'hawassa':       275,
    'humera':        950,
    'jijiga':        633,
    'jimma':         346,
    'kebri dahar':   840,
    'mekele':        783,
    'moyale':        770,
    'nekemte':       331,
    'shashemene':    250,
    'shire':         900,
    'axum':          1020,
    'adigrat':       870,
    'sodo':          370,
    'wolaita sodo':  370,
    'woldia':        521,
    'assosa':        668,
    'arba minch':    505,
    'ziway':         163,
    'butajira':      170,
    'hosanna':       230,
    'lalibela':      696,
    'debre tabor':   667,
    'wukro':         800,
    'dangila':       580,
}

# ---------------------------------------------------------------------------
# Verified GPS coordinates for Ethiopian cities
# ---------------------------------------------------------------------------
CITY_COORDS: dict[str, tuple[float, float]] = {
    'addis ababa':  ( 9.0320,  38.7469),
    'adama':        ( 8.5400,  39.2700),
    'asella':       ( 8.0000,  39.1333),
    'awash':        ( 8.9833,  40.1500),
    'bahir dar':    (11.5942,  37.3892),
    'bishoftu':     ( 8.7500,  38.9833),
    'debre zeit':   ( 8.7500,  38.9833),
    'bale robe':    ( 7.1167,  40.0167),
    'debre birhan': ( 9.6833,  39.5167),
    'debre markos': (10.3500,  37.7333),
    'dessie':       (11.1333,  39.6333),
    'kombolcha':    (11.0833,  39.7333),
    'dilla':        ( 6.4167,  38.3333),
    'dire dawa':    ( 9.5931,  41.8571),
    'gambela':      ( 8.2500,  34.5833),
    'goba':         ( 7.0000,  39.9667),
    'gondar':       (12.6030,  37.4670),
    'harar':        ( 9.3125,  42.1181),
    'hawassa':      ( 7.0504,  38.4955),
    'humera':       (14.2730,  36.5820),
    'jijiga':       ( 9.3500,  42.8000),
    'jimma':        ( 7.6710,  36.8342),
    'kebri dahar':  ( 6.7333,  44.2833),
    'mekele':       (13.4967,  39.4764),
    'moyale':       ( 3.5333,  39.0500),
    'nekemte':      ( 9.0833,  36.5500),
    'shashemene':   ( 7.2033,  38.5931),
    'shire':        (14.1002,  37.0668),
    'axum':         (14.1267,  38.7289),
    'adigrat':      (14.2750,  39.4667),
    'sodo':         ( 6.8500,  37.7500),
    'wolaita sodo': ( 6.8500,  37.7500),
    'woldia':       (11.8167,  39.6000),
    'assosa':       (10.0667,  34.5333),
    'arba minch':   ( 6.0333,  37.5500),
    'ziway':        ( 7.9333,  38.7167),
    'butajira':     ( 8.1333,  38.3667),
    'hosanna':      ( 7.5500,  37.8500),
    'lalibela':     (12.0317,  39.0472),
    'debre tabor':  (11.8500,  38.0167),
    'wukro':        (13.7833,  39.6000),
    'dangila':      (11.2667,  36.8333),
}

# Average road-to-straight-line ratio for Ethiopian inter-city routes.
# Calibrated against known Addis distances: northern highland routes ~1.6-1.8,
# southern rift-valley routes ~1.2-1.3. 1.6 is the best single global value.
_ROAD_FACTOR = 1.6

# ---------------------------------------------------------------------------
# Text normalisation
# ---------------------------------------------------------------------------
_ALIASES: dict[str, str] = {
    'addis abeba':   'addis ababa',
    'finfinne':      'addis ababa',
    'nazret':        'adama',
    'nazareth':      'adama',
    'wolaita':       'wolaita sodo',
    'endaselassie':  'shire',
    'debre zeit':    'bishoftu',
    'dire dewa':     'dire dawa',
    'harar':         'harar',
}


def normalize(raw: str) -> str:
    """Lower-case, strip, take first token before comma/slash, apply aliases."""
    s = re.split(r'[,/]', raw.strip())[0].strip().lower()
    for needle, canonical in _ALIASES.items():
        if needle in s:
            return canonical
    return s


# ---------------------------------------------------------------------------
# Distance calculation
# ---------------------------------------------------------------------------

def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def road_distance_km(from_city: str, to_city: str) -> int:
    """
    Return estimated road distance in km between two Ethiopian cities.

    Priority:
      1. If either endpoint is Addis Ababa → use DIST_FROM_AA (accurate).
      2. Both endpoints have GPS coords → Haversine × road factor (accurate).
      3. Both endpoints are in DIST_FROM_AA but no coords → AA-based triangle
         (fallback; should not happen given current coord coverage).
      4. Completely unknown cities → 400 km default.
    """
    nf = normalize(from_city)
    nt = normalize(to_city)
    aa = 'addis ababa'

    if nf == aa and nt == aa:
        return 0
    if nf == aa:
        return DIST_FROM_AA.get(nt) or 400
    if nt == aa:
        return DIST_FROM_AA.get(nf) or 400

    # Both have coordinates → Haversine + road factor
    cf = CITY_COORDS.get(nf)
    ct = CITY_COORDS.get(nt)
    if cf and ct:
        straight = _haversine_km(cf[0], cf[1], ct[0], ct[1])
        return max(10, round(straight * _ROAD_FACTOR))

    # Fallback: triangle via AA (much better than wrong heuristic)
    df = DIST_FROM_AA.get(nf)
    dt = DIST_FROM_AA.get(nt)
    if df and dt:
        # |df - dt| is a lower bound; add half of the smaller as rough correction
        return round(abs(df - dt) + min(df, dt) * 0.3)
    return df or dt or 400
