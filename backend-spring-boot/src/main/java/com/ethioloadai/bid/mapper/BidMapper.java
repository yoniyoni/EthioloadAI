package com.ethioloadai.bid.mapper;

import com.ethioloadai.bid.dto.BidResponse;
import com.ethioloadai.bid.entity.Bid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BidMapper {

    public BidResponse toResponse(Bid bid) {
        if (bid == null) {
            return null;
        }

        BidResponse.BidResponseBuilder builder = BidResponse.builder()
                .id(bid.getId())
                .freightId(bid.getCargoRequest().getId())
                .driverId(bid.getDriver().getId())
                .driverName(getDriverDisplayName(bid.getDriver()))
                .vehicleId(bid.getVehicle().getId())
                .vehiclePlateNumber(getVehiclePlateNumber(bid.getVehicle()))
                .vehicleType(getVehicleType(bid.getVehicle()))
                .amount(bid.getAmount())
                .note(bid.getNote())
                .availableDatetime(bid.getAvailableDatetime())
                .status(bid.getStatus().name().toLowerCase())
                .aiScore(bid.getAiScore())
                .isRecommended(bid.getIsRecommended())
                .distanceKm(bid.getDistanceKm())
                .createdAt(bid.getCreatedAt())
                .updatedAt(bid.getUpdatedAt());

        // Counter-offer fields (only if present)
        if (bid.getCounterAmount() != null) {
            builder.counterAmount(bid.getCounterAmount())
                   .counterNote(bid.getCounterNote())
                   .counterBy(bid.getCounterBy() != null ? bid.getCounterBy().name().toLowerCase() : null)
                   .counterAt(bid.getCounterAt());
        }

        // Cargo summary fields
        try {
            if (bid.getCargoRequest() != null) {
                builder.pickupLocation(bid.getCargoRequest().getPickupLocation())
                       .destination(bid.getCargoRequest().getDestination())
                       .materialType(bid.getCargoRequest().getMaterialType())
                       .weightTons(bid.getCargoRequest().getWeightTons())
                       .budget(bid.getCargoRequest().getBudget());

                if (bid.getCargoRequest().getShipper() != null) {
                    builder.shipperName(bid.getCargoRequest().getShipper().getFullName());
                    if (bid.getStatus() == Bid.BidStatus.ACCEPTED) {
                        builder.shipperPhone(bid.getCargoRequest().getShipper().getPhone());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not map cargo details for bid {}: {}", bid.getId(), e.getMessage());
        }

        return builder.build();
    }

    private String getDriverDisplayName(com.ethioloadai.user.entity.User driver) {
        if (driver == null) {
            return null;
        }
        
        try {
            String fullName = driver.getFullName();
            if (fullName != null && !fullName.trim().isEmpty()) {
                return fullName;
            }
        } catch (Exception e) {
            // Lazy proxy fallback
        }
        return "Driver #" + driver.getId();
    }

    private String getVehiclePlateNumber(com.ethioloadai.vehicle.entity.Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        try {
            return vehicle.getPlateNumber();
        } catch (Exception e) {
            return null;
        }
    }

    private String getVehicleType(com.ethioloadai.vehicle.entity.Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        try {
            return vehicle.getTruckType();
        } catch (Exception e) {
            return null;
        }
    }
}