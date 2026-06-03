from ..schemas.backhaul import BackhaulRequest, BackhaulResponse, BackhaulOpportunity

ROUTE_POSITIONS = {
    'addis ababa': 0,
    'adama': 90,
    'mekele': 780,
    'gondar': 735,
    'bahir dar': 565,
    'hawassa': 275,
    'jimma': 345,
    'dire dawa': 320,
}

BACKHAUL_CARGO = [
    {
        'cargo_id': 201,
        'pickup_location': 'Hawassa',
        'destination': 'Addis Ababa',
        'weight': 18.0,
        'price': 11800,
    },
    {
        'cargo_id': 202,
        'pickup_location': 'Bahir Dar',
        'destination': 'Addis Ababa',
        'weight': 22.0,
        'price': 13000,
    },
    {
        'cargo_id': 203,
        'pickup_location': 'Dire Dawa',
        'destination': 'Addis Ababa',
        'weight': 16.0,
        'price': 10700,
    },
    {
        'cargo_id': 204,
        'pickup_location': 'Jimma',
        'destination': 'Addis Ababa',
        'weight': 24.0,
        'price': 13500,
    },
    {
        'cargo_id': 205,
        'pickup_location': 'Gondar',
        'destination': 'Bahir Dar',
        'weight': 14.0,
        'price': 9900,
    },
]


def normalize_city(name: str) -> str:
    return name.strip().lower()


def position_for(city: str) -> float:
    return ROUTE_POSITIONS.get(normalize_city(city), 400.0)


def score_backhaul(cargo: dict, request: BackhaulRequest) -> float:
    current_pos = position_for(request.current_location)
    destination_pos = position_for(request.destination)
    cargo_pickup = position_for(cargo['pickup_location'])
    cargo_destination = position_for(cargo['destination'])

    direction_match = 1.0 if (current_pos <= cargo_pickup <= destination_pos) or (destination_pos <= cargo_pickup <= current_pos) else 0.6
    destination_match = 1.0 if normalize_city(request.destination) == normalize_city(cargo['destination']) else 0.8
    weight_penalty = max(0.0, 1.0 - ((cargo['weight'] - 20.0) / 40.0)) if cargo['weight'] > 20 else 1.0
    proximity_score = 1.0 / (1.0 + abs(cargo_pickup - current_pos) / 200.0)

    return round(0.35 * direction_match + 0.3 * destination_match + 0.2 * proximity_score + 0.15 * weight_penalty, 2)


def find_backhaul_opportunities(request: BackhaulRequest) -> BackhaulResponse:
    opportunities = []
    cargo_list = request.available_cargo if request.available_cargo else BACKHAUL_CARGO
    for cargo in cargo_list:
        score = score_backhaul(cargo, request)
        opportunities.append(
            BackhaulOpportunity(
                cargo_id=cargo['cargo_id'],
                pickup_location=cargo['pickup_location'],
                destination=cargo['destination'],
                weight=cargo['weight'],
                price=cargo['price'],
                score=score,
            )
        )

    opportunities.sort(key=lambda item: item.score, reverse=True)
    return BackhaulResponse(opportunities=opportunities[:5])
