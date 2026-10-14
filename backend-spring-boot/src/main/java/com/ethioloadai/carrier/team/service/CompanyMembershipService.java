package com.ethioloadai.carrier.team.service;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.carrier.team.mapper.CompanyTeamMapper;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CompanyMembershipService {
 private final CompanyMembershipRepository memberships;
 private final UserRepository users;
 private final CompanyTeamAccess access;
 private final CompanyTeamMapper mapper;

 public Page<MembershipResponse> list(Long companyId, User caller, int page, int size) {
  access.owner(companyId, caller, false);
  return memberships.findByCarrierCompanyId(companyId, paging(page, size)).map(this::response);
 }
 public Page<MembershipResponse> mine(User caller, int page, int size) {
  access.authenticated(caller);
  return memberships.findByUserIdAndStatus(caller.getId(), MembershipStatus.ACTIVE, paging(page, size)).map(this::response);
 }
 @Transactional
 public MembershipResponse role(Long companyId, Long id, CompanyRole role, User caller) {
  access.owner(companyId, caller, true);
  access.employeeRole(role);
  var member = employee(companyId, id);
  if (member.getStatus() == MembershipStatus.REMOVED) throw CompanyTeamException.invalid("Removed employees must rejoin by invitation");
  member.setRole(role);
  member.setRoleChangedBy(caller.getId());
  member.setRoleChangedAt(LocalDateTime.now(ZoneOffset.UTC));
  return response(memberships.saveAndFlush(member));
 }
 @Transactional
 public MembershipResponse status(Long companyId, Long id, MembershipStatus status, User caller) {
  access.owner(companyId, caller, true);
  var member = employee(companyId, id);
  if (status == null || member.getStatus() == MembershipStatus.REMOVED || member.getStatus() == status)
   throw CompanyTeamException.invalid("Invalid membership status transition; removed employees must rejoin by invitation");
  member.setStatus(status);
  member.setStatusChangedBy(caller.getId());
  member.setStatusChangedAt(LocalDateTime.now(ZoneOffset.UTC));
  return response(memberships.saveAndFlush(member));
 }
 private CompanyMembership employee(Long companyId, Long id) {
  var member = memberships.findByIdAndCarrierCompanyId(id, companyId).orElseThrow(CompanyTeamException::forbidden);
  if (member.getRole() == CompanyRole.CARRIER_OWNER) throw CompanyTeamException.invalid("Owner membership cannot be changed");
  return member;
 }
 public MembershipResponse response(CompanyMembership member) {
  var response = mapper.membership(member);
  users.findById(member.getUserId()).ifPresent(user -> {
   response.setFullName(user.getFullName());
   response.setEmail(user.getEmail());
  });
  return response;
 }
 public static Pageable paging(int page, int size) {
  if (page < 0 || size < 1 || size > 100) throw CompanyTeamException.invalid("Page must be >= 0 and size between 1 and 100");
  return PageRequest.of(page, size, Sort.by("id").descending());
 }
}

