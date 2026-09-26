package com.ethioloadai.fleet.dto;

import com.ethioloadai.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetDriverResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private Boolean isActive;
    private Boolean verificationStatus;

    public static FleetDriverResponse from(User user) {
        return FleetDriverResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .verificationStatus(user.getVerificationStatus())
                .build();
    }
}
