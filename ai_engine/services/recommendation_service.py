from ..schemas.recommendation import TruckRecommendationRequest, TruckRecommendationResponse, TruckInfo

CITY_DISTANCE_MAP = {
    ('addis ababa', 'mekele'): 780,
    ('addis ababa', 'bahir dar'): 565,
    ('addis ababa', 'hawassa'): 275,
    ('addis ababa', 'gondar'): 735,
    ('addis ababa', 'jimma'): 345,
    ('mekele', 'bahir dar'): 865,
    ('mekele', 'jimma'): 1100,
    ('gondar', 'bahir dar'): 180,
    ('gondar', 'jimma'): 760,
    ('hawassa', 'jimma'): 310,
}

TRUCK_FLEET = [
    {
        'truck_id': 1,
        'driver_name': 'Abebe',
        'plate_number': 'ET1234A',
        'capacity': 40.0,
        'base_location': 'addis ababa',
    },
    {
        'truck_id': 2,
        'driver_name': 'Kebede',
        'plate_number': 'ET5678B',
        'capacity': 35.0,
        'base_location': 'bahir dar',
    },
    {
        'truck_id': 3,
        'driver_name': 'Selam',
        'plate_number': 'ET9012C',
        'capacity': 28.0,
        'base_location': 'hawassa',
    },
]

MATERIAL_PRIORITY = {
    'perishable': 1.2,
    'fragile': 1.15,
    'electronics': 1.1,
    'construction': 1.0,
    'general': 1.0,
}

URGENCY_MULTIPLIER = {
    'low': 0.9,
    'normal': 1.0,
    'high': 1.1,
    'express': 1.25,
}


def estimate_distance(pickup: str, destination: str) -> float:
    key = (pickup.lower(), destination.lower())
    reverse_key = (destination.lower(), pickup.lower())
    if key in CITY_DISTANCE_MAP:
        return CITY_DISTANCE_MAP[key]
    if reverse_key in CITY_DISTANCE_MAP:
        return CITY_DISTANCE_MAP[reverse_key]
    return 420.0


def score_truck(truck: dict, request: TruckRecommendationRequest, distance_km: float) -> dict:
    capacity_fit = min(truck['capacity'] / max(request.weight, 1), 1.0)
    urgency_score = URGENCY_MULTIPLIER.get(request.urgency_level.lower(), 1.0)
    material_bonus = MATERIAL_PRIORITY.get(request.material_type.lower(), 1.0)
    distance_score = 1.0 / (1.0 + distance_km / 500.0)
    score = (capacity_fit * 0.45) + (urgency_score * 0.2) + (distance_score * 0.2) + (material_bonus * 0.15)
    score = min(max(score / 1.2, 0.0), 1.0)

    estimated_price = round(distance_km * (request.weight / 10) * 120 * material_bonus * urgency_score)
    return {
        'truck_id': truck['truck_id'],
        'driver_name': truck['driver_name'],
        'plate_number': truck['plate_number'],
        'capacity': truck['capacity'],
        'distance_km': round(distance_km, 1),
        'estimated_price': estimated_price,
        'score': round(score, 2),
    }


def recommend_truck(request: TruckRecommendationRequest) -> TruckRecommendationResponse:
    distance_km = estimate_distance(request.pickup_location, request.destination)
    recommendations = [score_truck(truck, request, distance_km) for truck in TRUCK_FLEET]
    recommendations.sort(key=lambda item: item['score'], reverse=True)
    trucks = [TruckInfo(**recommendation) for recommendation in recommendations]
    return TruckRecommendationResponse(recommended_trucks=trucks)
