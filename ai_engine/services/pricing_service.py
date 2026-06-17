import re
from ..schemas.pricing import PricingRequest, PricingResponse

# Road distances from Addis Ababa (km)
DIST_FROM_AA: dict[str, int] = {
    'adama':           99,
    'nazret':          99,
    'asella':         175,
    'awash':          225,
    'bahir dar':      565,
    'bishoftu':        50,
    'bale robe':      430,
    'debre birhan':   130,
    'debre markos':   300,
    'dessie':         401,
    'dilla':          367,
    'dire dawa':      520,
    'gambela':        769,
    'goba':           445,
    'gondar':         738,
    'harar':          526,
    'hawassa':        275,
    'jijiga':         630,
    'jimma':          346,
    'kebri dahar':    840,
    'mekele':         783,
    'moyale':         770,
    'nekemte':        331,
    'shashemene':     250,
    'shire':          900,
    'axum':          1020,
    'adigrat':        870,
    'sodo':           370,
    'wolaita sodo':   370,
    'woldia':         521,
    'assosa':         668,
    'arba minch':     505,
    'ziway':          163,
    'butajira':       170,
    'hosanna':        230,
}

RATE_MIN = 18.0   # ETB per km per ton
RATE_MAX = 28.0


def _normalize(raw: str) -> str:
    s = re.split(r'[,/]', raw.strip())[0].strip().lower()
    aliases = {
        'addis abeba': 'addis ababa',
        'nazret':      'adama',
        'wolaita':     'sodo',
        'endaselassie':'shire',
        'finfinne':    'addis ababa',
    }
    for needle, canonical in aliases.items():
        if needle in s:
            return canonical
    return s


def _material_mul(material: str) -> float:
    m = material.lower()
    if any(k in m for k in ['glass', 'electronic', 'ceramic', 'machinery part', 'medical']):
        return 1.25
    if any(k in m for k in ['vegetable', 'fruit', 'dairy', 'fish', 'meat', 'teff', 'grain', 'coffee']):
        return 1.15
    if any(k in m for k in ['cement', 'sand', 'gravel', 'scrap', 'construction', 'stone', 'aggregate']):
        return 0.9
    return 1.0


def _urgency_mul(urgency: str) -> float:
    return {'express': 1.4, 'high': 1.2, 'normal': 1.0, 'low': 0.85}.get(urgency.lower(), 1.0)


def _distance(from_city: str, to_city: str) -> int:
    nf = _normalize(from_city)
    nt = _normalize(to_city)
    aa = 'addis ababa'
    df = DIST_FROM_AA.get(nf)
    dt = DIST_FROM_AA.get(nt)

    if nf in (aa, ''):
        return dt or 400
    if nt in (aa, ''):
        return df or 400
    if df and dt:
        return round((df + dt) * 0.75)
    return df or dt or 400


def predict_price(request: PricingRequest) -> PricingResponse:
    # Accept both field name conventions
    pickup = request.pickup_location or getattr(request, 'from_', None) or ''
    dest   = request.destination   or request.to or ''

    dist_km  = _distance(pickup, dest)
    mat_mul  = _material_mul(request.material_type or '')
    urg_mul  = _urgency_mul(request.urgency_level  or 'normal')
    weight   = max(request.weight, 0.1)

    raw_min = dist_km * RATE_MIN * weight * mat_mul * urg_mul
    raw_max = dist_km * RATE_MAX * weight * mat_mul * urg_mul

    price_min = int(round(raw_min / 500) * 500)
    price_max = int(round(raw_max / 500) * 500)

    return PricingResponse(
        price_min=price_min,
        price_max=price_max,
        estimated_price=(price_min + price_max) // 2,
        distance_km=dist_km,
        currency='ETB',
        confidence=0.85,
        source='ai_engine',
    )
