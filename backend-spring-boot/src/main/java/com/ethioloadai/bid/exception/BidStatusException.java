package com.ethioloadai.bid.exception;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.exception.BaseException;

public class BidStatusException extends BaseException {

    public BidStatusException(String message) {
        super(message, "BID_STATUS_INVALID", 422);
    }

    public static BidStatusException invalidTransition(Bid.BidStatus from, Bid.BidStatus to) {
        return new BidStatusException(
            String.format("Cannot transition bid from %s to %s", from, to)
        );
    }

    public static BidStatusException alreadyFinal(Bid.BidStatus status) {
        return new BidStatusException(
            String.format("Bid is already in final state: %s", status)
        );
    }

    public static BidStatusException notEligibleFor(String action, Bid.BidStatus status) {
        return new BidStatusException(
            String.format("Cannot %s bid: bid is not eligible to %s in status: %s", action, action, status)
        );
    }
}