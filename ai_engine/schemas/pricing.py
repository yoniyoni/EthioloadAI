from pydantic import BaseModel

class PricingRequest(BaseModel):
    pickup_location: str
    destination: str
    weight: float
    material_type: str

class PricingResponse(BaseModel):
    estimated_price: float
    confidence: float
