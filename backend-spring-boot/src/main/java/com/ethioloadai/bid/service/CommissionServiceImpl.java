package com.ethioloadai.bid.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CommissionServiceImpl implements CommissionService {
    private static final Logger log = LoggerFactory.getLogger(CommissionServiceImpl.class);

    // Commission rates based on Laravel implementation
    private static final BigDecimal STANDARD_COMMISSION_RATE = new BigDecimal("0.10"); // 10%
    private static final BigDecimal FLEET_COMMISSION_RATE = new BigDecimal("0.05");     // 5%

    @Override
    public BigDecimal calculateCommission(BigDecimal amount, boolean isFleetBooking) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = getCommissionRate(isFleetBooking);
        BigDecimal commission = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        
        log.debug("Calculated commission: {} ({}% of {})", commission, 
                rate.multiply(new BigDecimal("100")), amount);
        
        return commission;
    }

    @Override
    public BigDecimal getCommissionRate(boolean isFleetBooking) {
        return isFleetBooking ? FLEET_COMMISSION_RATE : STANDARD_COMMISSION_RATE;
    }
}