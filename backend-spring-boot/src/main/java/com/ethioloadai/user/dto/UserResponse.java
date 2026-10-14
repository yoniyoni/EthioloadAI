package com.ethioloadai.user.dto;

import com.ethioloadai.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Safe read-only user projection for admin directory and user operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private Boolean isVerified;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonProperty("fullName")
    public String getFullName() {
        return name;
    }

    public static UserResponse from(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name().toLowerCase() : null)
                .isVerified(Boolean.TRUE.equals(user.getVerificationStatus()))
                .isActive(user.getIsActive() == null || Boolean.TRUE.equals(user.getIsActive()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
