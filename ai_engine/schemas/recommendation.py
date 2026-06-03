from pydantic import BaseModel
from typing import List, Optional

class TruckRecommendationRequest(BaseModel):
    pickup_location: str
    destination: str
    material_type: str
    weight: float
    urgency_level: str

class TruckInfo(BaseModel):
    truck_id: int
    driver_name: str
    plate_number: str
    capacity: float
    distance_km: float
    estimated_price: float
    score: float

class TruckRecommendationResponse(BaseModel):
    recommended_trucks: List[TruckInfo]
