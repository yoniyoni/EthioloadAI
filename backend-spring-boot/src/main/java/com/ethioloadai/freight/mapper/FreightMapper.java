package com.ethioloadai.freight.mapper;

import com.ethioloadai.freight.dto.CreateFreightRequest;
import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.dto.UpdateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface FreightMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shipper", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "matchedDriver", ignore = true)
    @Mapping(target = "matchedVehicle", ignore = true)
    @Mapping(target = "matchedPrice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Freight toEntity(CreateFreightRequest request);

    @Mapping(target = "shipperId", source = "shipper.id")
    @Mapping(target = "shipperName", source = "shipper.fullName")
    @Mapping(target = "matchedDriverId", source = "matchedDriver.id")
    @Mapping(target = "matchedDriverName", source = "matchedDriver.fullName")
    @Mapping(target = "matchedVehicleId", source = "matchedVehicle.id")
    FreightResponse toResponse(Freight freight);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shipper", ignore = true)
    @Mapping(target = "pickupLocation", ignore = true)
    @Mapping(target = "pickupLatitude", ignore = true)
    @Mapping(target = "pickupLongitude", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "destinationLatitude", ignore = true)
    @Mapping(target = "destinationLongitude", ignore = true)
    @Mapping(target = "materialType", ignore = true)
    @Mapping(target = "weightTons", ignore = true)
    @Mapping(target = "volumeM3", ignore = true)
    @Mapping(target = "distanceKm", ignore = true)
    @Mapping(target = "matchedDriver", ignore = true)
    @Mapping(target = "matchedVehicle", ignore = true)
    @Mapping(target = "matchedPrice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UpdateFreightRequest request, @MappingTarget Freight freight);
}
