from fastapi import APIRouter, Depends
from ..schemas.recommendation import TruckRecommendationRequest, TruckRecommendationResponse
from ..services.recommendation_service import recommend_truck

router = APIRouter()

@router.post("/recommend-truck", response_model=TruckRecommendationResponse)
def recommend_truck_api(request: TruckRecommendationRequest):
    return recommend_truck(request)
