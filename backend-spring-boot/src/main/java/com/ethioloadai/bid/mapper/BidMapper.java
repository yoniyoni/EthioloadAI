package com.ethioloadai.bid.mapper;

import com.ethioloadai.bid.dto.BidResponse;
import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.freight.entity.Freight;
import org.springframework.stereotype.Component;

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
                .createdAt(bid.getCreatedAt())
                .updatedAt(bid.getUpdatedAt());

        Freight freight = bid.getCargoRequest();
        if (freight != null) {
            builder.materialType(freight.getMaterialType())
                    .weightTons(freight.getWeightTons());
            if (freight.getPriceType() == Freight.PriceType.FIXED) {
                builder.budget(freight.getBudget());
            }
        }

        // Counter-offer fields (only if present)
        if (bid.getCounterAmount() != null) {
            builder.counterAmount(bid.getCounterAmount())
                   .counterNote(bid.getCounterNote())
                   .counterBy(bid.getCounterBy() != null ? bid.getCounterBy().name().toLowerCase() : null)
                   .counterAt(bid.getCounterAt());
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