package com.ethioloadai.freight.service;

import com.ethioloadai.freight.dto.*;

import java.util.List;

public interface FreightService {
    FreightResponse createFreight(Long userId, CreateFreightRequest request);
    FreightResponse getFreightById(Long id);
    List<FreightResponse> getUserFreights(Long userId);
    List<FreightResponse> searchFreights(FreightFilterRequest filter);
    FreightResponse updateFreight(Long id, Long userId, UpdateFreightRequest request);
    void cancelFreight(Long id, Long userId);
}
