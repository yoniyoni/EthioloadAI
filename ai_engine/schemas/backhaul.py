from pydantic import BaseModel
from typing import List, Optional

class BackhaulRequest(BaseModel):
    truck_id: int
    current_location: str
    destination: str
    available_cargo: Optional[List[dict]] = None

class BackhaulOpportunity(BaseModel):
    cargo_id: int
    pickup_location: str
    destination: str
    weight: float
    price: float
    score: float

class BackhaulResponse(BaseModel):
    opportunities: List[BackhaulOpportunity]
