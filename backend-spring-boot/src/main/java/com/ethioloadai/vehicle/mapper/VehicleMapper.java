package com.ethioloadai.vehicle.mapper;

import com.ethioloadai.vehicle.dto.VehicleCreateRequest;
import com.ethioloadai.vehicle.dto.VehicleLocationRequest;
import com.ethioloadai.vehicle.dto.VehicleResponse;
import com.ethioloadai.vehicle.dto.VehicleUpdateRequest;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDateTime;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VehicleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "fleetOwnerId", ignore = true)
    @Mapping(target = "lastLocationAt", ignore = true)
    @Mapping(target = "availabilityStatus", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Vehicle toEntity(VehicleCreateRequest request);

    VehicleResponse toResponse(Vehicle vehicle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "fleetOwnerId", ignore = true)
    @Mapping(target = "lastLocationAt", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(VehicleUpdateRequest request, @MappingTarget Vehicle vehicle);

    default void updateLocationFromDto(VehicleLocationRequest request, @MappingTarget Vehicle vehicle) {
        vehicle.setLatitude(request.getLatitude());
        vehicle.setLongitude(request.getLongitude());
        vehicle.setCurrentCity(request.getCurrentCity());
        vehicle.setLastLocationAt(LocalDateTime.now());
    }
}
