package com.ethioloadai.dispute.dto;

import java.util.List;

public record DisputeListResponse(
        List<DisputeResponse> disputes,
        long total
) {
    public static DisputeListResponse of(List<DisputeResponse> disputes) {
        return new DisputeListResponse(disputes, disputes != null ? disputes.size() : 0);
    }

    public static DisputeListResponse of(List<DisputeResponse> disputes, long total) {
        return new DisputeListResponse(disputes, total);
    }
}
