package com.ethioloadai.history.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HistoryRepository extends Repository<com.ethioloadai.booking.entity.Booking, Long> {
    @Query(value = """
            WITH ranked_bookings AS (
                SELECT b.cargo_id AS freight_id,
                       b.id AS booking_id,
                       b.booking_status AS booking_status,
                       b.updated_at AS booking_updated_at,
                       booking_driver.full_name AS driver_name,
                       t.id AS trip_id,
                       t.trip_status AS trip_status,
                       t.updated_at AS trip_updated_at,
                       trip_driver.full_name AS trip_driver_name,
                       p.id AS payment_id,
                       p.payment_status AS payment_status,
                       p.updated_at AS payment_updated_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY b.cargo_id
                           ORDER BY CASE
                               WHEN b.booking_status IN ('pending', 'accepted') THEN 0
                               WHEN b.booking_status = 'cancelled' THEN 2
                               ELSE 1
                           END,
                           b.updated_at DESC,
                           b.id DESC
                       ) AS booking_rank
                FROM bookings b
                LEFT JOIN users booking_driver ON booking_driver.id = b.driver_id
                LEFT JOIN trips t ON t.booking_id = b.id
                LEFT JOIN users trip_driver ON trip_driver.id = t.driver_id
                LEFT JOIN payments p ON p.booking_id = b.id
                WHERE b.cargo_id IN (:freightIds)
            )
            SELECT freight_id AS "freightId",
                   booking_id AS "bookingId",
                   booking_status AS "bookingStatus",
                   booking_updated_at AS "bookingUpdatedAt",
                   driver_name AS "driverName",
                   trip_id AS "tripId",
                   trip_status AS "tripStatus",
                   trip_updated_at AS "tripUpdatedAt",
                   trip_driver_name AS "tripDriverName",
                   payment_id AS "paymentId",
                   payment_status AS "paymentStatus",
                   payment_updated_at AS "paymentUpdatedAt"
            FROM ranked_bookings
            WHERE booking_rank = 1
            """, nativeQuery = true)
    List<HistoryBookingProjection> findRepresentativeBookings(
            @Param("freightIds") Collection<Long> freightIds);
}
