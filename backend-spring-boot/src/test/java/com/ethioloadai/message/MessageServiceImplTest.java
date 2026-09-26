package com.ethioloadai.message;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.message.dto.CreateMessageRequest;
import com.ethioloadai.message.dto.MessageListResponse;
import com.ethioloadai.message.dto.MessageResponse;
import com.ethioloadai.message.entity.Message;
import com.ethioloadai.message.entity.MessageType;
import com.ethioloadai.message.exception.MessageException;
import com.ethioloadai.message.repository.MessageRepository;
import com.ethioloadai.message.service.MessageServiceImpl;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl")
class MessageServiceImplTest {

    @Mock private MessageRepository messageRepository;
    @Mock private FreightRepository freightRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks
    private MessageServiceImpl messageService;

    private User shipper;
    private User driver;
    private User admin;
    private User intruder;
    private Freight freight;

    @BeforeEach
    void setUp() {
        shipper = User.builder()
                .id(10L)
                .fullName("Abebe Shipper")
                .phone("+251911000001")
                .email("shipper@test.com")
                .role(User.Role.SHIPPER)
                .build();

        driver = User.builder()
                .id(20L)
                .fullName("Kebede Driver")
                .phone("+251922000002")
                .email("driver@test.com")
                .role(User.Role.DRIVER)
                .build();

        admin = User.builder()
                .id(99L)
                .fullName("Admin User")
                .phone("+251999000099")
                .role(User.Role.ADMIN)
                .build();

        intruder = User.builder()
                .id(55L)
                .fullName("Intruder User")
                .phone("+251955000055")
                .role(User.Role.SHIPPER)
                .build();

        freight = Freight.builder()
                .id(100L)
                .shipper(shipper)
                .matchedDriver(driver)
                .status(Freight.FreightStatus.MATCHED)
                .pickupLocation("Addis Ababa")
                .destination("Hawassa")
                .materialType("General")
                .build();
    }

    // ── Authorization & IDOR ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Authorization & IDOR")
    class AuthorizationAndIdor {

        @Test
        @DisplayName("Shipper can retrieve messages for their freight")
        void getMessages_asShipper_success() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(Collections.emptyList());

            MessageListResponse response = messageService.getFreightMessages(100L, shipper);

            assertThat(response).isNotNull();
            assertThat(response.messages()).isEmpty();
            verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, shipper.getId());
        }

        @Test
        @DisplayName("Matched driver can retrieve messages for the freight")
        void getMessages_asMatchedDriver_success() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(Collections.emptyList());

            MessageListResponse response = messageService.getFreightMessages(100L, driver);

            assertThat(response).isNotNull();
            verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, driver.getId());
        }

        @Test
        @DisplayName("Driver assigned via Booking can retrieve messages when matchedDriver is null")
        void getMessages_asDriver_viaBooking_success() {
            freight.setMatchedDriver(null);
            Booking booking = Booking.builder().id(1L).driver(driver).cargoRequest(freight).build();

            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(100L)).thenReturn(List.of(booking));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(Collections.emptyList());

            MessageListResponse response = messageService.getFreightMessages(100L, driver);

            assertThat(response).isNotNull();
            verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, driver.getId());
        }

        @Test
        @DisplayName("Admin can retrieve messages for any freight (supervisory read)")
        void getMessages_asAdmin_success() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(Collections.emptyList());

            MessageListResponse response = messageService.getFreightMessages(100L, admin);

            assertThat(response).isNotNull();
            verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, admin.getId());
        }

        @Test
        @DisplayName("Non-participant user is rejected with 403 Forbidden (IDOR protection)")
        void getMessages_unauthorizedUser_throwsForbidden() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));

            assertThatThrownBy(() -> messageService.getFreightMessages(100L, intruder))
                    .isInstanceOf(MessageException.UnauthorizedParticipantException.class)
                    .hasMessageContaining("not authorized to view messages");

            verify(messageRepository, never()).markAsReadByFreightIdAndReceiverId(any(), any());
            verify(messageRepository, never()).findByFreightIdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Non-existent freight returns 404 Not Found")
        void getMessages_nonexistentFreight_throwsNotFound() {
            when(freightRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> messageService.getFreightMessages(999L, shipper))
                    .isInstanceOf(MessageException.FreightNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Non-participant cannot send messages (403 Forbidden)")
        void sendMessage_unauthorizedUser_throwsForbidden() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Hello", "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, intruder))
                    .isInstanceOf(MessageException.UnauthorizedParticipantException.class)
                    .hasMessageContaining("not authorized to participate");

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sending message on freight with no matched driver throws 400 Bad Request")
        void sendMessage_unassignedFreight_throwsBadRequest() {
            freight.setMatchedDriver(null);
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(100L)).thenReturn(Collections.emptyList());

            CreateMessageRequest req = new CreateMessageRequest(100L, null, "Hello", "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, shipper))
                    .isInstanceOf(MessageException.InvalidMessageException.class)
                    .hasMessageContaining("no driver has been matched or assigned");

            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sending message with mismatched receiverId throws 400 Bad Request (tamper prevention)")
        void sendMessage_mismatchedReceiverId_throwsBadRequest() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));

            // Shipper attempts to send to random intruder ID instead of matched driver
            CreateMessageRequest req = new CreateMessageRequest(100L, intruder.getId(), "Hello", "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, shipper))
                    .isInstanceOf(MessageException.InvalidMessageException.class)
                    .hasMessageContaining("Invalid recipient specified");

            verify(messageRepository, never()).save(any());
        }
    }

    // ── Sender Enactment & Recipient Resolution ───────────────────────────────

    @Nested
    @DisplayName("Sender Enactment & Counterparty Resolution")
    class SenderAndRecipientResolution {

        @Test
        @DisplayName("Shipper sending message resolves recipient to driver and forces sender to caller")
        void sendMessage_asShipper_resolvesRecipientToDriver() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(1L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "What is your ETA?", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.senderId()).isEqualTo(shipper.getId());
            assertThat(res.receiverId()).isEqualTo(driver.getId());
            assertThat(res.content()).isEqualTo("What is your ETA?");

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            Message saved = captor.getValue();
            assertThat(saved.getSender().getId()).isEqualTo(shipper.getId());
            assertThat(saved.getReceiver().getId()).isEqualTo(driver.getId());
            assertThat(saved.getFreight().getId()).isEqualTo(100L);
            assertThat(saved.getIsRead()).isFalse();
        }

        @Test
        @DisplayName("Driver sending message resolves recipient to shipper")
        void sendMessage_asDriver_resolvesRecipientToShipper() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(2L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, shipper.getId(), "Arriving in 30 minutes", "text");
            MessageResponse res = messageService.sendMessage(req, driver);

            assertThat(res.senderId()).isEqualTo(driver.getId());
            assertThat(res.receiverId()).isEqualTo(shipper.getId());

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(captor.capture());
            Message saved = captor.getValue();
            assertThat(saved.getSender().getId()).isEqualTo(driver.getId());
            assertThat(saved.getReceiver().getId()).isEqualTo(shipper.getId());
        }

        @Test
        @DisplayName("Null receiverId in request is safely resolved to the authorized counterparty")
        void sendMessage_nullReceiverId_autoResolved() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(3L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, null, "Hello driver", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.receiverId()).isEqualTo(driver.getId());
        }
    }

    // ── Content Filtering & Safety ────────────────────────────────────────────

    @Nested
    @DisplayName("Content Filtering & Safety")
    class ContentFiltering {

        @Test
        @DisplayName("Ethiopian mobile phone number is detected, masked, and warning is attached")
        void sendMessage_withEthiopianPhone_masksContentAndSetsWarning() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(10L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Call me at 0911234567 soon", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPhoneNumber()).isTrue();
            assertThat(res.maskedContent()).contains("[PHONE NUMBER MASKED]");
            assertThat(res.maskedContent()).doesNotContain("0911234567");
            assertThat(res.content()).isEqualTo("Call me at 0911234567 soon");
            assertThat(res.warning()).isNotNull().contains("restricted");
        }

        @Test
        @DisplayName("International Ethiopian phone (+251) is detected and masked")
        void sendMessage_withInternationalPhone_masksContent() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(11L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Reach me on +251912345678", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPhoneNumber()).isTrue();
            assertThat(res.maskedContent()).contains("[PHONE NUMBER MASKED]");
            assertThat(res.maskedContent()).doesNotContain("+251912345678");
            assertThat(res.warning()).isNotNull();
        }

        @Test
        @DisplayName("Standard formatted 10-digit phone is detected and masked")
        void sendMessage_withStandardFormattedPhone_masksContent() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(12L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "My number is 555-123-4567", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPhoneNumber()).isTrue();
            assertThat(res.maskedContent()).contains("[PHONE NUMBER MASKED]");
            assertThat(res.maskedContent()).doesNotContain("555-123-4567");
        }

        @Test
        @DisplayName("Direct off-platform payment request (e.g. telebirr) is flagged and returns warning")
        void sendMessage_withPaymentKeyword_setsFlagAndWarning() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(13L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Please pay via telebirr", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPaymentRequest()).isTrue();
            assertThat(res.warning()).isNotNull().contains("escrow");
        }

        @Test
        @DisplayName("Bank account keyword is detected as payment request")
        void sendMessage_withBankAccount_setsFlagAndWarning() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(14L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Send to CBE bank account 1000", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPaymentRequest()).isTrue();
            assertThat(res.warning()).isNotNull();
        }

        @Test
        @DisplayName("Clean text has no phone number, no payment request, null maskedContent, and null warning")
        void sendMessage_cleanText_noMaskingNoWarning() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
                Message m = inv.getArgument(0);
                m.setId(15L);
                m.setCreatedAt(LocalDateTime.now());
                return m;
            });

            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "Is the cargo loaded yet?", "text");
            MessageResponse res = messageService.sendMessage(req, shipper);

            assertThat(res.hasPhoneNumber()).isFalse();
            assertThat(res.hasPaymentRequest()).isFalse();
            assertThat(res.maskedContent()).isNull();
            assertThat(res.warning()).isNull();
        }
    }

    // ── Input Validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Null content throws 400 Bad Request")
        void sendMessage_nullContent_throwsBadRequest() {
            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), null, "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, shipper))
                    .isInstanceOf(MessageException.InvalidMessageException.class)
                    .hasMessageContaining("cannot be blank");
        }

        @Test
        @DisplayName("Blank content throws 400 Bad Request")
        void sendMessage_blankContent_throwsBadRequest() {
            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), "    ", "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, shipper))
                    .isInstanceOf(MessageException.InvalidMessageException.class)
                    .hasMessageContaining("cannot be blank");
        }

        @Test
        @DisplayName("Content exceeding 2000 chars throws 400 Bad Request")
        void sendMessage_contentTooLong_throwsBadRequest() {
            String longContent = "A".repeat(2001);
            CreateMessageRequest req = new CreateMessageRequest(100L, driver.getId(), longContent, "text");

            assertThatThrownBy(() -> messageService.sendMessage(req, shipper))
                    .isInstanceOf(MessageException.InvalidMessageException.class)
                    .hasMessageContaining("exceed 2000 characters");
        }
    }

    // ── Read Tracking & Ordering ──────────────────────────────────────────────

    @Nested
    @DisplayName("Read Tracking & Ordering")
    class ReadTrackingAndOrdering {

        @Test
        @DisplayName("getFreightMessages marks unread messages addressed to caller as read")
        void getMessages_marksUnreadAsRead() {
            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(Collections.emptyList());

            messageService.getFreightMessages(100L, driver);

            verify(messageRepository).markAsReadByFreightIdAndReceiverId(100L, driver.getId());
        }

        @Test
        @DisplayName("getFreightMessages returns messages with full DTO mappings and newest first")
        void getMessages_returnsMessagesWithDtoMapping() {
            LocalDateTime now = LocalDateTime.now();
            Message m1 = Message.builder()
                    .id(101L)
                    .freight(freight)
                    .sender(shipper)
                    .receiver(driver)
                    .content("Newer message")
                    .type(MessageType.TEXT)
                    .hasPhoneNumber(false)
                    .hasPaymentRequest(false)
                    .isRead(true)
                    .createdAt(now)
                    .build();

            Message m2 = Message.builder()
                    .id(100L)
                    .freight(freight)
                    .sender(driver)
                    .receiver(shipper)
                    .content("Older message")
                    .type(MessageType.TEXT)
                    .hasPhoneNumber(false)
                    .hasPaymentRequest(false)
                    .isRead(true)
                    .createdAt(now.minusMinutes(5))
                    .build();

            when(freightRepository.findById(100L)).thenReturn(Optional.of(freight));
            when(messageRepository.findByFreightIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(m1, m2));

            MessageListResponse response = messageService.getFreightMessages(100L, shipper);

            assertThat(response.messages()).hasSize(2);
            MessageResponse r1 = response.messages().get(0);
            assertThat(r1.id()).isEqualTo(101L);
            assertThat(r1.content()).isEqualTo("Newer message");
            assertThat(r1.sender().name()).isEqualTo("Abebe Shipper");
            assertThat(r1.sender().role()).isEqualTo("shipper");

            MessageResponse r2 = response.messages().get(1);
            assertThat(r2.id()).isEqualTo(100L);
            assertThat(r2.content()).isEqualTo("Older message");
            assertThat(r2.sender().name()).isEqualTo("Kebede Driver");
            assertThat(r2.sender().role()).isEqualTo("driver");
        }
    }
}
