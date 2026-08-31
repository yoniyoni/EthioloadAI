package com.ethioloadai.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Placeholder rate limiting filter.
 * 
 * TODO: Reintroduce configurable Bucket4j rate limiting in a later milestone.
 * Current implementation passes through all requests without rate limiting.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Placeholder: No rate limiting implemented
        // All requests pass through
        filterChain.doFilter(request, response);
    }
}
