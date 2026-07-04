from ..schemas.pricing import PricingRequest, PricingResponse
from ._geo import road_distance_km

RATE_MIN = 18.0   # ETB per km per ton
RATE_MAX = 28.0


def _material_mul(material: str) -> float:
    m = material.lower()
    if any(k in m for k in ['glass', 'electronic', 'ceramic', 'machinery', 'medical']):
        return 1.25
    if any(k in m for k in ['vegetable', 'fruit', 'dairy', 'fish', 'meat',
                              'teff', 'grain', 'coffee', 'spice']):
        return 1.15
    if any(k in m for k in ['cement', 'sand', 'gravel', 'scrap', 'construction',
                              'stone', 'aggregate']):
        return 0.9
    return 1.0


def _urgency_mul(urgency: str) -> float:
    return {'express': 1.4, 'high': 1.2, 'normal': 1.0, 'low': 0.85}.get(
        urgency.lower(), 1.0
    )


def predict_price(request: PricingRequest) -> PricingResponse:
    pickup = request.pickup_location or getattr(request, 'from_', None) or ''
    dest   = request.destination   or request.to or ''

    dist_km  = road_distance_km(pickup, dest)
    mat_mul  = _material_mul(request.material_type or '')
    urg_mul  = _urgency_mul(request.urgency_level  or 'normal')
    weight   = max(request.weight, 0.1)

    rate_min = request.rate_min if request.rate_min is not None else RATE_MIN
    rate_max = request.rate_max if request.rate_max is not None else RATE_MAX

    raw_min = dist_km * rate_min * weight * mat_mul * urg_mul
    raw_max = dist_km * rate_max * weight * mat_mul * urg_mul

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
