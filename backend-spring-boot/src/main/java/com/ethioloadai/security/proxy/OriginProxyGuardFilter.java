package com.ethioloadai.security.proxy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public final class OriginProxyGuardFilter extends OncePerRequestFilter {
    public static final String PROXY_SECRET_HEADER = "X-EthioLoad-Proxy-Secret";
    public static final String TRUSTED_CLIENT_IP_HEADER = "X-Trusted-Client-IP";
    public static final String TRUSTED_CLIENT_IP_ATTRIBUTE =
            OriginProxyGuardFilter.class.getName() + ".trustedClientIp";

    private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");
    private final boolean required;
    private final byte[] expectedSecret;

    public OriginProxyGuardFilter(boolean required, String expectedSecret) {
        this.required = required;
        this.expectedSecret = expectedSecret == null
                ? new byte[0]
                : expectedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!required) {
            filterChain.doFilter(request, response);
            return;
        }

        if (expectedSecret.length < 32) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        String suppliedSecret = request.getHeader(PROXY_SECRET_HEADER);
        if (suppliedSecret == null || !MessageDigest.isEqual(expectedSecret,
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String applicationPath = applicationPath(request);
        String trustedClientIp = request.getHeader(TRUSTED_CLIENT_IP_HEADER);
        if (requiresClientIp(request.getMethod(), applicationPath)) {
            trustedClientIp = normalizeIp(trustedClientIp);
            if (trustedClientIp == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } else {
            trustedClientIp = normalizeIp(trustedClientIp);
        }
        if (trustedClientIp != null) {
            request.setAttribute(TRUSTED_CLIENT_IP_ATTRIBUTE, trustedClientIp);
        }

        filterChain.doFilter(request, response);
    }

    private static String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private static boolean requiresClientIp(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) return false;
        return "/auth/login".equals(path)
                || "/auth/register".equals(path)
                || "/login".equals(path)
                || "/register".equals(path)
                || "/company-invitations/register".equals(path);
    }

    private static String normalizeIp(String value) {
        if (value == null || value.isBlank() || value.contains(",")) return null;
        String candidate = value.trim();
        if (IPV4.matcher(candidate).matches()) {
            String[] octets = candidate.split("\\.");
            for (String octet : octets) {
                if (Integer.parseInt(octet) > 255) return null;
            }
        } else if (!IPV6.matcher(candidate).matches() || !candidate.contains(":")) {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) return null;
            return address.getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static boolean isPublicExceptionPath(String path) {
        return "/actuator/health".equals(path)
                || "/ws".equals(path)
                || path.startsWith("/ws/")
                || "/api/ws".equals(path)
                || path.startsWith("/api/ws/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPublicExceptionPath(applicationPath(request));
    }
}
