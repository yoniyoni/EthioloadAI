package com.ethioloadai.security.jwt;

import com.ethioloadai.config.JwtConfig;
import com.ethioloadai.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Debug-level: method + URI — never logged in production (level is INFO+)
        log.debug("JWT filter processing: {} {}", request.getMethod(), request.getRequestURI());

        String jwt = extractJwtFromRequest(request);

        if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
            String username = jwtService.extractUsername(jwt);
            // Log the resolved username only at DEBUG. Never log the token itself.
            log.debug("Valid JWT resolved username: {}", username);

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Reject disabled (inactive) accounts even if they hold a valid token.
            if (!userDetails.isEnabled()) {
                log.warn("Authentication rejected — account inactive for user: {}", username);
                throw new org.springframework.security.authentication.DisabledException(
                        "User account is inactive");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        ((com.ethioloadai.security.model.UserDetailsImpl) userDetails).getUser(),
                            null,
                            userDetails.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("SecurityContext populated for user: {}", username);
        } else if (StringUtils.hasText(jwt)) {
            // Token was present but failed validation (expired, tampered, wrong key, etc.)
            log.debug("JWT present but failed validation for: {} {}", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtConfig.getHeader());

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtConfig.getPrefix())) {
            // Strip the "Bearer " prefix (plus any accidental leading whitespace)
            return bearerToken.substring(jwtConfig.getPrefix().length()).stripLeading();
        }

        return null;
    }
}
