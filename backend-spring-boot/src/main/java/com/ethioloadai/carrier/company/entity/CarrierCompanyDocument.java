package com.ethioloadai.carrier.company.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carrier_company_documents", uniqueConstraints =
    @UniqueConstraint(name = "uq_carrier_document_type", columnNames = {"carrier_company_id", "document_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CarrierCompanyDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "carrier_company_id", nullable = false, updatable = false)
    private Long carrierCompanyId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 40)
    private CompanyDocumentType documentType;
    @Column(nullable = false, unique = true, length = 500)
    private String storageKey;
    @Column(nullable = false, length = 255)
    private String originalName;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private Long fileSize;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;
    @Column(length = 2000)
    private String reviewMessage;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(nullable = false)
    private LocalDateTime uploadedAt;
}

