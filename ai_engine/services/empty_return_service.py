from ..schemas.empty_return import EmptyReturnRequest, EmptyReturnResponse

# Heuristic probabilities for returning empty from certain destinations based on trade imbalances
# Remote areas have higher chances of returning empty.
DESTINATION_RISKS = {
    'addis ababa': 0.10, # Central hub, easy to find backhaul
    'adama': 0.15,
    'hawassa': 0.25,
    'bahir dar': 0.35,
    'dire dawa': 0.40,
    'gondar': 0.45,
    'mekele': 0.50,
    'debre markos': 0.55,
    'gorgora': 0.65,
    'metema': 0.85, # Border town, high chance of empty return unless pre-arranged
}

def predict_empty_return(request: EmptyReturnRequest) -> EmptyReturnResponse:
    dest = request.destination.strip().lower()
    
    # Default high risk if destination is unknown
    probability = DESTINATION_RISKS.get(dest, 0.70)
    
    if probability < 0.30:
        risk_level = "Low"
        recommendation = "High availability of backhaul cargo. Proceed with standard pricing."
    elif probability < 0.60:
        risk_level = "Medium"
        recommendation = "Moderate risk of empty return. Consider booking backhaul in advance or add slight price markup."
    else:
        risk_level = "High"
        recommendation = "High probability of empty return. Factor in the round-trip cost into your initial price."
        
    return EmptyReturnResponse(
        destination=request.destination,
        empty_return_probability=probability,
        risk_level=risk_level,
        recommendation=recommendation
    )
