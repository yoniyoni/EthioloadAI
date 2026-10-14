package com.ethioloadai.bid.exception;

import com.ethioloadai.exception.BaseException;

public class BidNotFoundException extends BaseException {

    public BidNotFoundException(Long bidId) {
        super("Bid not found with ID: " + bidId, "BID_NOT_FOUND", 404);
    }
}