package com.ethioloadai.carrier.team;

import com.ethioloadai.carrier.team.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class InvitationRegistrationTest extends TeamTestSupport {
 String address;
 String inviteNew(CompanyRole role) {
  address = UUID.randomUUID()+"@example.test";
  invitations.invite(companyId, request(address,role), owner);
  return links.get(address).split("#token=")[1];
 }
 Map<String,String> registration(String token) {
  return Map.of("token",token,"fullName","New Employee","phone","+251"+String.format("%09d",new Random().nextInt(1000000000)),"password","new-password");
 }
 @ParameterizedTest @EnumSource(value=CompanyRole.class,names={"FLEET_MANAGER","DISPATCHER"})
 void createsAccountAcceptsAssignedRoleAndReturnsWorkingSession(CompanyRole role) throws Exception {
  String raw=inviteNew(role);
  mvc.perform(post("/company-invitations/validate").contentType(MediaType.APPLICATION_JSON).content(body(tokenBody(raw))))
   .andExpect(jsonPath("$.accountExists").value(false));
  var payload=new HashMap<>(registration(raw));
  payload.put("email","attacker@example.test"); payload.put("role","ADMIN"); payload.put("carrierCompanyId","99999");
  var response=mvc.perform(post("/company-invitations/register").contentType(MediaType.APPLICATION_JSON).content(body(payload)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.session.user.email").value(address))
   .andExpect(jsonPath("$.membership.carrierCompanyId").value(companyId))
   .andExpect(jsonPath("$.membership.role").value(role.name())).andReturn();
  String jwt=json.readTree(response.getResponse().getContentAsString()).path("session").path("token").asText();
  mvc.perform(get("/company-memberships/me").header("Authorization","Bearer "+jwt))
   .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].role").value(role.name()));
  var user=users.findByEmail(address).orElseThrow();
  assertThat(user.getPassword()).isNotEqualTo("new-password");
  mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("identifier",address,"password","new-password"))))
   .andExpect(status().isOk());
  mvc.perform(post("/company-invitations/register").contentType(MediaType.APPLICATION_JSON).content(body(payload)))
   .andExpect(status().isBadRequest());
 }
 @Test void existingAccountCannotBeOverwritten() throws Exception {
  invite(employee,CompanyRole.DISPATCHER);
  mvc.perform(post("/company-invitations/register").contentType(MediaType.APPLICATION_JSON).content(body(registration(token(employee)))))
   .andExpect(status().isUnprocessableEntity());
  assertThat(users.findById(employee.getId()).orElseThrow().getPassword()).isEqualTo("unused");
  assertThat(invitations.validate(token(employee)).accountExists()).isTrue();
 }
 @Test void revokedInvitationDoesNotCreateAccount() throws Exception {
  String raw=inviteNew(CompanyRole.FLEET_MANAGER);
  var invitation=invitationRepository.findByTokenHash(tokens.hash(raw)).orElseThrow();
  invitations.revoke(companyId,invitation.getId(),owner);
  mvc.perform(post("/company-invitations/register").contentType(MediaType.APPLICATION_JSON).content(body(registration(raw))))
   .andExpect(status().isBadRequest());
  assertThat(users.existsByEmail(address)).isFalse();
 }
 @Test void weakPasswordDoesNotConsumeInvitation() throws Exception {
  String raw=inviteNew(CompanyRole.DISPATCHER);
  var payload=new HashMap<>(registration(raw)); payload.put("password","123");
  mvc.perform(post("/company-invitations/register").contentType(MediaType.APPLICATION_JSON).content(body(payload)))
   .andExpect(status().isUnprocessableEntity());
  assertThat(users.existsByEmail(address)).isFalse();
  assertThat(invitations.validate(raw).valid()).isTrue();
 }
}
