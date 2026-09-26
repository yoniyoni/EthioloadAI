package com.ethioloadai.carrier.team.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="company_invitations")
@Getter @Setter @NoArgsConstructor
public class CompanyInvitation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long carrierCompanyId;
 @Column(nullable=false, length=255) private String invitedEmail;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private CompanyRole invitedRole;
 @com.fasterxml.jackson.annotation.JsonIgnore
 @Column(nullable=false, unique=true, length=64) private String tokenHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private InvitationStatus status;
 @Column(nullable=false) private Long invitedBy;
 @Column(nullable=false) private LocalDateTime expiresAt;
 private LocalDateTime acceptedAt;
 private Long acceptedBy;
 private LocalDateTime revokedAt;
 private Long revokedBy;
 @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(nullable=false) private LocalDateTime updatedAt;
}

