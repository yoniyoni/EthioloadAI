import random
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

# Cold-start-safe defaults for a driver with no rating/bid history yet —
# neutral-good, not zero, so a new driver isn't punished for lacking a track
# record. Mirrors AiController::localTruckRecommend()'s PHP fallback so both
# engines rank the same way regardless of which one actually answers a call.
NEUTRAL_RATING = 4.0
NEUTRAL_ACCEPTANCE_RATE = 0.7


def _score_truck(truck: dict, req: TruckRecommendationRequest,
                 route_km: float, proximity_km: float) -> dict:
    capacity_fit    = min(truck['capacity'] / max(req.weight, 1), 1.0)
    urgency_bonus   = URGENCY_MULTIPLIER.get(req.urgency_level.lower(), 1.0)
    material_bonus  = MATERIAL_PRIORITY.get(req.material_type.lower(), 1.0)
    proximity_score = 1.0 / (1.0 + proximity_km / 200.0)
    rating_score    = (truck.get('avg_rating') or NEUTRAL_RATING) / 5.0
    accept_score    = truck.get('acceptance_rate')
    if accept_score is None:
        accept_score = NEUTRAL_ACCEPTANCE_RATE

    # Real driver-performance signal (rating, bid-acceptance rate) alongside
    # trip fit — not just static trip attributes. Previously capacity_fit
    # capped at 1.0 for any "big enough" truck and proximity ended up the
    # only real differentiator, which meant Addis-based trucks (where most
    # cargo originates) won almost every call regardless of driver quality.
    score = (
        (capacity_fit * 0.20)
        + (urgency_bonus * 0.10)
        + (proximity_score * 0.20)
        + (material_bonus * 0.10)
        + (rating_score * 0.20)
        + (accept_score * 0.20)
    )
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


def _shuffle_tied_scores(scored: list[dict]) -> list[dict]:
    """Sorts descending by score, but randomizes order WITHIN each group of
    equal scores instead of always resolving ties to the same list-order
    winner. A higher-scored truck never moves below a lower-scored one —
    two genuinely-tied trucks just take turns being #1 across calls instead
    of one fixed truck always winning by accident of fleet-list order."""
    groups: dict[float, list[dict]] = {}
    for item in scored:
        groups.setdefault(item['score'], []).append(item)

    result: list[dict] = []
    for s in sorted(groups.keys(), reverse=True):
        group = groups[s]
        random.shuffle(group)
        result.extend(group)
    return result


def recommend_truck(request: TruckRecommendationRequest) -> TruckRecommendationResponse:
    fleet = request.truck_fleet if (request.truck_fleet and len(request.truck_fleet) > 0) else _DEMO_FLEET

    def base(t: dict) -> str:
        return t.get('base_location') or t.get('current_city') or 'addis ababa'

    route_km = road_distance_km(request.pickup_location, request.destination)

    scored = [_score_truck(t, request, route_km, road_distance_km(base(t), request.pickup_location)) for t in fleet]
    ranked = _shuffle_tied_scores(scored)

    return TruckRecommendationResponse(
        recommended_trucks=[TruckInfo(**r) for r in ranked]
    )
