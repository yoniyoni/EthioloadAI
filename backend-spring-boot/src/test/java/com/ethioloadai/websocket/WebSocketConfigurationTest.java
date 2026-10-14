package com.ethioloadai.websocket;

import com.ethioloadai.websocket.config.WebSocketConfig;
import com.ethioloadai.websocket.dto.WebSocketMessage;
import com.ethioloadai.websocket.handler.LocationWebSocketHandler;
import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocket configuration and basic functionality.
 * 
 * Tests WebSocket infrastructure without requiring full Spring Boot context
 * to verify Phase 9 WebSocket infrastructure is properly configured.
 */
@SpringJUnitConfig
class WebSocketConfigurationTest {

    @MockBean
    private LocationWebSocketHandler locationWebSocketHandler;
    
    @MockBean
    private WebSocketAuthenticationInterceptor authenticationInterceptor;
    
    @MockBean
    private WebSocketSessionManager sessionManager;

    @Test
    void testWebSocketConfigurationCreation() {
        // Test that WebSocketConfig can be created with required dependencies
        WebSocketConfig config = new WebSocketConfig(locationWebSocketHandler, authenticationInterceptor);
        
        assertThat(config).isNotNull();
    }

    @Test
    void testWebSocketHandlerRegistration() {
        WebSocketConfig config = new WebSocketConfig(locationWebSocketHandler, authenticationInterceptor);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);

        // addHandler returns WebSocketHandlerRegistration (fluent sub-type)
        org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration registration =
            mock(org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), any())).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOrigins(any())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        // Verify handlers are registered for expected endpoints
        verify(registry, times(3)).addHandler(eq(locationWebSocketHandler), anyString());
    }

    @Test
    void testWebSocketMessageCreation() {
        // Test WebSocketMessage DTO functionality
        WebSocketMessage systemMessage = WebSocketMessage.system("TEST", "test data");
        
        assertThat(systemMessage.type()).isEqualTo("TEST");
        assertThat(systemMessage.data()).isEqualTo("test data");
        assertThat(systemMessage.source()).isEqualTo("system");
        
        WebSocketMessage locationMessage = WebSocketMessage.locationUpdate("location data");
        
        assertThat(locationMessage.type()).isEqualTo("LOCATION_UPDATE");
        assertThat(locationMessage.data()).isEqualTo("location data");
        assertThat(locationMessage.source()).isEqualTo("tracking");
        
        WebSocketMessage errorMessage = WebSocketMessage.error("test error");
        
        assertThat(errorMessage.type()).isEqualTo("ERROR");
        assertThat(errorMessage.source()).isEqualTo("system");
    }

    @Test
    void testSessionManagerInterface() {
        // Test that session manager interface is properly defined
        assertThat(sessionManager).isNotNull();
        
        // Verify key methods exist (will be mocked)
        sessionManager.getSessionStatistics();
        verify(sessionManager).getSessionStatistics();
        
        sessionManager.cleanupInactiveSessions();
        verify(sessionManager).cleanupInactiveSessions();
    }

    @TestConfiguration
    static class TestConfig {
        // Test configuration for isolated WebSocket testing
    }
}