package com.ethioloadai.security.proxy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OriginProxyGuardFilterTest {
    private static final String SECRET = "proxy-secret-that-is-at-least-32-bytes";

    @Test
    void rejectsMissingOrIncorrectProxySecretAndIgnoresSpoofedClientIp() throws Exception {
        MockHttpServletRequest missingSecret = request("/api/auth/login");
        missingSecret.addHeader(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_HEADER, "203.0.113.9");
        missingSecret.addHeader("X-Forwarded-For", "198.51.100.77");
        MockHttpServletResponse missingResponse = apply(missingSecret, new OriginProxyGuardFilter(true, SECRET));

        MockHttpServletRequest wrongSecret = request("/api/auth/login");
        wrongSecret.addHeader(OriginProxyGuardFilter.PROXY_SECRET_HEADER, "wrong");
        wrongSecret.addHeader(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_HEADER, "203.0.113.9");
        MockHttpServletResponse wrongResponse = apply(wrongSecret, new OriginProxyGuardFilter(true, SECRET));

        assertThat(missingResponse.getStatus()).isEqualTo(403);
        assertThat(wrongResponse.getStatus()).isEqualTo(403);
        assertThat(missingSecret.getAttribute(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_ATTRIBUTE)).isNull();
    }

    @Test
    void acceptsOnlyAuthenticatedProxyIpAndRequiresValidIpForAuthRateLimits() throws Exception {
        MockHttpServletRequest request = request("/api/auth/register");
        request.addHeader(OriginProxyGuardFilter.PROXY_SECRET_HEADER, SECRET);
        request.addHeader(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_HEADER, "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.77");
        AtomicBoolean continued = new AtomicBoolean();
        MockHttpServletResponse response = apply(request, new OriginProxyGuardFilter(true, SECRET),
                (req, res) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(continued).isTrue();
        assertThat(TrustedClientAddress.resolve(request)).isEqualTo("203.0.113.9");

        MockHttpServletRequest spoofed = request("/api/auth/login");
        spoofed.addHeader(OriginProxyGuardFilter.PROXY_SECRET_HEADER, SECRET);
        spoofed.addHeader(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_HEADER, "198.51.100.4, 203.0.113.9");
        assertThat(apply(spoofed, new OriginProxyGuardFilter(true, SECRET)).getStatus()).isEqualTo(403);
    }

    @Test
    void allowsOnlyHealthAndJwtProtectedWebSocketHandshakesWithoutProxySecret() throws Exception {
        MockHttpServletResponse health = apply(request("/api/actuator/health"),
                new OriginProxyGuardFilter(true, SECRET));
        MockHttpServletResponse websocket = apply(request("/api/ws/location"),
                new OriginProxyGuardFilter(true, SECRET));
        MockHttpServletResponse regularApi = apply(request("/api/freight"),
                new OriginProxyGuardFilter(true, SECRET));

        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(websocket.getStatus()).isEqualTo(200);
        assertThat(regularApi.getStatus()).isEqualTo(403);
    }

    @Test
    void failsClosedWhenRequiredSecretIsMissingOrTooShort() throws Exception {
        MockHttpServletResponse response = apply(request("/api/freight"),
                new OriginProxyGuardFilter(true, ""));
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void leavesLocalDevelopmentRequestsUnchangedWhenProxyGuardIsDisabled() throws Exception {
        MockHttpServletResponse response = apply(request("/api/freight"),
                new OriginProxyGuardFilter(false, ""));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String uri) {
        String method = uri.endsWith("/login") || uri.endsWith("/register") ? "POST" : "GET";
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setContextPath("/api");
        return request;
    }

    private static MockHttpServletResponse apply(MockHttpServletRequest request,
                                                 OriginProxyGuardFilter filter) throws IOException, jakarta.servlet.ServletException {
        return apply(request, filter, (req, res) -> {});
    }

    private static MockHttpServletResponse apply(MockHttpServletRequest request,
                                                 OriginProxyGuardFilter filter,
                                                 FilterChain chain) throws IOException, jakarta.servlet.ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
