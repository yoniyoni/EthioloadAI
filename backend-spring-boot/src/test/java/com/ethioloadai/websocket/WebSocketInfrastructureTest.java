package com.ethioloadai.websocket;

import com.ethioloadai.EthioloadAiApplication;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.websocket.service.WebSocketSessionManager;
import com.ethioloadai.websocket.security.WebSocketRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for WebSocket infrastructure.
 * 
 * Tests WebSocket connections, authentication, and message handling
 * for real-time GPS tracking features.
 */
@SpringBootTest(
    classes = EthioloadAiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "jwt.secret=websocket-integration-test-secret-with-at-least-32-bytes",
    "logging.level.com.ethioloadai.websocket=DEBUG"
})
@Slf4j
class WebSocketInfrastructureTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private WebSocketRateLimitService rateLimitService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private StandardWebSocketClient webSocketClient;
    private String validJwtToken;
    private User testUser;

    @BeforeEach
    void setUp() {
        webSocketClient = new StandardWebSocketClient();
        validJwtToken = "valid.jwt.token";
        
        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setPhone("testdriver");
        testUser.setFullName("Test Driver");
        testUser.setRole(User.Role.DRIVER);
        testUser.setIsActive(true);

        // Mock JWT service
        when(jwtService.isTokenValid(validJwtToken)).thenReturn(true);
        when(jwtService.extractUsername(validJwtToken)).thenReturn(testUser.getPhone());
        
        // Mock user repository
        when(userRepository.findByEmailOrPhone(testUser.getPhone())).thenReturn(Optional.of(testUser));
    }

    @Test
    void testWebSocketConnectionWithValidToken() throws Exception {
        log.info("Testing WebSocket connection with valid JWT token");

        CountDownLatch connectionLatch = new CountDownLatch(1);
        AtomicReference<String> welcomeMessage = new AtomicReference<>();
        AtomicReference<Throwable> connectionError = new AtomicReference<>();

        WebSocketSession session = null;

        try {
            // Connect to WebSocket with valid token
            URI wsUri = URI.create("ws://localhost:" + port + "/api/ws/location?token=" + validJwtToken);
            
            session = webSocketClient.doHandshake(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("WebSocket connection established: {}", session.getId());
                    connectionLatch.countDown();
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    if (message instanceof TextMessage textMessage) {
                        String payload = textMessage.getPayload();
                        log.info("Received message: {}", payload);
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
                        if ("CONNECTION_ESTABLISHED".equals(messageData.get("type"))) {
                            welcomeMessage.set(payload);
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.error("WebSocket transport error: {}", exception.getMessage(), exception);
                    connectionError.set(exception);
                    connectionLatch.countDown();
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    log.info("WebSocket connection closed: {}", closeStatus);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, null, wsUri).get(5, TimeUnit.SECONDS);

            // Wait for connection
            boolean connected = connectionLatch.await(10, TimeUnit.SECONDS);
            assertThat(connected).isTrue();
            assertThat(connectionError.get()).isNull();
            assertThat(session.isOpen()).isTrue();

            // Wait a bit for welcome message
            Thread.sleep(1000);
            
            // Verify welcome message was received
            assertThat(welcomeMessage.get()).isNotNull();
            assertThat(welcomeMessage.get()).contains("CONNECTION_ESTABLISHED");

            log.info("WebSocket connection test passed");

        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }

        long releaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (rateLimitService.getUserRateLimitStatus(testUser.getId()).connectionCount() != 0
                && System.nanoTime() < releaseDeadline) {
            Thread.sleep(25);
        }
        assertThat(rateLimitService.getUserRateLimitStatus(testUser.getId()).connectionCount()).isZero();
    }

    @Test
    void testWebSocketConnectionWithInvalidToken() throws Exception {
        log.info("Testing WebSocket connection with invalid JWT token");

        CountDownLatch connectionLatch = new CountDownLatch(1);
        AtomicReference<Throwable> connectionError = new AtomicReference<>();

        // Mock invalid token
        when(jwtService.isTokenValid("invalid.token")).thenReturn(false);

        try {
            URI wsUri = URI.create("ws://localhost:" + port + "/api/ws/location?token=invalid.token");
            
            webSocketClient.doHandshake(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.error("WebSocket connection should not have been established with invalid token");
                    connectionLatch.countDown();
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    // Should not receive any messages
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.info("Expected WebSocket transport error with invalid token: {}", exception.getMessage());
                    connectionError.set(exception);
                    connectionLatch.countDown();
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    log.info("WebSocket connection closed as expected: {}", closeStatus);
                    connectionLatch.countDown();
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, null, wsUri).get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("Expected exception when connecting with invalid token: {}", e.getMessage());
            connectionError.set(e);
            connectionLatch.countDown();
        }

        // Wait for connection attempt
        boolean completed = connectionLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // Should have connection error
        assertThat(connectionError.get()).isNotNull();

        log.info("Invalid token test passed");
    }

    @Test
    void testWebSocketConnectionWithoutToken() throws Exception {
        log.info("Testing WebSocket connection without JWT token");

        CountDownLatch connectionLatch = new CountDownLatch(1);
        AtomicReference<Throwable> connectionError = new AtomicReference<>();

        try {
            URI wsUri = URI.create("ws://localhost:" + port + "/api/ws/location");
            
            webSocketClient.doHandshake(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.error("WebSocket connection should not have been established without token");
                    connectionLatch.countDown();
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    // Should not receive any messages
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.info("Expected WebSocket transport error without token: {}", exception.getMessage());
                    connectionError.set(exception);
                    connectionLatch.countDown();
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    log.info("WebSocket connection closed as expected: {}", closeStatus);
                    connectionLatch.countDown();
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, null, wsUri).get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.info("Expected exception when connecting without token: {}", e.getMessage());
            connectionError.set(e);
            connectionLatch.countDown();
        }

        // Wait for connection attempt
        boolean completed = connectionLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // Should have connection error
        assertThat(connectionError.get()).isNotNull();

        log.info("No token test passed");
    }

    @Test
    void testWebSocketSubscriptionFlow() throws Exception {
        log.info("Testing WebSocket subscription flow");

        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch subscriptionLatch = new CountDownLatch(1);
        AtomicReference<String> subscriptionResponse = new AtomicReference<>();

        WebSocketSession session = null;

        try {
            URI wsUri = URI.create("ws://localhost:" + port + "/api/ws/location?token=" + validJwtToken);
            
            session = webSocketClient.doHandshake(new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("WebSocket connection established for subscription test: {}", session.getId());
                    connectionLatch.countDown();

                    // Send subscription message after connection
                    Thread.sleep(500); // Give server time to process connection
                    
                    Map<String, Object> subscribeMessage = Map.of(
                        "type", "SUBSCRIBE",
                        "data", Map.of("topic", "trip.123.location")
                    );
                    
                    String messageJson = objectMapper.writeValueAsString(subscribeMessage);
                    session.sendMessage(new TextMessage(messageJson));
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    if (message instanceof TextMessage textMessage) {
                        String payload = textMessage.getPayload();
                        log.info("Received subscription response: {}", payload);
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
                        if ("SUBSCRIBED".equals(messageData.get("type"))) {
                            subscriptionResponse.set(payload);
                            subscriptionLatch.countDown();
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.error("WebSocket transport error during subscription test: {}", exception.getMessage(), exception);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    log.info("WebSocket connection closed: {}", closeStatus);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            }, null, wsUri).get(5, TimeUnit.SECONDS);

            // Wait for connection and subscription
            boolean connected = connectionLatch.await(10, TimeUnit.SECONDS);
            assertThat(connected).isTrue();
            assertThat(session.isOpen()).isTrue();

            boolean subscribed = subscriptionLatch.await(10, TimeUnit.SECONDS);
            assertThat(subscribed).isTrue();
            assertThat(subscriptionResponse.get()).isNotNull();
            assertThat(subscriptionResponse.get()).contains("SUBSCRIBED");

            log.info("WebSocket subscription test passed");

        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    @Test
    void testSessionManagerStatistics() {
        log.info("Testing WebSocket session manager statistics");

        WebSocketSessionManager.SessionStatistics stats = sessionManager.getSessionStatistics();
        
        assertThat(stats).isNotNull();
        assertThat(stats.totalSessions()).isGreaterThanOrEqualTo(0);
        assertThat(stats.activeSessions()).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalSubscriptions()).isGreaterThanOrEqualTo(0);
        assertThat(stats.subscriptionsByTopic()).isNotNull();
        assertThat(stats.sessionsByRole()).isNotNull();
        assertThat(stats.sessionsByEndpoint()).isNotNull();

        log.info("Session statistics: {}", stats);
        log.info("Session manager statistics test passed");
    }
}