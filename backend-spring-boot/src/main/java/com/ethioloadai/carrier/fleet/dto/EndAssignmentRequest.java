package com.ethioloadai.carrier.fleet.dto;

import lombok.Getter;
import lombok.Setter;

/** Optional body for ending an assignment. */
@Getter @Setter
public class EndAssignmentRequest {
    /** Optional reason / notes for ending the assignment. */
    private String notes;
}
