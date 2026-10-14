package com.ethioloadai.carrier.team.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name="company_memberships", uniqueConstraints=@UniqueConstraint(name="uq_company_membership", columnNames={"carrier_company_id","user_id"}))
@Getter @Setter @NoArgsConstructor
public class CompanyMembership {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long carrierCompanyId;
 @Column(nullable=false) private Long userId;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private CompanyRole role;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private MembershipStatus status;
 @Column(nullable=false) private LocalDateTime joinedAt;
 private Long invitedBy;
 private Long roleChangedBy;
 private LocalDateTime roleChangedAt;
 private Long statusChangedBy;
 private LocalDateTime statusChangedAt;
 @CreationTimestamp @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(nullable=false) private LocalDateTime updatedAt;
}
