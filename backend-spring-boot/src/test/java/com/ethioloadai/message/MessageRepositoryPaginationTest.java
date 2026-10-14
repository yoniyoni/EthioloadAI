package com.ethioloadai.message;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.message.entity.Message;
import com.ethioloadai.message.entity.MessageType;
import com.ethioloadai.message.repository.MessageRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryPaginationTest {

    @Autowired private MessageRepository messageRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Freight freight;
    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = userRepository.saveAndFlush(User.builder()
                .fullName("Shipper").phone("0911111111").password("hashed")
                .role(User.Role.SHIPPER).build());
        receiver = userRepository.saveAndFlush(User.builder()
                .fullName("Driver").phone("0922222222").password("hashed")
                .role(User.Role.DRIVER).build());
        freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(sender)
                .pickupLocation("Addis Ababa")
                .destination("Adama")
                .materialType("general")
                .weightTons(new java.math.BigDecimal("1"))
                .budget(new java.math.BigDecimal("100"))
                .urgencyLevel("normal")
                .matchedDriver(receiver)
                .build());
    }

    @Test
    void keysetQueriesUseExclusiveTimestampAndIdBoundariesWithStableOrdering() {
        Message older = saveMessage("older");
        Message tieLowId = saveMessage("tie low");
        Message tieHighId = saveMessage("tie high");
        Message newest = saveMessage("newest");

        LocalDateTime tieTime = LocalDateTime.parse("2026-10-06T10:00:00.123456");
        LocalDateTime olderTime = tieTime.minusSeconds(1);
        LocalDateTime newestTime = tieTime.plusSeconds(1);
        setCreatedAt(older.getId(), olderTime);
        setCreatedAt(tieLowId.getId(), tieTime);
        setCreatedAt(tieHighId.getId(), tieTime);
        setCreatedAt(newest.getId(), newestTime);

        List<Message> latest = messageRepository.findLatestPage(freight.getId(),
                org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(latest).extracting(Message::getId).containsExactly(newest.getId(), tieHighId.getId());

        List<Message> olderPage = messageRepository.findOlderPage(
                freight.getId(), tieHighId.getCreatedAt(), tieHighId.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(olderPage).extracting(Message::getId).containsExactly(tieLowId.getId(), older.getId());

        List<Message> newerPage = messageRepository.findNewerPage(
                freight.getId(), tieLowId.getCreatedAt(), tieLowId.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(newerPage).extracting(Message::getId)
                .containsExactly(tieHighId.getId(), newest.getId());

        assertThat(messageRepository.findOlderPage(freight.getId(), olderTime, older.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
    }

    private Message saveMessage(String content) {
        return messageRepository.saveAndFlush(Message.builder()
                .freight(freight)
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .type(MessageType.TEXT)
                .hasPhoneNumber(false)
                .hasPaymentRequest(false)
                .isRead(false)
                .build());
    }

    private void setCreatedAt(Long messageId, LocalDateTime timestamp) {
        jdbcTemplate.update("UPDATE messages SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(timestamp), messageId);
        messageRepository.flush();
        // Ensure subsequent assertions use persisted timestamp values.
        messageRepository.findById(messageId).ifPresent(message -> message.setCreatedAt(timestamp));
    }
}
