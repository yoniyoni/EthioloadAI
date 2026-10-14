package com.ethioloadai.carrier.company.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "carrier_companies")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CarrierCompany {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String companyName;
    @Column(length = 255)
    private String legalName;
    @Column(unique = true, length = 50)
    private String tinNumber;
    @Column(unique = true, length = 100)
    private String businessRegistrationNumber;
    @Column(length = 20)
    private String phone;
    @Column(length = 255)
    private String email;
    @Column(length = 1000)
    private String businessAddress;
    @Column(length = 100)
    private String city;
    // Same scalar user reference convention as Vehicle; enforced by a users FK.
    @Column(nullable = false, updatable = false)
    private Long ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;
    @Column(length = 2000)
    private String reviewMessage;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime submittedAt;
    @CreationTimestamp @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(nullable = false)
    private LocalDateTime updatedAt;
}

