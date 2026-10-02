package com.ethioloadai.carrier.team.service;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.carrier.team.mapper.CompanyTeamMapper;
import com.ethioloadai.carrier.team.repository.*;
import com.ethioloadai.email.EmailService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.*;
import java.util.Locale;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CompanyInvitationService {
 private final CompanyInvitationRepository invitations;
 private final CompanyMembershipRepository memberships;
 private final CarrierCompanyRepository companies;
 private final UserRepository users;
 private final CompanyTeamAccess access;
 private final CompanyMembershipService membershipService;
 private final CompanyTeamMapper mapper;
 private final InvitationTokens tokens;
 private final InvitationProperties properties;
 private final EmailService emailService;
 private final EntityManager entityManager;

 @Transactional
 public InvitationResponse invite(Long companyId, InvitationRequest request, User caller) {
  CarrierCompany company = access.owner(companyId, caller, true);
  access.employeeRole(request.getRole());
  return issue(company, normalize(request.getEmail()), request.getRole(), caller);
 }

 @Transactional
 public InvitationResponse resend(Long companyId, Long id, User caller) {
  CarrierCompany company = access.owner(companyId, caller, true);
  var original = invitations.findByIdAndCarrierCompanyId(id, companyId).orElseThrow(CompanyTeamException::forbidden);
  if (original.getStatus() != InvitationStatus.PENDING && original.getStatus() != InvitationStatus.EXPIRED)
   throw CompanyTeamException.invalid("Only pending or expired invitations can be resent");
  return issue(company, original.getInvitedEmail(), original.getInvitedRole(), caller);
 }

 private InvitationResponse issue(CarrierCompany company, String email, CompanyRole role, User caller) {
  if (email.isBlank() || email.length() > 255) throw CompanyTeamException.invalid("Valid email is required");
  if (memberships.activeEmailExists(company.getId(), email))
   throw new CompanyTeamException("This email already belongs to an active company member", "MEMBER_ALREADY_ACTIVE", 409);
  LocalDateTime now = now();
  if ((properties.getCooldownSeconds() > 0 && invitations.existsByCarrierCompanyIdAndCreatedAtAfter(company.getId(), now.minusSeconds(properties.getCooldownSeconds())))
      || invitations.countByCarrierCompanyIdAndCreatedAtAfter(company.getId(), now.minusHours(1)) >= properties.getMaxPerHour())
   throw new CompanyTeamException("Please wait before sending another company invitation", "INVITATION_RATE_LIMITED", 429);
  String base = frontendBase();
  // Retain old rows for audit; replacement invalidates every previous pending token.
  for (var old : invitations.findByCarrierCompanyIdAndInvitedEmailAndStatus(company.getId(), email, InvitationStatus.PENDING)) {
   if (!old.getExpiresAt().isAfter(now)) old.setStatus(InvitationStatus.EXPIRED);
   else {
    old.setStatus(InvitationStatus.REVOKED);
    old.setRevokedAt(now);
    old.setRevokedBy(caller.getId());
   }
  }
  invitations.flush(); // release the PostgreSQL partial unique key before inserting its replacement
  String rawToken = tokens.generate();
  var invitation = new CompanyInvitation();
  invitation.setCarrierCompanyId(company.getId());
  invitation.setInvitedEmail(email);
  invitation.setInvitedRole(role);
  invitation.setTokenHash(tokens.hash(rawToken));
  invitation.setStatus(InvitationStatus.PENDING);
  invitation.setInvitedBy(caller.getId());
  invitation.setCreatedAt(now);
  invitation.setExpiresAt(now.plusHours(properties.getExpiryHours()));
  invitations.saveAndFlush(invitation);
  // Bounded synchronous send: failures roll back creation AND replacement/revocation.
  // SMTP acceptance is not inbox-delivery confirmation. A post-send DB failure may yield a dead link.
  emailService.sendInvitation(email, company.getCompanyName(), role,
      base + "/invitations/accept#token=" + rawToken, invitation.getExpiresAt());
  return response(invitation);
 }

 public Page<InvitationResponse> list(Long companyId, User caller, int page, int size) {
  access.owner(companyId, caller, false);
  return invitations.findByCarrierCompanyId(companyId, CompanyMembershipService.paging(page, size)).map(this::response);
 }

 @Transactional
 public InvitationResponse revoke(Long companyId, Long id, User caller) {
  access.owner(companyId, caller, true);
  var invitation = invitations.findByIdAndCarrierCompanyId(id, companyId).orElseThrow(CompanyTeamException::forbidden);
  if (invitation.getStatus() != InvitationStatus.PENDING) throw CompanyTeamException.invalid("Only pending invitations can be revoked");
  invitation.setStatus(InvitationStatus.REVOKED);
  invitation.setRevokedBy(caller.getId());
  invitation.setRevokedAt(now());
  return response(invitations.saveAndFlush(invitation));
 }

 public InvitationPreview validate(String token) {
  var invitation = invitations.findByTokenHash(tokens.hash(token)).orElseThrow(CompanyTeamException::invalidToken);
  pending(invitation);
  var company = companies.findById(invitation.getCarrierCompanyId()).orElseThrow(CompanyTeamException::invalidToken);
  access.approved(company);
  return new InvitationPreview(true, company.getCompanyName(), invitation.getInvitedRole(),
      invitation.getInvitedEmail(), invitation.getExpiresAt(), users.existsByEmail(invitation.getInvitedEmail()));
 }

 @Transactional
 public MembershipResponse accept(String token, User caller) {
  access.authenticated(caller);
  var invitation = invitations.findByTokenHash(tokens.hash(token)).orElseThrow(CompanyTeamException::invalidToken);
  // Use the same parent-first lock order as issue/resend/revoke and all team changes.
  var company = companies.findForUpdate(invitation.getCarrierCompanyId()).orElseThrow(CompanyTeamException::invalidToken);
  entityManager.refresh(invitation); // another transaction may have consumed/revoked it while we waited
  pending(invitation);
  access.approved(company);
  User currentUser = users.findById(caller.getId()).orElseThrow(CompanyTeamException::forbidden);
  if (!Boolean.TRUE.equals(currentUser.getIsActive()) || !normalize(currentUser.getEmail()).equals(invitation.getInvitedEmail()))
   throw new CompanyTeamException("Sign in with the invited email address", "INVITATION_EMAIL_MISMATCH", 403);
  var member = memberships.findByCarrierCompanyIdAndUserId(company.getId(), caller.getId()).orElseGet(CompanyMembership::new);
  if (member.getStatus() == MembershipStatus.ACTIVE || member.getRole() == CompanyRole.CARRIER_OWNER)
   throw new CompanyTeamException("Already an active company member", "MEMBER_ALREADY_ACTIVE", 409);
  LocalDateTime now = now();
  member.setCarrierCompanyId(company.getId());
  member.setUserId(caller.getId());
  member.setRole(invitation.getInvitedRole());
  member.setStatus(MembershipStatus.ACTIVE);
  member.setJoinedAt(now);
  member.setInvitedBy(invitation.getInvitedBy());
  member.setRoleChangedBy(invitation.getInvitedBy());
  member.setRoleChangedAt(now);
  member.setStatusChangedBy(caller.getId());
  member.setStatusChangedAt(now);
  memberships.saveAndFlush(member);
  invitation.setStatus(InvitationStatus.ACCEPTED);
  invitation.setAcceptedBy(caller.getId());
  invitation.setAcceptedAt(now);
  invitations.saveAndFlush(invitation);
  return membershipService.response(member);
 }

 private void pending(CompanyInvitation invitation) {
  if (invitation.getStatus() == InvitationStatus.EXPIRED
      || (invitation.getStatus() == InvitationStatus.PENDING && !invitation.getExpiresAt().isAfter(now())))
   throw new CompanyTeamException("Invitation has expired; ask the company owner for a new invitation", "INVITATION_EXPIRED", 400);
  if (invitation.getStatus() != InvitationStatus.PENDING) throw CompanyTeamException.invalidToken();
 }
 private InvitationResponse response(CompanyInvitation invitation) {
  var response = mapper.invitation(invitation);
  if (invitation.getStatus() == InvitationStatus.PENDING && !invitation.getExpiresAt().isAfter(now()))
   response.setStatus(InvitationStatus.EXPIRED);
  return response;
 }
 private String frontendBase() {
  try {
   URI uri = URI.create(properties.getFrontendBaseUrl());
   boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
   if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
       || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))) throw CompanyTeamException.mail();
   return uri.toString().replaceAll("/+$", "");
  } catch (RuntimeException ex) { throw CompanyTeamException.mail(); }
 }
 private String normalize(String email) { return email == null ? "" : email.strip().toLowerCase(Locale.ROOT); }
 private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
