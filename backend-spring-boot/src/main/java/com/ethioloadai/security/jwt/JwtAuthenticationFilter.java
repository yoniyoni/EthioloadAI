package com.ethioloadai.security.jwt;

import com.ethioloadai.config.JwtConfig;
import com.ethioloadai.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        System.out.println("JWT_FILTER: Request URI: " + request.getRequestURI());
        System.out.println("JWT_FILTER: Request Method: " + request.getMethod());
        
        String jwt = extractJwtFromRequest(request);
        System.out.println("JWT_FILTER: Extracted JWT: " + (jwt != null ? jwt.substring(0, Math.min(20, jwt.length())) + "..." : "null"));
        
        if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
            System.out.println("JWT_FILTER: JWT is valid");
            String username = jwtService.extractUsername(jwt);
            System.out.println("JWT_FILTER: Extracted username: " + username);
            
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            System.out.println("JWT_FILTER: Loaded UserDetails: " + userDetails.getUsername());
            
            // Check if user account is enabled (active)
            if (!userDetails.isEnabled()) {
                System.out.println("JWT_FILTER: User account is inactive, rejecting authentication");
                throw new org.springframework.security.authentication.DisabledException("User account is inactive");
            }
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
            
            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            System.out.println("JWT_FILTER: Set authentication in SecurityContext");
        } else {
            System.out.println("JWT_FILTER: JWT validation failed or JWT is null/empty");
        }
        
        System.out.println("JWT_FILTER: SecurityContext authentication before controller: " + SecurityContextHolder.getContext().getAuthentication());
        
        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtConfig.getHeader());
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtConfig.getPrefix())) {
            return bearerToken.substring(jwtConfig.getPrefix().length()).stripLeading();
        }
        
        return null;
    }
}
