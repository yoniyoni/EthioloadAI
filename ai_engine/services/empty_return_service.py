import re
from ..schemas.empty_return import EmptyReturnRequest, EmptyReturnResponse

# Probability of returning empty (0 = always find backhaul, 1 = almost always empty)
# Based on trade flow imbalance in the Ethiopian freight market
DESTINATION_RISKS: dict[str, float] = {
    'addis ababa':    0.10,   # central hub — easy to find backhaul
    'adama':          0.15,
    'bishoftu':       0.18,
    'shashemene':     0.22,
    'hawassa':        0.25,
    'jimma':          0.30,
    'dire dawa':      0.35,
    'bahir dar':      0.38,
    'gondar':         0.42,
    'mekele':         0.50,
    'dessie':         0.45,
    'woldia':         0.48,
    'nekemte':        0.50,
    'debre markos':   0.55,
    'asella':         0.50,
    'bale robe':      0.58,
    'dilla':          0.55,
    'sodo':           0.55,
    'arba minch':     0.60,
    'harar':          0.40,
    'jijiga':         0.65,
    'gambela':        0.75,
    'assosa':         0.72,
    'moyale':         0.80,
    'metema':         0.85,
    'kebri dahar':    0.88,
    'axum':           0.65,
    'adigrat':        0.60,
    'shire':          0.68,
    'mekele':         0.50,
    'debre birhan':   0.35,
    'awash':          0.30,
    'ziway':          0.28,
    'butajira':       0.45,
    'hosanna':        0.50,
    'goba':           0.65,
}


def _normalize(raw: str) -> str:
    """Strip region suffix and normalize to lowercase for lookup."""
    s = re.split(r'[,/]', raw.strip())[0].strip().lower()
    aliases = {
        'addis abeba':  'addis ababa',
        'finfinne':     'addis ababa',
        'nazret':       'adama',
        'wolaita':      'sodo',
        'wolaita sodo': 'sodo',
        'endaselassie': 'shire',
        'debre zeit':   'bishoftu',
        'woldiya':      'woldia',
    }
    for needle, canonical in aliases.items():
        if needle in s:
            return canonical
    return s


def predict_empty_return(request: EmptyReturnRequest) -> EmptyReturnResponse:
    dest = _normalize(request.destination)
    probability = DESTINATION_RISKS.get(dest, 0.65)

    if probability < 0.30:
        risk_level = 'Low'
        recommendation = (
            'High availability of backhaul cargo from this destination. '
            'Proceed with standard pricing — you are unlikely to return empty.'
        )
    elif probability < 0.55:
        risk_level = 'Medium'
        recommendation = (
            'Moderate risk of empty return. Consider pre-booking a backhaul load '
            'or adding a small markup (5–10%) to cover potential empty-return cost.'
        )
    else:
        risk_level = 'High'
        pct = round(probability * 100)
        recommendation = (
            f'{pct}% chance of returning empty from this destination. '
            'Factor the round-trip cost into your rate — typically add 15–25% '
            'to cover the empty leg, or arrange a backhaul before departing.'
        )

    return EmptyReturnResponse(
        destination=request.destination,
        empty_return_probability=probability,
        risk_level=risk_level,
        recommendation=recommendation,
    )
