package com.ethioloadai.message.entity;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.message.converter.MessageTypeConverter;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freight_id", nullable = false)
    private Freight freight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "masked_content", length = 2000)
    private String maskedContent;

    @Convert(converter = MessageTypeConverter.class)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Column(name = "has_phone_number", nullable = false)
    @Builder.Default
    private Boolean hasPhoneNumber = false;

    @Column(name = "has_payment_request", nullable = false)
    @Builder.Default
    private Boolean hasPaymentRequest = false;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
