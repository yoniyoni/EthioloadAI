from fastapi import APIRouter
from ..schemas.route_optimizer import RouteOptimizerRequest, RouteOptimizerResponse
from ..services.route_optimizer_service import optimize_route

router = APIRouter()

@router.post("/optimize-route", response_model=RouteOptimizerResponse)
def optimize_route_api(request: RouteOptimizerRequest):
    return optimize_route(request)
