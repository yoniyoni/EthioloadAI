package com.ethioloadai.websocket.interceptor;

import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.security.WebSocketAuditService;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.ethioloadai.websocket.security.WebSocketSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketClientAddressTest {

    @Test
    void ignoresUntrustedForwardingHeadersAndUsesSocketPeerAddress() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.200");
        headers.add("X-Real-IP", "203.0.113.201");
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.20", 443));

        assertEquals("192.0.2.20", WebSocketAuthenticationInterceptor.getClientIpAddress(request));
    }

    @Test
    void missingSocketPeerAddressUsesSharedConservativeKey() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        assertEquals("unknown", WebSocketAuthenticationInterceptor.getClientIpAddress(request));
    }

    @Test
    void websocketHandshakeStillRejectsMissingJwtWithoutTheHttpProxySecret() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        WebSocketAuthenticationInterceptor interceptor = interceptor(jwtService);
        ServerHttpRequest request = request("wss://api.example.test/api/ws/location");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new java.util.HashMap<>()));
        verify(jwtService, never()).isTokenValid(anyString());
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void websocketHandshakeStillRejectsInvalidJwtWithoutTheHttpProxySecret() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.isTokenValid("invalid-token")).thenReturn(false);
        WebSocketAuthenticationInterceptor interceptor = interceptor(jwtService);
        ServerHttpRequest request = request("wss://api.example.test/api/ws/location?token=invalid-token");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        assertFalse(interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new java.util.HashMap<>()));
        verify(jwtService).isTokenValid("invalid-token");
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private static WebSocketAuthenticationInterceptor interceptor(JwtService jwtService) {
        return new WebSocketAuthenticationInterceptor(
                jwtService,
                mock(UserRepository.class),
                new WebSocketSecurityConfig(),
                mock(WebSocketRateLimitService.class),
                mock(WebSocketAuditService.class));
    }

    private static ServerHttpRequest request(String uri) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("192.0.2.20", 443));
        return request;
    }
}
