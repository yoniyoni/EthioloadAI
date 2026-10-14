package com.ethioloadai.carrier.team.controller;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.service.*;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/carrier-companies/{companyId}/team")
@RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
@Tag(name="Company Team", description="Active primary owners manage their own company; writes require approval")
public class CompanyTeamController {
 private final CompanyInvitationService invitations;
 private final CompanyMembershipService memberships;
 @PostMapping("/invitations") @Operation(summary="Invite an employee by email; SMTP acceptance required")
 public ResponseEntity<InvitationResponse> invite(@PathVariable Long companyId, @Valid @RequestBody InvitationRequest request, @AuthenticationPrincipal User caller) {
  return ResponseEntity.status(HttpStatus.CREATED).body(invitations.invite(companyId, request, caller));
 }
 @GetMapping("/invitations") @Operation(summary="List private company invitations")
 public Page<InvitationResponse> invitations(@PathVariable Long companyId, @AuthenticationPrincipal User caller,
   @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
  return invitations.list(companyId, caller, page, size);
 }
 @PostMapping("/invitations/{id}/resend") @Operation(summary="Replace an invitation with a new token and email")
 public InvitationResponse resend(@PathVariable Long companyId, @PathVariable Long id, @AuthenticationPrincipal User caller) {
  return invitations.resend(companyId, id, caller);
 }
 @PostMapping("/invitations/{id}/revoke") @Operation(summary="Revoke a pending invitation")
 public InvitationResponse revoke(@PathVariable Long companyId, @PathVariable Long id, @AuthenticationPrincipal User caller) {
  return invitations.revoke(companyId, id, caller);
 }
 @GetMapping("/members") @Operation(summary="List private team member details")
 public Page<MembershipResponse> members(@PathVariable Long companyId, @AuthenticationPrincipal User caller,
   @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
  return memberships.list(companyId, caller, page, size);
 }
 @PatchMapping("/members/{id}/role") @Operation(summary="Change an employee between fleet manager and dispatcher")
 public MembershipResponse role(@PathVariable Long companyId, @PathVariable Long id, @Valid @RequestBody MemberRoleRequest request, @AuthenticationPrincipal User caller) {
  return memberships.role(companyId, id, request.getRole(), caller);
 }
 @PostMapping("/members/{id}/deactivate") @Operation(summary="Deactivate company membership without disabling the User account")
 public MembershipResponse deactivate(@PathVariable Long companyId, @PathVariable Long id, @AuthenticationPrincipal User caller) {
  return memberships.status(companyId, id, MembershipStatus.INACTIVE, caller);
 }
 @PostMapping("/members/{id}/activate") @Operation(summary="Reactivate an inactive employee")
 public MembershipResponse activate(@PathVariable Long companyId, @PathVariable Long id, @AuthenticationPrincipal User caller) {
  return memberships.status(companyId, id, MembershipStatus.ACTIVE, caller);
 }
 @DeleteMapping("/members/{id}") @Operation(summary="Mark employee REMOVED, preserving account and membership history")
 public MembershipResponse remove(@PathVariable Long companyId, @PathVariable Long id, @AuthenticationPrincipal User caller) {
  return memberships.status(companyId, id, MembershipStatus.REMOVED, caller);
 }
}

