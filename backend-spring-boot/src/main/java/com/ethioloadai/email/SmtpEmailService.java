package com.ethioloadai.email;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service @RequiredArgsConstructor
public class SmtpEmailService implements EmailService {
 private final JavaMailSender sender;
 private final MailProperties mail;
 private final InvitationProperties invitations;

 @Override public void sendInvitation(String recipient, String companyName, CompanyRole role, String url, LocalDateTime expiresAt) {
  if (blank(mail.getUsername()) || blank(mail.getPassword()) || blank(invitations.getEmailFrom())) {
   throw CompanyTeamException.mail();
  }
  try {
   var message = sender.createMimeMessage();
   var helper = new MimeMessageHelper(message, true, "UTF-8");
   helper.setFrom(invitations.getEmailFrom());
   helper.setTo(recipient);
   String safeName = companyName.replaceAll("[\\r\\n]", " ");
   helper.setSubject("You're invited to join " + safeName + " on Freight-Link");
   String roleName = role.name().replace('_', ' ');
   String expiration = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " UTC";
   String text = "You have been invited to join " + safeName + " on Freight-Link as " + roleName
     + ".\nSign in or create an account using this email address, then accept your invitation.\n"
     + "Accept invitation: " + url + "\nExpires: " + expiration
     + "\nIf you were not expecting this invitation, please ignore this email.";
   String html = "<h2>Join " + esc(safeName) + " on Freight-Link</h2><p>You are invited as <strong>"
     + esc(roleName) + "</strong>.</p><p>Sign in or create an account using this email address to join.</p>"
     + "<p><a href=\"" + esc(url) + "\">Accept Invitation</a></p><p>Expires: " + esc(expiration)
     + "</p><p>If you were not expecting this invitation, please ignore this email.</p>";
   helper.setText(text, html);
   sender.send(message);
  } catch (Exception ex) {
   // Never expose/log provider messages: they can contain credentials or invitation URLs.
   throw CompanyTeamException.mail();
  }
 }
 private boolean blank(String value) { return value == null || value.isBlank(); }
 private String esc(String value) { return HtmlUtils.htmlEscape(value); }
}

