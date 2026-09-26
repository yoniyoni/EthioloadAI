package com.ethioloadai.carrier.team.service;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import org.springframework.stereotype.Component;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class InvitationTokens {
 private final SecureRandom random = new SecureRandom();
 public String generate() {
  byte[] bytes = new byte[32];
  random.nextBytes(bytes);
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
 }
 public String hash(String token) {
  if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw CompanyTeamException.invalidToken();
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
  catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
 }
}

