package com.ethioloadai.dispute.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DisputeStatus {
    OPEN("open"),
    UNDER_REVIEW("under_review"),
    RESOLVED("resolved"),
    CLOSED("closed");

    private final String value;

    DisputeStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DisputeStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        if ("investigating".equals(normalized)) {
            return UNDER_REVIEW;
        }
        for (DisputeStatus status : DisputeStatus.values()) {
            if (status.value.equalsIgnoreCase(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown dispute status: " + text);
    }
}
