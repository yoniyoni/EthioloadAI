from fastapi import APIRouter
from ..schemas.empty_return import EmptyReturnRequest, EmptyReturnResponse
from ..services.empty_return_service import predict_empty_return

router = APIRouter()

@router.post("/predict-empty-return", response_model=EmptyReturnResponse)
def predict_empty_return_api(request: EmptyReturnRequest):
    return predict_empty_return(request)
