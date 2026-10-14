package com.ethioloadai.carrier.team.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InvitationRegistrationRequest {
 private String token;
 @NotBlank @Size(max=255)
 private String fullName;
 @NotBlank @Pattern(regexp="^\\+?[0-9]{10,15}$")
 private String phone;
 @NotBlank @Size(min=6, max=72)
 private String password;
}
