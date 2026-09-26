package com.ethioloadai.email;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import java.time.LocalDateTime;
public interface EmailService {
 void sendInvitation(String recipient, String companyName, CompanyRole role, String acceptanceUrl, LocalDateTime expiresAt);
}

