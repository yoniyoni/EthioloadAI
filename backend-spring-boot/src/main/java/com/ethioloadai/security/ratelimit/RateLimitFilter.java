package com.ethioloadai.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Placeholder rate limiting filter.
 * 
 * TODO: Reintroduce configurable Bucket4j rate limiting in a later milestone.
 * Current implementation passes through all requests without rate limiting.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Placeholder: No rate limiting implemented
        // All requests pass through
        filterChain.doFilter(request, response);
    }
}
