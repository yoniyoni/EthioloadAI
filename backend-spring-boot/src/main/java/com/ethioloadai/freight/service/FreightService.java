package com.ethioloadai.freight.service;

import com.ethioloadai.freight.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ethioloadai.user.entity.User;

import java.util.List;

public interface FreightService {
    FreightResponse createFreight(Long userId, CreateFreightRequest request);
    FreightResponse getFreightById(Long id);
    List<FreightResponse> getUserFreights(Long userId);
    /** Paginated freight search — always use this for the public listing endpoint. */
    Page<FreightResponse> searchFreightsPaged(FreightFilterRequest filter, Pageable pageable);

    Page<FreightResponse> searchFreightsPaged(FreightFilterRequest filter, Pageable pageable, User caller);
    FreightResponse updateFreight(Long id, Long userId, UpdateFreightRequest request);
    void cancelFreight(Long id, Long userId);
}
