package com.ethioloadai.carrier.company.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** Editable fields only. PUT replaces these fields; omitted optional values are cleared. */
@Data
public class CarrierCompanyRequest {
    @NotBlank @Size(max = 255)
    private String companyName;
    @Size(max = 255)
    private String legalName;
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9/-]{1,49}", message = "TIN must be 2-50 letters, digits, slashes or hyphens")
    private String tinNumber;
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9/-]{1,99}", message = "Registration number must be 2-100 letters, digits, slashes or hyphens")
    private String businessRegistrationNumber;
    @Pattern(regexp = "\\+?[0-9]{7,15}", message = "Phone must contain 7-15 digits with an optional leading +")
    private String phone;
    @Email @Size(max = 255)
    private String email;
    @Size(max = 1000)
    private String businessAddress;
    @Size(max = 100)
    private String city;
}

