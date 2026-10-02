package com.ethioloadai.carrier.team.service;

import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.service.AuthenticationService;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class InvitationRegistrationService {
 private final CompanyInvitationService invitations;
 private final AuthenticationService authentication;
 private final UserRepository users;

 public record Result(AuthenticationResponse session, MembershipResponse membership) {}

 // Account creation and invitation consumption must commit or roll back together.
 @Transactional
 public Result register(InvitationRegistrationRequest request) {
  var preview = invitations.validate(request.getToken());
  var registration = new RegisterRequest(request.getFullName(), request.getPhone(),
      preview.email(), request.getPassword(), "shipper");
  // The base account role does not grant company permissions. Company/role are
  // taken exclusively from the invitation by accept(), never from client input.
  var session = authentication.register(registration);
  var user = users.findById(session.getUser().getId()).orElseThrow();
  var membership = invitations.accept(request.getToken(), user);
  return new Result(session, membership);
 }
}
