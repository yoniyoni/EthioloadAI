package com.ethioloadai.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for updating a user via admin endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String name;

    private String email;

    private String phone;

    private String role;

    private String password;

    @JsonProperty("is_active")
    @JsonAlias({"isActive", "is_active"})
    private Boolean isActive;
}
