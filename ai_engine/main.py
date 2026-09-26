from fastapi import FastAPI
from .routers import recommendation, backhaul, pricing, route_optimizer, empty_return

app = FastAPI(title="EthioLoadAI AI Engine")

app.include_router(recommendation.router, prefix="/ai", tags=["AI Recommendation"])
app.include_router(backhaul.router, prefix="/ai", tags=["Backhaul Optimization"])
app.include_router(pricing.router, prefix="/ai", tags=["Pricing"])
app.include_router(route_optimizer.router, prefix="/ai", tags=["Route Optimization"])
app.include_router(empty_return.router, prefix="/ai", tags=["Empty Return Prediction"])
