package com.ethioloadai.bid.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BidPageResponse {
    List<BidResponse> content;
    int page;
    int size;
    boolean hasNext;
    Integer nextPage;
    Long recommendedBidId;
    long pendingCount;
}
