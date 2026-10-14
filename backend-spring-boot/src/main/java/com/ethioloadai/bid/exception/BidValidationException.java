package com.ethioloadai.bid.exception;

import com.ethioloadai.exception.BaseException;
import java.math.BigDecimal;

public class BidValidationException extends BaseException {

    public BidValidationException(String message) {
        super(message, "BID_VALIDATION_ERROR", 400);
    }

    public static BidValidationException fixedPriceMismatch(BigDecimal budget) {
        return new BidValidationException(
            String.format("Fixed-price freight requires the exact posted price of ETB %s", 
                budget != null ? budget.toPlainString() : "0")
        );
    }

    public static BidValidationException freightNotAcceptingBids(String reason) {
        return new BidValidationException("This freight is not accepting bids: " + reason);
    }

    public static BidValidationException vehicleNotOwnedBy(Long vehicleId, String ownerType) {
        return new BidValidationException(
            String.format("Vehicle %d is not owned by the %s", vehicleId, ownerType)
        );
    }

    public static BidValidationException duplicateBid(Long freightId, Long vehicleId) {
        return new BidValidationException(
            String.format("Vehicle %d has already bid on this freight (freight %d); has already bid on freight", vehicleId, freightId)
        );
    }

    public static BidValidationException bidDeadlinePassed() {
        return new BidValidationException("The bid deadline has passed. Bidding is closed for this freight.");
    }

    public static BidValidationException availableDateTimeRequired() {
        return new BidValidationException("Please specify when you are available (available_datetime is required for intra-city bids).");
    }

    public static BidValidationException availableDateTimeFuture() {
        return new BidValidationException("available_datetime must be in the future.");
    }
}