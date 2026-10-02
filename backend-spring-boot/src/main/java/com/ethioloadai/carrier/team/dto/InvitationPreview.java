package com.ethioloadai.carrier.team.dto;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import java.time.LocalDateTime;
public record InvitationPreview(boolean valid, String companyName, CompanyRole role, String email, LocalDateTime expiresAt, boolean accountExists) {}

