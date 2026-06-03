from fastapi import APIRouter
from ..schemas.pricing import PricingRequest, PricingResponse
from ..services.pricing_service import predict_price

router = APIRouter()

@router.post("/predict-price", response_model=PricingResponse)
def predict_price_api(request: PricingRequest):
    return predict_price(request)
