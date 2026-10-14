package com.ethioloadai.history.repository;

import java.time.LocalDateTime;

public interface HistoryBookingProjection {
    Long getFreightId();
    Long getBookingId();
    String getBookingStatus();
    LocalDateTime getBookingUpdatedAt();
    String getDriverName();
    Long getTripId();
    String getTripStatus();
    LocalDateTime getTripUpdatedAt();
    String getTripDriverName();
    Long getPaymentId();
    String getPaymentStatus();
    LocalDateTime getPaymentUpdatedAt();
}
