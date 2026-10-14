package com.ethioloadai.security.jwt;

import com.ethioloadai.config.JwtConfig;
import com.ethioloadai.security.token.RevokedToken;
import com.ethioloadai.security.token.RevokedTokenRepository;
import com.ethioloadai.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final RevokedTokenRepository revokedTokenRepository;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Boolean extractVerificationStatus(String token) {
        return extractClaim(token, claims -> claims.get("verification_status", Boolean.class));
    }

    public Boolean extractIsActive(String token) {
        return extractClaim(token, claims -> claims.get("is_active", Boolean.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("verification_status", user.getVerificationStatus());
        extraClaims.put("is_active", user.getIsActive());
        
        String username = user.getEmail() != null ? user.getEmail() : user.getPhone();
        return generateToken(extraClaims, username);
    }

    public String generateToken(Map<String, Object> extraClaims, String username) {
        return Jwts
                .builder()
                .claims(extraClaims)
            .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !revokedTokenRepository.existsById(hashToken(token));
        } catch (Exception e) {
            return false;
        }
    }

    public void revokeToken(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            if (expiration == null || !expiration.after(new Date())) {
                return;
            }

            revokedTokenRepository.deleteByExpiresAtBefore(java.time.LocalDateTime.now());
            String tokenHash = hashToken(token);
            if (!revokedTokenRepository.existsById(tokenHash)) {
                revokedTokenRepository.save(new RevokedToken(
                        tokenHash,
                        expiration.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to revoke token", e);
        }
    }

    /** Alias used by WebSocket interceptor. */
    public boolean isTokenValid(String token) {
        return validateToken(token);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Key getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
