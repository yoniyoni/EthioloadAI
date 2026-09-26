package com.ethioloadai.websocket.config;

import com.ethioloadai.websocket.handler.LocationWebSocketHandler;
import com.ethioloadai.websocket.interceptor.WebSocketAuthenticationInterceptor;
import com.ethioloadai.config.CorsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for real-time GPS tracking and notifications.
 * 
 * Configures WebSocket endpoints, handlers, and security interceptors
 * for real-time communication between backend and clients.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LocationWebSocketHandler locationWebSocketHandler;
    private final WebSocketAuthenticationInterceptor authenticationInterceptor;
        private final CorsProperties corsProperties;

    public WebSocketConfig(LocationWebSocketHandler locationWebSocketHandler,
                           WebSocketAuthenticationInterceptor authenticationInterceptor) {
        this(locationWebSocketHandler, authenticationInterceptor, new CorsProperties());
    }

    @Autowired
    public WebSocketConfig(LocationWebSocketHandler locationWebSocketHandler,
                           WebSocketAuthenticationInterceptor authenticationInterceptor,
                           CorsProperties corsProperties) {
        this.locationWebSocketHandler = locationWebSocketHandler;
        this.authenticationInterceptor = authenticationInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        boolean hasPatterns = corsProperties.getAllowedOriginPatterns() != null
                && !corsProperties.getAllowedOriginPatterns().isEmpty();

        // Real-time GPS location updates endpoint
        var locationReg = registry.addHandler(locationWebSocketHandler, "/ws/location")
                .addInterceptors(authenticationInterceptor);
        if (hasPatterns) {
            locationReg.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
        } else {
            locationReg.setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
        }

        // Trip notifications endpoint
        var notifReg = registry.addHandler(locationWebSocketHandler, "/ws/notifications")
                .addInterceptors(authenticationInterceptor);
        if (hasPatterns) {
            notifReg.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
        } else {
            notifReg.setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
        }

        // Fleet management dashboard endpoint
        var fleetReg = registry.addHandler(locationWebSocketHandler, "/ws/fleet")
                .addInterceptors(authenticationInterceptor);
        if (hasPatterns) {
            fleetReg.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
        } else {
            fleetReg.setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
        }
    }
}