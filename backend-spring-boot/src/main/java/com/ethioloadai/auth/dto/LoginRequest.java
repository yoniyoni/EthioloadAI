package com.ethioloadai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Identifier is required")
    @Size(max = 254, message = "Identifier must not exceed 254 characters")
    private String identifier;

    @NotBlank(message = "Password is required")
    @lombok.ToString.Exclude
    private String password;
}
