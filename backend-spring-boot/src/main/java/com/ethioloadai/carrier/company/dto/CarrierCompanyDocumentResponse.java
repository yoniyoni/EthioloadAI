package com.ethioloadai.carrier.company.dto;

import com.ethioloadai.carrier.company.entity.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarrierCompanyDocumentResponse {
    private Long id;
    private Long carrierCompanyId;
    private CompanyDocumentType documentType;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private VerificationStatus verificationStatus;
    private String reviewMessage;
    private LocalDateTime uploadedAt;
    private LocalDateTime reviewedAt;
    // Files are available only through the authenticated download endpoint.
}

