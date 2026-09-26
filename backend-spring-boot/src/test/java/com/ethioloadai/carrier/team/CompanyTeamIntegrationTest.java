package com.ethioloadai.carrier.team;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class CompanyTeamIntegrationTest extends TeamTestSupport {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.boot.actuate.health.HealthContributorRegistry health;
 @Test void optionalMailDoesNotDisableApplicationHealth() {
  assertThat(health.getContributor("mail")).isNull();
 }
 @Test void newCompanyCreatesActiveOwnerMembershipAndLeavesPlatformRoleAlone() {
  var member = memberships.findByCarrierCompanyIdAndUserId(companyId,owner.getId()).orElseThrow();
  assertThat(member.getRole()).isEqualTo(CompanyRole.CARRIER_OWNER);
  assertThat(member.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
  assertThat(users.findById(owner.getId()).orElseThrow().getRole()).isEqualTo(User.Role.SHIPPER);
 }
 @ParameterizedTest @EnumSource(value=CompanyRole.class,names={"FLEET_MANAGER","DISPATCHER"})
 void ownerCanInviteAllowedRoles(CompanyRole role) throws Exception {
  mvc.perform(post(team()+"/invitations").header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON)
    .content(body(request(employee.getEmail(),role))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.invitedRole").value(role.name()))
   .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.tokenHash").doesNotExist());
  verify(email).sendInvitation(eq(employee.getEmail()),eq("Example Logistics"),eq(role),anyString(),any());
 }
 @ParameterizedTest @ValueSource(strings={"CARRIER_OWNER","ADMIN","INVALID"})
 void cannotInviteUnsupportedRole(String role) throws Exception {
  mvc.perform(post(team()+"/invitations").header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON)
    .content(body(Map.of("email",employee.getEmail(),"role",role)))).andExpect(status().isBadRequest());
  verifyNoInteractions(email);
 }
 @Test void invalidEmailRejected() throws Exception {
  mvc.perform(post(team()+"/invitations").header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON)
    .content(body(request("invalid",CompanyRole.DISPATCHER)))).andExpect(status().isUnprocessableEntity());
 }
 @Test void companyMustBeApproved() {
  companyId = company(owner,false);
  assertThatThrownBy(()->invite(employee,CompanyRole.DISPATCHER)).isInstanceOf(CompanyTeamException.class);
  verifyNoInteractions(email);
 }
 @Test void adminHasNoOwnerOverride() throws Exception {
  mvc.perform(get(team()+"/members").header("Authorization",auth(admin))).andExpect(status().isForbidden());
 }
 @Test void unrelatedOwnerCannotManageAnotherCompany() throws Exception {
  company(stranger,true);
  mvc.perform(post(team()+"/invitations").header("Authorization",auth(stranger)).contentType(MediaType.APPLICATION_JSON)
    .content(body(request(employee.getEmail(),CompanyRole.DISPATCHER)))).andExpect(status().isForbidden());
  mvc.perform(get(team()+"/members").header("Authorization",auth(stranger))).andExpect(status().isForbidden());
  mvc.perform(get(team()+"/invitations").header("Authorization",auth(stranger))).andExpect(status().isForbidden());
 }
 @Test void unauthenticatedManagementAndAcceptanceDenied() throws Exception {
  mvc.perform(get(team()+"/members")).andExpect(status().isUnauthorized());
  mvc.perform(post("/company-invitations/accept").contentType(MediaType.APPLICATION_JSON)
    .content(body(tokenBody(tokens.generate())))).andExpect(status().isUnauthorized());
 }
 @Test void duplicateInviteRetiresOldTokenAndNormalizesEmail() {
  var first = invite(employee,CompanyRole.FLEET_MANAGER);
  String old = token(employee);
  var next = invitations.invite(companyId,request("  "+employee.getEmail().toUpperCase(Locale.ROOT)+"  ",CompanyRole.DISPATCHER),owner);
  assertThat(next.getId()).isNotEqualTo(first.getId());
  assertThat(next.getInvitedEmail()).isEqualTo(employee.getEmail());
  assertThat(invitationRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(InvitationStatus.REVOKED);
  assertThatThrownBy(()->invitations.validate(old)).isInstanceOf(CompanyTeamException.class);
  assertThat(invitationRepository.findByCarrierCompanyIdAndInvitedEmailAndStatus(companyId,employee.getEmail(),InvitationStatus.PENDING)).hasSize(1);
 }
 @Test void activeMemberCannotBeReinvited() {
  join();
  assertThatThrownBy(()->invite(employee,CompanyRole.DISPATCHER)).extracting("code").isEqualTo("MEMBER_ALREADY_ACTIVE");
 }
 @Test void rawTokenNeverStoredOrReturned() {
  var response=invite(employee,CompanyRole.DISPATCHER);
  var stored=invitationRepository.findById(response.getId()).orElseThrow();
  String raw=token(employee);
  assertThat(raw.length()).isEqualTo(43);
  assertThat(stored.getTokenHash().length()).isEqualTo(64);
  assertThat(stored.getTokenHash().equals(raw)).isFalse();
  assertThat(stored.getTokenHash().equals(tokens.hash(raw))).isTrue();
 }
 @Test void tokensHave256BitsAndAreDistinct() {
  Set<String> generated=new HashSet<>();
  for(int i=0;i<1000;i++) generated.add(tokens.generate());
  assertThat(generated.size()).isEqualTo(1000);
  assertThat(generated.stream().allMatch(t->t.matches("[A-Za-z0-9_-]{43}"))).isTrue();
 }
 @Test void publicValidationReturnsLimitedMetadataWithoutAuth() throws Exception {
  invite(employee,CompanyRole.DISPATCHER);
  mvc.perform(post("/company-invitations/validate").contentType(MediaType.APPLICATION_JSON).content(body(tokenBody(token(employee)))))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$.companyName").value("Example Logistics")).andExpect(jsonPath("$.role").value("DISPATCHER"))
   .andExpect(jsonPath("$.carrierCompanyId").doesNotExist()).andExpect(jsonPath("$.tokenHash").doesNotExist());
 }
 @ParameterizedTest @ValueSource(strings={"", "invalid", "../../etc"})
 void invalidTokensReturnSafeResponse(String value) throws Exception {
  mvc.perform(post("/company-invitations/validate").contentType(MediaType.APPLICATION_JSON).content(body(tokenBody(value))))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVITATION_INVALID"))
   .andExpect(jsonPath("$.companyName").doesNotExist());
 }
 @Test void malformedTokenBodyIsNotEchoed() throws Exception {
  mvc.perform(post("/company-invitations/validate").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"sensitive-test"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid invitation request"));
 }
 @Test void unknownWellFormedTokenRejected() {
  assertThatThrownBy(()->invitations.validate(tokens.generate())).extracting("code").isEqualTo("INVITATION_INVALID");
 }
 @Test void expiredInvitationIsVisibleAsExpiredAndCannotBeAccepted() {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  // SQL adjusts both timestamps, preserving expires_at > created_at in real PostgreSQL.
  jdbc.update("update company_invitations set created_at=?, expires_at=? where id=?",
   LocalDateTime.now(ZoneOffset.UTC).minusDays(2),LocalDateTime.now(ZoneOffset.UTC).minusDays(1),sent.getId());
  em.clear();
  assertThatThrownBy(()->invitations.validate(token(employee))).extracting("code").isEqualTo("INVITATION_EXPIRED");
  assertThatThrownBy(()->invitations.accept(token(employee),employee)).extracting("code").isEqualTo("INVITATION_EXPIRED");
  assertThat(invitations.list(companyId,owner,0,20).getContent().getFirst().getStatus()).isEqualTo(InvitationStatus.EXPIRED);
 }
 @Test void revokedInvitationCannotBeAccepted() {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  invitations.revoke(companyId,sent.getId(),owner);
  assertThatThrownBy(()->invitations.accept(token(employee),employee)).extracting("code").isEqualTo("INVITATION_INVALID");
 }
 @Test void existingUserAcceptanceIsAtomicAndCannotReplay() {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  var member=invitations.accept(token(employee),employee);
  assertThat(member.getRole()).isEqualTo(CompanyRole.DISPATCHER);
  assertThat(member.getCarrierCompanyId()).isEqualTo(companyId);
  var stored=invitationRepository.findById(sent.getId()).orElseThrow();
  assertThat(stored.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
  assertThat(stored.getAcceptedAt()).isNotNull();
  assertThat(stored.getAcceptedBy()).isEqualTo(employee.getId());
  assertThatThrownBy(()->invitations.accept(token(employee),employee)).extracting("code").isEqualTo("INVITATION_INVALID");
 }
 @Test void wrongAccountCannotConsumeInvitation() throws Exception {
  invite(employee,CompanyRole.DISPATCHER);
  mvc.perform(post("/company-invitations/accept").header("Authorization",auth(stranger)).contentType(MediaType.APPLICATION_JSON)
    .content(body(tokenBody(token(employee))))).andExpect(status().isForbidden());
  assertThat(memberships.findByCarrierCompanyIdAndUserId(companyId,stranger.getId())).isEmpty();
 }
 @Test void acceptanceEmailIsCaseInsensitive() {
  invite(employee,CompanyRole.DISPATCHER);
  String raw=token(employee);
  employee.setEmail(employee.getEmail().toUpperCase(Locale.ROOT));
  users.saveAndFlush(employee);
  assertThat(invitations.accept(raw,employee).getUserId()).isEqualTo(employee.getId());
 }
 @Test void clientCannotChooseCompanyOrRoleDuringAcceptance() throws Exception {
  invite(employee,CompanyRole.DISPATCHER);
  mvc.perform(post("/company-invitations/accept").header("Authorization",auth(employee)).contentType(MediaType.APPLICATION_JSON)
    .content(body(Map.of("token",token(employee),"carrierCompanyId",9999,"role","CARRIER_OWNER"))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DISPATCHER"))
   .andExpect(jsonPath("$.carrierCompanyId").value(companyId));
 }
 @Test void newUserCanRegisterThenAcceptWithoutNewAuthModel() throws Exception {
  String emailAddress=UUID.randomUUID()+"@example.test";
  invitations.invite(companyId,request(emailAddress,CompanyRole.FLEET_MANAGER),owner);
  String raw=links.get(emailAddress).split("#token=")[1];
  var register=Map.of("fullName","New employee","email",emailAddress,"phone","+2519"+String.format("%08d",new Random().nextInt(100000000)),
    "password","Test-only-password!","role","shipper");
  var result=mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(register))).andExpect(status().isCreated()).andReturn();
  String accessToken=json.readTree(result.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/company-invitations/accept").header("Authorization","Bearer "+accessToken).contentType(MediaType.APPLICATION_JSON)
    .content(body(tokenBody(raw)))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("FLEET_MANAGER"));
 }
 @ParameterizedTest @EnumSource(value=CompanyRole.class,names={"FLEET_MANAGER","DISPATCHER"})
 void ownerCanChangeEmployeeRole(CompanyRole role) throws Exception {
  var member=join();
  teams.role(companyId,member.getId(),role == CompanyRole.FLEET_MANAGER ? CompanyRole.DISPATCHER : CompanyRole.FLEET_MANAGER,owner);
  mvc.perform(patch(team()+"/members/"+member.getId()+"/role").header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON)
    .content(body(Map.of("role",role.name())))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name()))
    .andExpect(jsonPath("$.roleChangedBy").value(owner.getId()));
 }
 @Test void employeeCannotChangeOwnRoleOrInviteOthers() throws Exception {
  var member=join();
  mvc.perform(patch(team()+"/members/"+member.getId()+"/role").header("Authorization",auth(employee)).contentType(MediaType.APPLICATION_JSON)
    .content("{\"role\":\"DISPATCHER\"}")).andExpect(status().isForbidden());
  mvc.perform(post(team()+"/invitations").header("Authorization",auth(employee)).contentType(MediaType.APPLICATION_JSON)
    .content(body(request(stranger.getEmail(),CompanyRole.DISPATCHER)))).andExpect(status().isForbidden());
 }
 @Test void cannotPromoteEmployeeToOwner() {
  var member=join();
  assertThatThrownBy(()->teams.role(companyId,member.getId(),CompanyRole.CARRIER_OWNER,owner)).isInstanceOf(CompanyTeamException.class);
 }
 @Test void ownerCannotBeDemotedDeactivatedOrRemoved() {
  var member=memberships.findByCarrierCompanyIdAndUserId(companyId,owner.getId()).orElseThrow();
  assertThatThrownBy(()->teams.role(companyId,member.getId(),CompanyRole.DISPATCHER,owner)).isInstanceOf(CompanyTeamException.class);
  assertThatThrownBy(()->teams.status(companyId,member.getId(),MembershipStatus.INACTIVE,owner)).isInstanceOf(CompanyTeamException.class);
  assertThatThrownBy(()->teams.status(companyId,member.getId(),MembershipStatus.REMOVED,owner)).isInstanceOf(CompanyTeamException.class);
 }
 @Test void deactivateReactivateRemoveOnlyAffectMembership() throws Exception {
  var member=join();
  mvc.perform(post(team()+"/members/"+member.getId()+"/deactivate").header("Authorization",auth(owner)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
  assertThat(users.findById(employee.getId()).orElseThrow().getIsActive()).isTrue();
  mvc.perform(post(team()+"/members/"+member.getId()+"/activate").header("Authorization",auth(owner)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
  mvc.perform(delete(team()+"/members/"+member.getId()).header("Authorization",auth(owner)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REMOVED"));
  assertThat(users.existsById(employee.getId())).isTrue();
  assertThat(memberships.existsById(member.getId())).isTrue();
  assertThatThrownBy(()->teams.status(companyId,member.getId(),MembershipStatus.ACTIVE,owner)).isInstanceOf(CompanyTeamException.class);
 }
 @Test void removedEmployeeCanOnlyRejoinWithNewInvitation() {
  var member=join();
  teams.status(companyId,member.getId(),MembershipStatus.REMOVED,owner);
  invite(employee,CompanyRole.DISPATCHER);
  var rejoined=invitations.accept(token(employee),employee);
  assertThat(rejoined.getId()).isEqualTo(member.getId());
  assertThat(rejoined.getRole()).isEqualTo(CompanyRole.DISPATCHER);
 }
 @Test void ownerListsMembersAndUserSeesOwnActiveMemberships() throws Exception {
  join();
  mvc.perform(get(team()+"/members").header("Authorization",auth(owner))).andExpect(status().isOk())
   .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].email").value(employee.getEmail()));
  mvc.perform(get("/company-memberships/me").header("Authorization",auth(employee))).andExpect(status().isOk())
   .andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void memberIdCannotCrossCompanyBoundary() {
  var member=join();
  Long another=company(owner,true);
  assertThatThrownBy(()->teams.role(another,member.getId(),CompanyRole.DISPATCHER,owner)).extracting("httpStatus").isEqualTo(403);
 }
 @Test void multiCompanyMembershipIsAllowed() {
  join();
  companyId=company(stranger,true); owner=stranger;
  assertThat(join().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
  assertThat(teams.mine(employee,0,20).getTotalElements()).isEqualTo(2);
 }
 @Test void ownerCanListRevokeAndResend() throws Exception {
  var original=invite(employee,CompanyRole.DISPATCHER);
  String old=token(employee);
  mvc.perform(get(team()+"/invitations").header("Authorization",auth(owner))).andExpect(status().isOk())
   .andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(post(team()+"/invitations/"+original.getId()+"/resend").header("Authorization",auth(owner))).andExpect(status().isOk());
  assertThat(token(employee).equals(old)).isFalse();
  assertThatThrownBy(()->invitations.validate(old)).isInstanceOf(CompanyTeamException.class);
  var newest=invitations.list(companyId,owner,0,20).getContent().getFirst();
  mvc.perform(post(team()+"/invitations/"+newest.getId()+"/revoke").header("Authorization",auth(owner))).andExpect(status().isOk());
 }
 @ParameterizedTest @ValueSource(strings={"resend","revoke"})
 void unauthorizedInvitationManagementDenied(String action) throws Exception {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  mvc.perform(post(team()+"/invitations/"+sent.getId()+"/"+action).header("Authorization",auth(stranger))).andExpect(status().isForbidden());
 }
 @Test void invitationIdCannotCrossCompanyBoundary() {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  Long another=company(owner,true);
  assertThatThrownBy(()->invitations.resend(another,sent.getId(),owner)).extracting("httpStatus").isEqualTo(403);
  assertThatThrownBy(()->invitations.revoke(another,sent.getId(),owner)).extracting("httpStatus").isEqualTo(403);
 }
 @Test void cooldownPreventsSpamAndDoesNotSendAgain() {
  var sent=invite(employee,CompanyRole.DISPATCHER);
  properties.setCooldownSeconds(60);
  try {
   assertThatThrownBy(()->invitations.resend(companyId,sent.getId(),owner)).extracting("httpStatus").isEqualTo(429);
   verify(email,times(1)).sendInvitation(anyString(),anyString(),any(),anyString(),any());
  } finally { properties.setCooldownSeconds(0); }
 }
 @Test void hourlyLimitCoversDifferentRecipients() {
  invite(employee,CompanyRole.DISPATCHER);
  properties.setMaxPerHour(1);
  try { assertThatThrownBy(()->invite(stranger,CompanyRole.DISPATCHER)).extracting("httpStatus").isEqualTo(429); }
  finally { properties.setMaxPerHour(100); }
 }
 @Test void frontendUrlMustBeSecureOrLocal() {
  String previous=properties.getFrontendBaseUrl();
  properties.setFrontendBaseUrl("http://public.example.test");
  try { assertThatThrownBy(()->invite(employee,CompanyRole.DISPATCHER)).extracting("code").isEqualTo("INVITATION_EMAIL_FAILED"); }
  finally { properties.setFrontendBaseUrl(previous); }
 }
}
