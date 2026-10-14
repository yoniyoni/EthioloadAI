package com.ethioloadai.carrier.team.controller;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.service.*;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@Tag(name="Company Invitations", description="Tokens are sent in POST bodies, never URL queries")
public class CompanyInvitationController {
 private final CompanyInvitationService invitations;
 private final CompanyMembershipService memberships;
 private final InvitationRegistrationService registration;
 @PostMapping("/company-invitations/register")
 public ResponseEntity<InvitationRegistrationService.Result> register(
   @jakarta.validation.Valid @RequestBody InvitationRegistrationRequest request) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registration.register(request));
 }
 @PostMapping("/company-invitations/validate") @Operation(summary="Public token validation with limited metadata")
 public ResponseEntity<InvitationPreview> validate(@RequestBody InvitationTokenRequest request) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitations.validate(request.getToken()));
 }
 @PostMapping("/company-invitations/accept") @PreAuthorize("isAuthenticated()")
 @Operation(summary="Join the company and role from a single-use invitation; requires the invited email")
 public MembershipResponse accept(@RequestBody InvitationTokenRequest request, @AuthenticationPrincipal User caller) {
  return invitations.accept(request.getToken(), caller);
 }
 @GetMapping("/company-memberships/me") @PreAuthorize("isAuthenticated()")
 @Operation(summary="List the authenticated user's active company memberships")
 public Page<MembershipResponse> mine(@AuthenticationPrincipal User caller,
  @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
  return memberships.mine(caller, page, size);
 }
}

