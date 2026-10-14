from pydantic import BaseModel
from typing import List

class RouteOptimizerRequest(BaseModel):
    origin: str
    destination: str
    waypoints: List[str] = []

class RouteOptimizerResponse(BaseModel):
    optimized_route: List[str]
    total_distance_km: float
    estimated_time_min: float
