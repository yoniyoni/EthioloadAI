package com.ethioloadai.carrier.team.dto;
import com.ethioloadai.carrier.team.entity.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class MembershipResponse {
 private Long id;
 private Long carrierCompanyId;
 private String companyName;
 private Long userId;
 private String fullName;
 private String email;
 private CompanyRole role;
 private MembershipStatus status;
 private LocalDateTime joinedAt;
 private Long invitedBy;
 private Long roleChangedBy;
 private LocalDateTime roleChangedAt;
 private Long statusChangedBy;
 private LocalDateTime statusChangedAt;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
}

