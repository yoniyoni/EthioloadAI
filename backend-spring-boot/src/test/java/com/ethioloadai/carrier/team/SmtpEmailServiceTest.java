package com.ethioloadai.carrier.team;
import com.ethioloadai.email.SmtpEmailService;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.LocalDateTime;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailServiceTest {
 JavaMailSender sender;
 MailProperties mail;
 InvitationProperties config;
 SmtpEmailService service;
 MimeMessage message;
 @BeforeEach void setup() {
  sender=mock(JavaMailSender.class);
  mail=new MailProperties();
  mail.setUsername("test-user"); mail.setPassword("test-only-secret");
  config=new InvitationProperties(); config.setEmailFrom("sender@example.test");
  message=new MimeMessage(Session.getInstance(new Properties()));
  when(sender.createMimeMessage()).thenReturn(message);
  service=new SmtpEmailService(sender,mail,config);
 }
 @Test void emailHasRecipientCompanyRoleExpirationAndPlainTextAndHtml() throws Exception {
  String url="https://example.test/invitations/accept#token=test-only-value";
  service.sendInvitation("recipient@example.test","ABC <Logistics>",CompanyRole.FLEET_MANAGER,url,LocalDateTime.of(2026,10,1,12,0));
  verify(sender).send(message);
  assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.test");
  assertThat(message.getSubject()).contains("ABC <Logistics>","Freight-Link");
  String content=content(message);
  assertThat(content).contains("FLEET MANAGER","2026-10-01T12:00","UTC","ignore","ABC &lt;Logistics&gt;","Accept Invitation");
  assertThat(content.contains(url)).isTrue();
  assertThat(content.contains(mail.getPassword())).isFalse();
 }
 @Test void providerErrorsNeverEscape() {
  doThrow(new MailSendException("internal provider details with secret")).when(sender).send(message);
  assertThatThrownBy(()->service.sendInvitation("recipient@example.test","ABC",CompanyRole.DISPATCHER,"https://example.test",LocalDateTime.now()))
   .isInstanceOf(CompanyTeamException.class).hasMessage("Invitation email could not be sent. Please try again later.")
   .hasNoCause();
 }
 @Test void missingConfigurationFailsWithoutContactingSmtp() {
  mail.setPassword("");
  assertThatThrownBy(()->service.sendInvitation("recipient@example.test","ABC",CompanyRole.DISPATCHER,"https://example.test",LocalDateTime.now()))
   .isInstanceOf(CompanyTeamException.class);
  verifyNoInteractions(sender);
 }
 @Test void missingUsernameOrSenderFailsWithoutContactingSmtp() {
  mail.setUsername("");
  assertThatThrownBy(()->service.sendInvitation("recipient@example.test","ABC",CompanyRole.DISPATCHER,"https://example.test",LocalDateTime.now()))
   .isInstanceOf(CompanyTeamException.class);
  verifyNoInteractions(sender);

  mail.setUsername("test-user");
  config.setEmailFrom("");
  assertThatThrownBy(()->service.sendInvitation("recipient@example.test","ABC",CompanyRole.DISPATCHER,"https://example.test",LocalDateTime.now()))
   .isInstanceOf(CompanyTeamException.class);
  verifyNoInteractions(sender);
 }
 @Test void blankSmtpConfigurationFailsWithoutContactingSmtp() {
  mail.setUsername(" ");
  mail.setPassword("");
  config.setEmailFrom("\t");
  assertThatThrownBy(()->service.sendInvitation("recipient@example.test","ABC",CompanyRole.DISPATCHER,"https://example.test",LocalDateTime.now()))
   .isInstanceOf(CompanyTeamException.class);
  verifyNoInteractions(sender);
 }
 String content(Part part) throws Exception {
  Object value=part.getContent();
  if(value instanceof Multipart parts) {
   StringBuilder out=new StringBuilder();
   for(int i=0;i<parts.getCount();i++) out.append(content(parts.getBodyPart(i)));
   return out.toString();
  }
  return value.toString();
 }
}
