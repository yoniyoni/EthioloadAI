from fastapi import APIRouter
from ..schemas.backhaul import BackhaulRequest, BackhaulResponse
from ..services.backhaul_service import find_backhaul_opportunities

router = APIRouter()

@router.post("/backhaul-opportunities", response_model=BackhaulResponse)
def backhaul_opportunities_api(request: BackhaulRequest):
    return find_backhaul_opportunities(request)
