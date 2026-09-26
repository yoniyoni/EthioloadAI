package com.ethioloadai.carrier.team;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompanyTeamTransactionTest extends TeamTestSupport {
 @AfterEach void cleanup() {
  new TransactionTemplate(transactions).executeWithoutResult(status-> {
   invitationRepository.deleteAll(invitationRepository.findByCarrierCompanyId(companyId,org.springframework.data.domain.Pageable.unpaged()).getContent());
   memberships.deleteAll(memberships.findByCarrierCompanyId(companyId,org.springframework.data.domain.Pageable.unpaged()).getContent());
   companies.deleteById(companyId);
   companies.flush();
   users.deleteAllById(List.of(owner.getId(),employee.getId(),stranger.getId(),admin.getId()));
  });
 }
 @Test void failedEmailRollsBackNewInvitationAndReturnsSanitizedError() {
  doThrow(CompanyTeamException.mail()).when(email).sendInvitation(anyString(),anyString(),any(),anyString(),any());
  assertThatThrownBy(()->invite(employee,CompanyRole.DISPATCHER)).extracting("code").isEqualTo("INVITATION_EMAIL_FAILED");
  assertThat(invitationRepository.findByCarrierCompanyId(companyId,org.springframework.data.domain.Pageable.unpaged())).isEmpty();
 }
 @Test void failedResendPreservesOldPendingToken() {
  var original=invite(employee,CompanyRole.DISPATCHER);
  String old=token(employee);
  doThrow(CompanyTeamException.mail()).when(email).sendInvitation(anyString(),anyString(),any(),anyString(),any());
  assertThatThrownBy(()->invitations.resend(companyId,original.getId(),owner)).isInstanceOf(CompanyTeamException.class);
  assertThat(invitations.validate(old).valid()).isTrue();
  assertThat(invitationRepository.findByCarrierCompanyId(companyId,org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(1);
 }
 @Test void acceptanceRollsBackMembershipAndTokenTogether() {
  var original=invite(employee,CompanyRole.DISPATCHER);
  String raw=token(employee);
  assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
   invitations.accept(raw,employee);
   throw new IllegalStateException("simulated later transaction failure");
  })).isInstanceOf(IllegalStateException.class);
  assertThat(memberships.findByCarrierCompanyIdAndUserId(companyId,employee.getId())).isEmpty();
  assertThat(invitationRepository.findById(original.getId()).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
 }
 @Test void concurrentAcceptanceHasExactlyOneWinner() throws Exception {
  invite(employee,CompanyRole.DISPATCHER);
  String raw=token(employee);
  List<Boolean> result=race(()->{invitations.accept(raw,employee);return true;},()->{invitations.accept(raw,employee);return true;});
  assertThat(result.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
  assertThat(memberships.findByCarrierCompanyId(companyId,org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(2);
 }
 @ParameterizedTest @ValueSource(strings={"resend","revoke"})
 void acceptRacingTokenManagementIsSerialized(String operation) throws Exception {
  var original=invite(employee,CompanyRole.DISPATCHER);
  String raw=token(employee);
  var results=race(()->{invitations.accept(raw,employee);return true;},()->{
   if(operation.equals("resend")) invitations.resend(companyId,original.getId(),owner);
   else invitations.revoke(companyId,original.getId(),owner);
   return true;
  });
  assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
  assertThatThrownBy(()->invitations.accept(raw,employee)).isInstanceOf(CompanyTeamException.class);
 }
 @Test void concurrentDuplicateInvitesLeaveOnePendingToken() throws Exception {
  var results=race(()->{invite(employee,CompanyRole.DISPATCHER);return true;},()->{invite(employee,CompanyRole.DISPATCHER);return true;});
  assertThat(results).containsOnly(true);
  assertThat(invitationRepository.findByCarrierCompanyIdAndInvitedEmailAndStatus(companyId,employee.getEmail(),InvitationStatus.PENDING)).hasSize(1);
 }
 private List<Boolean> race(Callable<Boolean> a, Callable<Boolean> b) throws Exception {
  try(var executor=Executors.newFixedThreadPool(2)) {
   CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
   List<Future<Boolean>> futures=new ArrayList<>();
   for(var action:List.of(a,b)) futures.add(executor.submit(()->{
    ready.countDown();
    if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
    try { return action.call(); }
    catch(CompanyTeamException ex) { return false; }
   }));
   assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
   return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
  }
 }
}

