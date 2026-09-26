package com.ethioloadai.bid.service;

import java.math.BigDecimal;

public interface CommissionService {

    /**
     * Calculate platform commission for a bid amount.
     *
     * @param amount the bid/booking amount
     * @param isFleetBooking true if this is a fleet owner booking
     * @return commission amount
     */
    BigDecimal calculateCommission(BigDecimal amount, boolean isFleetBooking);

    /**
     * Get the commission rate for a booking type.
     *
     * @param isFleetBooking true if this is a fleet owner booking
     * @return commission rate as decimal (0.10 for 10%)
     */
    BigDecimal getCommissionRate(boolean isFleetBooking);
}