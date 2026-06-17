from pydantic import BaseModel
from typing import Optional

class PricingRequest(BaseModel):
    pickup_location: str = ''
    destination: str = ''
    weight: float = 10.0
    material_type: Optional[str] = 'general'
    urgency_level: Optional[str] = 'normal'
    # Laravel also sends these aliases
    from_: Optional[str] = None
    to: Optional[str] = None
    # Admin-set rates passed from Laravel (overrides built-in defaults)
    rate_min: Optional[float] = None
    rate_max: Optional[float] = None

    class Config:
        populate_by_name = True

class PricingResponse(BaseModel):
    price_min: int
    price_max: int
    estimated_price: int           # midpoint — kept for UI compatibility
    distance_km: int
    currency: str = 'ETB'
    confidence: float = 0.85
    source: str = 'ai_engine'
