package com.ethioloadai.carrier.team.dto;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class InvitationRequest {
 @NotBlank @Email @Size(max=255) private String email;
 @NotNull private CompanyRole role;
 public void setEmail(String email) {
  this.email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
 }
}
