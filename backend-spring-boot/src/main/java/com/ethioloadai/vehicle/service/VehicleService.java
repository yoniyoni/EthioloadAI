package com.ethioloadai.vehicle.service;

import com.ethioloadai.vehicle.dto.VehicleCreateRequest;
import com.ethioloadai.vehicle.dto.VehicleLocationRequest;
import com.ethioloadai.vehicle.dto.VehicleResponse;
import com.ethioloadai.vehicle.dto.VehicleUpdateRequest;

import java.math.BigDecimal;
import java.util.List;

public interface VehicleService {
    VehicleResponse createVehicle(VehicleCreateRequest request, Long userId);
    VehicleResponse getVehicleById(Long id, Long userId);
    List<VehicleResponse> getMyVehicles(Long userId);
    List<VehicleResponse> getAllVehicles();
    VehicleResponse updateVehicle(Long id, VehicleUpdateRequest request, Long userId);
    void deleteVehicle(Long id, Long userId);
    VehicleResponse updateVehicleLocation(Long id, VehicleLocationRequest request, Long userId);
    VehicleResponse updateDriverLocation(VehicleLocationRequest request, Long userId);
    List<VehicleResponse> getNearbyVehicles(BigDecimal latitude, BigDecimal longitude, Double radius);
    List<VehicleResponse> getAvailableVehiclesInCity(String city);
    VehicleResponse updateCurrentCity(Long id, String currentCity, Long userId);
}
