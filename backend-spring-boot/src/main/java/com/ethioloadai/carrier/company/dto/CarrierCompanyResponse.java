package com.ethioloadai.carrier.company.dto;

import com.ethioloadai.carrier.company.entity.VerificationStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarrierCompanyResponse {
    private Long id;
    private String companyName;
    private String legalName;
    private String tinNumber;
    private String businessRegistrationNumber;
    private String phone;
    private String email;
    private String businessAddress;
    private String city;
    private Long ownerId;
    private VerificationStatus verificationStatus;
    private String reviewMessage;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

