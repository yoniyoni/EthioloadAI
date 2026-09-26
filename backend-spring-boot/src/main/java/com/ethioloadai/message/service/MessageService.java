package com.ethioloadai.message.service;

import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.dto.MessageResponse;
import com.ethioloadai.user.entity.User;

public interface MessageService {

    /**
     * Send a new message in a freight conversation.
     * Sender is strictly determined by the authenticated caller user.
     */
    MessageResponse sendMessage(CreateMessageRequest request, User callerUser);

    /**
     * Retrieve all messages for a freight conversation, newest first.
     * Automatically marks unread messages addressed to the caller as read.
     */
    MessageListResponse getFreightMessages(Long freightId, User callerUser);
}
