package com.ethioloadai.carrier.team.dto;
import com.ethioloadai.carrier.team.entity.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class InvitationResponse {
 private Long id;
 private Long carrierCompanyId;
 private String invitedEmail;
 private CompanyRole invitedRole;
 private InvitationStatus status;
 private Long invitedBy;
 private LocalDateTime expiresAt;
 private LocalDateTime acceptedAt;
 private LocalDateTime revokedAt;
 private LocalDateTime createdAt;
}

