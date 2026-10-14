package com.ethioloadai.carrier.team.service;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class CompanyTeamAccess {
 private final CarrierCompanyRepository companies;
 private final CompanyMembershipRepository memberships;
 public CarrierCompany owner(Long companyId, User caller, boolean write) {
  authenticated(caller);
  CarrierCompany company = (write ? companies.findForUpdate(companyId) : companies.findById(companyId))
    .orElseThrow(CompanyTeamException::forbidden);
  var member = memberships.findByCarrierCompanyIdAndUserId(companyId, caller.getId())
    .orElseThrow(CompanyTeamException::forbidden);
  if (!company.getOwnerId().equals(caller.getId()) || member.getRole() != CompanyRole.CARRIER_OWNER
      || member.getStatus() != MembershipStatus.ACTIVE) throw CompanyTeamException.forbidden();
  if (write) approved(company);
  return company;
 }
 public void approved(CarrierCompany company) {
  if (company.getVerificationStatus() != VerificationStatus.APPROVED)
   throw CompanyTeamException.invalid("Company must be APPROVED to manage employees or accept invitations");
 }
 public void authenticated(User caller) {
  if (caller == null || caller.getId() == null) throw new CompanyTeamException("Authentication required", "UNAUTHENTICATED", 401);
 }
 public void employeeRole(CompanyRole role) {
  if (role != CompanyRole.FLEET_MANAGER && role != CompanyRole.DISPATCHER)
   throw CompanyTeamException.invalid("Role must be FLEET_MANAGER or DISPATCHER");
 }
}

