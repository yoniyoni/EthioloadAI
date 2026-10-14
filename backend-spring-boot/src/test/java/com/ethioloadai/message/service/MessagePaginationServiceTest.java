package com.ethioloadai.message.service;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.entity.Message;
import com.ethioloadai.message.entity.MessageType;
import com.ethioloadai.message.exception.MessageException;
import com.ethioloadai.message.repository.MessageRepository;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePaginationServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private FreightRepository freightRepository;
    @Mock private BookingRepository bookingRepository;
    @InjectMocks private MessageServiceImpl service;

    private User shipper;
    private User driver;
    private Freight freight;

    @BeforeEach
    void setUp() {
        shipper = User.builder().id(10L).fullName("Shipper").role(User.Role.SHIPPER).build();
        driver = User.builder().id(20L).fullName("Driver").role(User.Role.DRIVER).build();
        freight = Freight.builder().id(100L).shipper(shipper).matchedDriver(driver).build();
        when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
    }

    @Test
    void noParametersPreserveLegacyFullHistoryAndWrapper() {
        when(messageRepository.findConversationHistory(100L)).thenReturn(List.of());

        MessageListResponse response = service.getFreightMessages(100L, shipper);

        assertThat(response.messages()).isEmpty();
        assertThat(response.page()).isNull();
        verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, shipper.getId());
        verify(messageRepository).findConversationHistory(100L);
        verify(messageRepository, never()).findLatestPage(any(), any());
    }

    @Test
    void limitOnlyRequestsMostRecentPageWithFiftyMessages() {
        when(messageRepository.findLatestPage(eq(100L), any(PageRequest.class))).thenReturn(List.of());

        MessageListResponse response = service.getFreightMessages(100L, shipper, 50, null, null);

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(messageRepository).findLatestPage(eq(100L), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(51);
        assertThat(response.page().mode()).isEqualTo("LATEST");
        assertThat(response.page().hasOlder()).isFalse();
        assertThat(response.messages()).isEmpty();
    }

    @Test
    void omittedLimitOnCursorRequestDefaultsToFifty() {
        String cursor = MessageCursorCodec.encode(100L, LocalDateTime.now(), 1L);
        when(messageRepository.findNewerPage(eq(100L), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of());

        MessageListResponse response = service.getFreightMessages(100L, shipper, null, null, cursor);

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(messageRepository).findNewerPage(eq(100L), any(), any(), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(51);
        assertThat(response.messages()).isEmpty();
        assertThat(response.page().afterCursor()).isEqualTo(cursor);
        assertThat(response.page().hasMoreAfter()).isFalse();
    }

    @Test
    void pageSizeIsCappedAtOneHundredAndInvalidLimitsAreRejectedBeforeReadMarking() {
        when(messageRepository.findLatestPage(eq(100L), any(PageRequest.class))).thenReturn(List.of());
        service.getFreightMessages(100L, shipper, 100, null, null);
        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(messageRepository).findLatestPage(eq(100L), pageRequest.capture());
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(101);

        for (int invalid : List.of(-1, 0, 101)) {
            reset(messageRepository);
            assertThatThrownBy(() -> service.getFreightMessages(100L, shipper, invalid, null, null))
                    .isInstanceOf(MessageException.InvalidMessageException.class);
            verify(messageRepository, never()).markAsReadByFreightIdAndReceiverId(any(), any());
        }
    }

    @Test
    void rejectsBothDirectionsAndInvalidOrCrossFreightCursorsBeforeReadMarking() {
        assertThatThrownBy(() -> service.getFreightMessages(100L, shipper, 50, "x", "y"))
                .isInstanceOf(MessageException.InvalidMessageException.class);
        assertThatThrownBy(() -> service.getFreightMessages(100L, shipper, 50, "not-a-cursor", null))
                .isInstanceOf(MessageException.InvalidMessageException.class);

        String otherFreightCursor = MessageCursorCodec.encode(101L, LocalDateTime.now(), 1L);
        assertThatThrownBy(() -> service.getFreightMessages(100L, shipper, 50, null, otherFreightCursor))
                .isInstanceOf(MessageException.InvalidMessageException.class);
        verify(messageRepository, never()).markAsReadByFreightIdAndReceiverId(any(), any());
    }

    @Test
    void olderPageUsesExclusiveTimestampAndIdCursorAndReportsAnotherOlderPage() {
        Message first = message(9L, LocalDateTime.parse("2026-10-06T10:00:00.123456"));
        Message second = message(8L, LocalDateTime.parse("2026-10-06T10:00:00.123456"));
        Message extra = message(7L, LocalDateTime.parse("2026-10-06T09:59:59.999999"));
        when(messageRepository.findOlderPage(eq(100L), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(first, second, extra));
        String cursor = MessageCursorCodec.encode(100L, LocalDateTime.parse("2026-10-06T10:00:00.123456"), 10L);

        MessageListResponse response = service.getFreightMessages(100L, shipper, 2, cursor, null);

        verify(messageRepository).findOlderPage(eq(100L),
                eq(LocalDateTime.parse("2026-10-06T10:00:00.123456")), eq(10L), any(PageRequest.class));
        assertThat(response.messages()).extracting("id").containsExactly(9L, 8L);
        assertThat(response.page().hasOlder()).isTrue();
        MessageCursorCodec.Cursor olderCursor = MessageCursorCodec.decode(response.page().olderCursor(), 100L);
        assertThat(olderCursor.createdAt()).isEqualTo(second.getCreatedAt());
        assertThat(olderCursor.messageId()).isEqualTo(second.getId());
    }

    @Test
    void afterPageReturnsNewestFirstAndCursorAdvancesOnlyThroughReturnedMessages() {
        Message oldestNew = message(11L, LocalDateTime.parse("2026-10-06T10:00:00.000001"));
        Message newestReturned = message(12L, LocalDateTime.parse("2026-10-06T10:00:00.000002"));
        Message extra = message(13L, LocalDateTime.parse("2026-10-06T10:00:00.000003"));
        when(messageRepository.findNewerPage(eq(100L), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(oldestNew, newestReturned, extra));
        String cursor = MessageCursorCodec.encode(100L, LocalDateTime.parse("2026-10-06T10:00:00"), 10L);

        MessageListResponse response = service.getFreightMessages(100L, shipper, 2, null, cursor);

        assertThat(response.messages()).extracting("id").containsExactly(12L, 11L);
        assertThat(response.page().hasMoreAfter()).isTrue();
        MessageCursorCodec.Cursor afterCursor = MessageCursorCodec.decode(response.page().afterCursor(), 100L);
        assertThat(afterCursor.messageId()).isEqualTo(12L);
        verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, shipper.getId());
    }

    @Test
    void unauthorizedUserCannotReadOrMarkMessages() {
        User outsider = User.builder().id(99L).role(User.Role.SHIPPER).build();

        assertThatThrownBy(() -> service.getFreightMessages(100L, outsider, 50, null, null))
                .isInstanceOf(MessageException.UnauthorizedParticipantException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void sentMessageIsAvailableToTheNextIncrementalRead() {
        LocalDateTime priorTime = LocalDateTime.parse("2026-10-06T10:00:00.000001");
        LocalDateTime sentTime = priorTime.plusNanos(1_000);
        Message sent = message(11L, sentTime);
        when(messageRepository.save(any(Message.class))).thenReturn(sent);
        when(messageRepository.findNewerPage(eq(100L), any(), any(), any(PageRequest.class)))
                .thenReturn(List.of(sent));
        String currentCursor = MessageCursorCodec.encode(100L, priorTime, 10L);

        assertThat(service.sendMessage(new CreateMessageRequest(100L, driver.getId(), "On my way", "text"),
                shipper).id()).isEqualTo(11L);

        MessageListResponse incremental = service.getFreightMessages(100L, shipper, 50, null, currentCursor);

        assertThat(incremental.messages()).extracting("id").containsExactly(11L);
        assertThat(incremental.page().mode()).isEqualTo("AFTER");
    }

    private Message message(Long id, LocalDateTime createdAt) {
        return Message.builder()
                .id(id)
                .freight(freight)
                .sender(shipper)
                .receiver(driver)
                .content("message " + id)
                .type(MessageType.TEXT)
                .hasPhoneNumber(false)
                .hasPaymentRequest(false)
                .isRead(true)
                .createdAt(createdAt)
                .build();
    }
}
