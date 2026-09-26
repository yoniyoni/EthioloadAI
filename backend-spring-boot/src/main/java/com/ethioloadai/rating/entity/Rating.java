package com.ethioloadai.rating.entity;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A post-trip rating submitted by one party (shipper or driver) for a booking.
 *
 * <p>Both parties may rate: unique constraint on (booking_id, rater_id) allows
 * at most one row per rater per booking, meaning up to two ratings per booking.
 *
 * <p>rating: integer 1–5 (enforced by DB CHECK constraint).
 * feedback: optional free-text comment.
 */
@Entity
@Table(name = "ratings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id", nullable = false)
    private User shipper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    /** The user who submitted this rating row (shipper or driver). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id")
    private User rater;

    /** Integer 1–5. DB CHECK constraint enforces the range. */
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
