package com.ethioloadai.ai.service;

import com.ethioloadai.ai.dto.*;

import java.util.Map;

public interface AiGatewayService {

    AiPricePredictionResponse predictPrice(Map<String, Object> params);

    AiRecommendationResponse recommendTruck(Map<String, Object> params);

    AiBackhaulResponse backhaulOpportunities(Map<String, Object> params);

    Map<String, Object> predictEmptyReturn(Map<String, Object> params);

    Map<String, Object> optimizeRoute(Map<String, Object> params);

    AiAssistantResponse chatAssistant(String message, Map<String, Object> context);
}
