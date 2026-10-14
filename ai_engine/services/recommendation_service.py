import re
from ..schemas.recommendation import TruckRecommendationRequest, TruckRecommendationResponse, TruckInfo
from ._geo import road_distance_km

# Fallback demo fleet used only when no DB trucks are injected
_DEMO_FLEET = [
    {'truck_id': 1, 'driver_name': 'Abebe Girma',   'plate_number': 'ET-1234-A', 'capacity': 40.0, 'base_location': 'addis ababa'},
    {'truck_id': 2, 'driver_name': 'Kebede Tadesse', 'plate_number': 'ET-5678-B', 'capacity': 35.0, 'base_location': 'bahir dar'},
    {'truck_id': 3, 'driver_name': 'Selam Bekele',   'plate_number': 'ET-9012-C', 'capacity': 28.0, 'base_location': 'hawassa'},
]

MATERIAL_PRIORITY = {
    'perishable': 1.2, 'fragile': 1.15, 'electronics': 1.1,
    'construction': 1.0, 'general': 1.0,
}
URGENCY_MULTIPLIER = {
    'low': 0.9, 'normal': 1.0, 'high': 1.1, 'express': 1.25,
}


def _score_truck(truck: dict, req: TruckRecommendationRequest,
                 route_km: float, proximity_km: float) -> dict:
    capacity_fit    = min(truck['capacity'] / max(req.weight, 1), 1.0)
    urgency_bonus   = URGENCY_MULTIPLIER.get(req.urgency_level.lower(), 1.0)
    material_bonus  = MATERIAL_PRIORITY.get(req.material_type.lower(), 1.0)
    proximity_score = 1.0 / (1.0 + proximity_km / 200.0)

    score = (capacity_fit * 0.40) + (urgency_bonus * 0.15) + (proximity_score * 0.30) + (material_bonus * 0.15)
    score = round(min(max(score, 0.0), 1.0), 2)

    estimated_price = int(round(route_km * req.weight * 23 * material_bonus * urgency_bonus))

    return {
        'truck_id':        truck['truck_id'],
        'driver_name':     truck['driver_name'],
        'plate_number':    truck['plate_number'],
        'capacity':        truck['capacity'],
        'distance_km':     round(proximity_km, 1),
        'estimated_price': estimated_price,
        'score':           score,
    }


def recommend_truck(request: TruckRecommendationRequest) -> TruckRecommendationResponse:
    fleet = request.truck_fleet if (request.truck_fleet and len(request.truck_fleet) > 0) else _DEMO_FLEET

    def base(t: dict) -> str:
        return t.get('base_location') or t.get('current_city') or 'addis ababa'

    route_km = road_distance_km(request.pickup_location, request.destination)

    ranked = sorted(
        [_score_truck(t, request, route_km, road_distance_km(base(t), request.pickup_location)) for t in fleet],
        key=lambda x: x['score'],
        reverse=True,
    )

    return TruckRecommendationResponse(
        recommended_trucks=[TruckInfo(**r) for r in ranked]
    )
