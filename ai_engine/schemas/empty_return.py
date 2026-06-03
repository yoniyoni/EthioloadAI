from pydantic import BaseModel

class EmptyReturnRequest(BaseModel):
    origin: str
    destination: str
    truck_type: str = "general"

class EmptyReturnResponse(BaseModel):
    destination: str
    empty_return_probability: float
    risk_level: str
    recommendation: str
