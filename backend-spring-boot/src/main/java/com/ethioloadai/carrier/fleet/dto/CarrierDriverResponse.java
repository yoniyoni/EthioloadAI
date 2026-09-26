package com.ethioloadai.carrier.fleet.dto;

import com.ethioloadai.carrier.fleet.entity.CarrierDriver;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Public projection of a {@link CarrierDriver} row enriched with
 * driver user information.
 */
@Value
@Builder
public class CarrierDriverResponse {

    Long id;                         // carrier_drivers.id
    Long carrierCompanyId;
    Long driverUserId;

    // Enriched from User
    String fullName;
    String phone;
    String email;
    Boolean verificationStatus;      // KYC approved
    Boolean isActive;                // account active

    CarrierDriverStatus status;
    DriverAvailability availability;

    Long addedBy;
    LocalDateTime joinedAt;
    LocalDateTime leftAt;
    String notes;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    /** Build from a CarrierDriver + the corresponding User. */
    public static CarrierDriverResponse from(
            CarrierDriver cd,
            com.ethioloadai.user.entity.User user) {
        return CarrierDriverResponse.builder()
                .id(cd.getId())
                .carrierCompanyId(cd.getCarrierCompanyId())
                .driverUserId(cd.getDriverUserId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .verificationStatus(user.getVerificationStatus())
                .isActive(user.getIsActive())
                .status(cd.getStatus())
                .availability(cd.getAvailability())
                .addedBy(cd.getAddedBy())
                .joinedAt(cd.getJoinedAt())
                .leftAt(cd.getLeftAt())
                .notes(cd.getNotes())
                .createdAt(cd.getCreatedAt())
                .updatedAt(cd.getUpdatedAt())
                .build();
    }
}
