from pydantic import BaseModel
from typing import List, Optional, Any

class TruckRecommendationRequest(BaseModel):
    pickup_location: str
    destination: str
    material_type: str = 'general'
    weight: float = 10.0
    urgency_level: str = 'normal'
    truck_fleet: Optional[List[Any]] = None   # injected by Laravel from DB

class TruckInfo(BaseModel):
    truck_id: int
    driver_name: str
    plate_number: str
    capacity: float
    distance_km: float    # km from truck's current city to pickup
    estimated_price: float
    score: float

class TruckRecommendationResponse(BaseModel):
    recommended_trucks: List[TruckInfo]
