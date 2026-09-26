package com.ethioloadai.message;

import com.ethioloadai.message.controller.MessageController;
import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.dto.MessageResponse;
import com.ethioloadai.message.dto.SenderDto;
import com.ethioloadai.message.service.MessageService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController")
class MessageControllerTest {

    @Mock private MessageService messageService;
    @Mock private Authentication authentication;

    @InjectMocks
    private MessageController messageController;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = User.builder()
                .id(42L)
                .fullName("Test Caller")
                .phone("+251911424242")
                .role(User.Role.SHIPPER)
                .build();
    }

    @Test
    @DisplayName("GET /api/messages/{freightId} returns 200 OK with MessageListResponse")
    void getFreightMessages_returns200WithMessages() {
        // JwtAuthenticationFilter stores the raw User entity as principal — not UserDetailsImpl
        when(authentication.getPrincipal()).thenReturn(caller);

        MessageResponse msg = MessageResponse.builder()
                .id(1L)
                .senderId(42L)
                .receiverId(43L)
                .freightId(10L)
                .content("Hello driver")
                .type("text")
                .hasPhoneNumber(false)
                .hasPaymentRequest(false)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .sender(new SenderDto("Test Caller", "shipper"))
                .build();

        MessageListResponse serviceResponse = new MessageListResponse(List.of(msg));
        when(messageService.getFreightMessages(eq(10L), eq(caller))).thenReturn(serviceResponse);

        ResponseEntity<MessageListResponse> response = messageController.getFreightMessages(10L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().messages()).hasSize(1);
        assertThat(response.getBody().messages().get(0).content()).isEqualTo("Hello driver");
    }

    @Test
    @DisplayName("POST /api/messages returns 201 CREATED with MessageResponse")
    void sendMessage_returns201WithMessageResponse() {
        when(authentication.getPrincipal()).thenReturn(caller);

        CreateMessageRequest req = new CreateMessageRequest(10L, 43L, "On my way", "text");
        MessageResponse serviceResponse = MessageResponse.builder()
                .id(2L)
                .senderId(42L)
                .receiverId(43L)
                .freightId(10L)
                .content("On my way")
                .type("text")
                .hasPhoneNumber(false)
                .hasPaymentRequest(false)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .sender(new SenderDto("Test Caller", "shipper"))
                .build();

        when(messageService.sendMessage(eq(req), eq(caller))).thenReturn(serviceResponse);

        ResponseEntity<MessageResponse> response = messageController.sendMessage(req, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(2L);
        assertThat(response.getBody().content()).isEqualTo("On my way");
    }
}
