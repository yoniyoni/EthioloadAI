package com.ethioloadai.message.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.dto.MessagePageMetadata;
import com.ethioloadai.message.dto.MessageResponse;
import com.ethioloadai.message.dto.SenderDto;
import com.ethioloadai.message.entity.Message;
import com.ethioloadai.message.entity.MessageType;
import com.ethioloadai.message.exception.MessageException;
import com.ethioloadai.message.repository.MessageRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final MessageRepository messageRepository;
    private final FreightRepository freightRepository;
    private final BookingRepository bookingRepository;

    // Matches Ethiopian mobile patterns (e.g. 0911234567, +251911234567, 0712345678, with optional delimiters)
    private static final Pattern ETHIOPIAN_PHONE_PATTERN = Pattern.compile(
            "(?:\\+?251[\\s.-]?|0)[79](?:[\\s.-]?\\d){8}"
    );

    // Matches standard 10-digit formatted telephone patterns (e.g. 123-456-7890, 123.456.7890)
    private static final Pattern STANDARD_PHONE_PATTERN = Pattern.compile(
            "\\b\\d{3}[-.\\s]\\d{3}[-.\\s]\\d{4}\\b"
    );

    // Keywords indicating off-platform payment attempts
    private static final List<String> PAYMENT_KEYWORDS = List.of(
            "telebirr",
            "cbe birr",
            "cbebirr",
            "cbe",
            "bank account",
            "account number",
            "pay me directly",
            "direct payment",
            "wire transfer"
    );

    private static final String SAFETY_WARNING =
            "For your safety, phone numbers and direct payment requests are restricted. All payments must go through escrow.";

    @Override
    @Transactional
    public MessageResponse sendMessage(CreateMessageRequest request, User callerUser) {
        if (request.content() == null || request.content().isBlank()) {
            throw new MessageException.InvalidMessageException("Message content cannot be blank");
        }
        if (request.content().length() > 2000) {
            throw new MessageException.InvalidMessageException("Message content must not exceed 2000 characters");
        }

        Freight freight = freightRepository.findById(request.freightId())
                .orElseThrow(() -> new MessageException.FreightNotFoundException(request.freightId()));

        User shipper = freight.getShipper();
        User driver = resolveFreightDriver(freight);

        if (driver == null) {
            throw new MessageException.InvalidMessageException(
                    "Cannot send message: no driver has been matched or assigned to this freight yet"
            );
        }

        // Determine sender and counterparty strictly from domain authorization
        User expectedRecipient;
        if (callerUser.getId().equals(shipper.getId())) {
            expectedRecipient = driver;
        } else if (callerUser.getId().equals(driver.getId())) {
            expectedRecipient = shipper;
        } else {
            throw new MessageException.UnauthorizedParticipantException(
                    "You are not authorized to participate in messaging for this freight"
            );
        }

        // Validate client-supplied receiverId (if present) against computed counterparty to prevent tampering
        if (request.receiverId() != null && !request.receiverId().equals(expectedRecipient.getId())) {
            throw new MessageException.InvalidMessageException(
                    "Invalid recipient specified for this freight conversation"
            );
        }

        // Phone number detection & masking
        String rawContent = request.content().trim();
        boolean hasPhone = ETHIOPIAN_PHONE_PATTERN.matcher(rawContent).find()
                || STANDARD_PHONE_PATTERN.matcher(rawContent).find();
        String maskedContent = null;
        if (hasPhone) {
            String masked = ETHIOPIAN_PHONE_PATTERN.matcher(rawContent).replaceAll("[PHONE NUMBER MASKED]");
            maskedContent = STANDARD_PHONE_PATTERN.matcher(masked).replaceAll("[PHONE NUMBER MASKED]");
        }

        // Payment request detection
        String lowerContent = rawContent.toLowerCase();
        boolean hasPaymentRequest = PAYMENT_KEYWORDS.stream().anyMatch(lowerContent::contains);

        String warning = (hasPhone || hasPaymentRequest) ? SAFETY_WARNING : null;

        Message message = Message.builder()
                .freight(freight)
                .sender(callerUser)
                .receiver(expectedRecipient)
                .content(rawContent)
                .maskedContent(maskedContent)
                .type(MessageType.TEXT)
                .hasPhoneNumber(hasPhone)
                .hasPaymentRequest(hasPaymentRequest)
                .isRead(false)
                .build();

        Message saved = messageRepository.save(message);
        log.info("Message sent: id={}, freightId={}, senderId={}, receiverId={}",
                saved.getId(), freight.getId(), callerUser.getId(), expectedRecipient.getId());

        return toMessageResponse(saved, warning);
    }

    @Override
    @Transactional
    public MessageListResponse getFreightMessages(Long freightId, User callerUser) {
        return getFreightMessages(freightId, callerUser, null, null, null);
    }

    @Override
    @Transactional
    public MessageListResponse getFreightMessages(
            Long freightId, User callerUser, Integer requestedLimit, String before, String after) {
        Freight freight = freightRepository.findById(freightId)
                .orElseThrow(() -> new MessageException.FreightNotFoundException(freightId));

        User shipper = freight.getShipper();
        User driver = resolveFreightDriver(freight);

        boolean isShipper = callerUser.getId().equals(shipper.getId());
        boolean isDriver = freight.getStatus() != Freight.FreightStatus.CANCELLED
                && driver != null && callerUser.getId().equals(driver.getId());
        boolean isAdmin = callerUser.getRole() == User.Role.ADMIN;

        if (!isShipper && !isDriver && !isAdmin) {
            throw new MessageException.UnauthorizedParticipantException(
                    "You are not authorized to view messages for this freight"
            );
        }

        if (before != null && after != null) {
            throw new MessageException.InvalidMessageException("Only one of before or after may be specified");
        }
        if (requestedLimit != null && (requestedLimit < 1 || requestedLimit > MAX_PAGE_SIZE)) {
            throw new MessageException.InvalidMessageException(
                    "Message page limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        MessageCursorCodec.Cursor beforePosition = before == null
                ? null : MessageCursorCodec.decode(before, freightId);
        MessageCursorCodec.Cursor afterPosition = after == null
                ? null : MessageCursorCodec.decode(after, freightId);

        // Mark unread messages addressed to the caller as read
        messageRepository.markAsReadByFreightIdAndReceiverId(freightId, callerUser.getId());

        // No parameters preserve the historical full-conversation response.
        if (requestedLimit == null && before == null && after == null) {
            return toResponse(messageRepository.findConversationHistory(freightId), null);
        }

        int limit = requestedLimit == null ? DEFAULT_PAGE_SIZE : requestedLimit;
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        if (beforePosition != null) {
            List<Message> fetched = messageRepository.findOlderPage(
                    freightId, beforePosition.createdAt(), beforePosition.messageId(), pageRequest);
            boolean hasOlder = fetched.size() > limit;
            List<Message> page = new ArrayList<>(fetched.subList(0, Math.min(limit, fetched.size())));
            String olderCursor = hasOlder && !page.isEmpty() ? cursorFor(freightId, page.getLast()) : null;
            return toResponse(page, new MessagePageMetadata("OLDER", olderCursor, null, hasOlder, null));
        }

        if (afterPosition != null) {
            List<Message> fetchedAscending = messageRepository.findNewerPage(
                    freightId, afterPosition.createdAt(), afterPosition.messageId(), pageRequest);
            boolean hasMoreAfter = fetchedAscending.size() > limit;
            List<Message> page = new ArrayList<>(
                    fetchedAscending.subList(0, Math.min(limit, fetchedAscending.size())));
            java.util.Collections.reverse(page);
            String afterCursor = page.isEmpty() ? after : cursorFor(freightId, page.getFirst());
            return toResponse(page, new MessagePageMetadata("AFTER", null, afterCursor, null, hasMoreAfter));
        }

        List<Message> fetched = messageRepository.findLatestPage(freightId, pageRequest);
        boolean hasOlder = fetched.size() > limit;
        List<Message> page = new ArrayList<>(fetched.subList(0, Math.min(limit, fetched.size())));
        String olderCursor = hasOlder && !page.isEmpty() ? cursorFor(freightId, page.getLast()) : null;
        String afterCursor = page.isEmpty() ? null : cursorFor(freightId, page.getFirst());
        return toResponse(page, new MessagePageMetadata("LATEST", olderCursor, afterCursor, hasOlder, false));
    }

    private MessageListResponse toResponse(List<Message> messages, MessagePageMetadata page) {
        List<MessageResponse> dtos = messages.stream()
                .map(m -> toMessageResponse(m, null))
                .toList();

        return new MessageListResponse(dtos, page);
    }

    private String cursorFor(Long freightId, Message message) {
        return MessageCursorCodec.encode(freightId, message.getCreatedAt(), message.getId());
    }

    private User resolveFreightDriver(Freight freight) {
        if (freight.getStatus() == Freight.FreightStatus.CANCELLED) {
            return null;
        }
        if (freight.getMatchedDriver() != null) {
            return freight.getMatchedDriver();
        }
        List<User> eligibleDrivers = bookingRepository.findDistinctDriversByCargoRequest_IdAndBookingStatus(
                freight.getId(),
                Booking.BookingStatus.ACCEPTED,
                PageRequest.of(0, 2));
        return eligibleDrivers.size() == 1 ? eligibleDrivers.getFirst() : null;
    }

    private MessageResponse toMessageResponse(Message message, String warning) {
        String senderName = message.getSender() != null ? message.getSender().getFullName() : "Unknown";
        String senderRole = message.getSender() != null && message.getSender().getRole() != null
                ? message.getSender().getRole().name().toLowerCase()
                : "user";

        return MessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .receiverId(message.getReceiver().getId())
                .freightId(message.getFreight().getId())
                .type(message.getType() != null ? message.getType().name().toLowerCase() : "text")
                .content(message.getContent())
                .maskedContent(message.getMaskedContent())
                .hasPhoneNumber(message.getHasPhoneNumber())
                .hasPaymentRequest(message.getHasPaymentRequest())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .sender(new SenderDto(senderName, senderRole))
                .warning(warning)
                .build();
    }
}
