package com.ethioloadai.websocket;

import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import com.ethioloadai.websocket.security.WebSocketAuditService;
import com.ethioloadai.websocket.security.WebSocketAuthorizationService;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.ethioloadai.websocket.service.WebSocketSessionManagerImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConnectionLifecycleTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private WebSocketRateLimitService rateLimitService;
    @Mock private WebSocketAuthorizationService authorizationService;
    @Mock private WebSocketAuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private WebSocketSession session;

    @InjectMocks
    private WebSocketSessionManagerImpl sessionManager;

    @Test
    void unregisteringSessionReleasesItsConnectionLimitSlot() {
        when(session.getId()).thenReturn("websocket-session");
        when(session.getAttributes()).thenReturn(Map.of(
                WebSocketAuthenticationInterceptor.RATE_LIMIT_CONNECTION_ID_ATTR, "rate-limit-connection"));

        sessionManager.unregisterSession(session);

        verify(rateLimitService).releaseConnection("rate-limit-connection");
    }
}
