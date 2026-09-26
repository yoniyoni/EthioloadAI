package com.ethioloadai.message.controller;

import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.dto.MessageResponse;
import com.ethioloadai.message.service.MessageService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Validated
@Tag(name = "Messages", description = "Endpoints for in-app messaging between freight shippers and drivers")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageService messageService;

    @GetMapping("/{freightId}")
    @Operation(summary = "Get messages for freight",
               description = "Retrieves conversation messages for an authorized freight shipment. " +
                             "Sender must be shipper, assigned driver, or admin. " +
                             "Automatically marks unread messages addressed to caller as read. " +
                             "Returns messages sorted newest first.")
    public ResponseEntity<MessageListResponse> getFreightMessages(
            @Parameter(description = "Freight shipment ID", required = true)
            @PathVariable Long freightId,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        log.info("GET /api/messages/{} caller={}", freightId, caller.getId());

        MessageListResponse response = messageService.getFreightMessages(freightId, caller);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Send message",
               description = "Sends a new message in a freight conversation. " +
                             "Sender is determined strictly by the authenticated JWT. " +
                             "Recipient is validated against the assigned freight counterparty. " +
                             "Phone numbers and direct payment requests are filtered for safety.")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody CreateMessageRequest request,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        log.info("POST /api/messages freightId={} caller={}", request.freightId(), caller.getId());

        MessageResponse response = messageService.sendMessage(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private User getUserFromAuthentication(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
