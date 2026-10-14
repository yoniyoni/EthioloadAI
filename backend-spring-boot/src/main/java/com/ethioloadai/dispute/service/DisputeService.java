package com.ethioloadai.dispute.service;

import com.ethioloadai.dispute.dto.CreateDisputeRequest;
import com.ethioloadai.dispute.dto.DisputeListResponse;
import com.ethioloadai.dispute.dto.DisputeResponse;
import com.ethioloadai.dispute.dto.ResolveDisputeRequest;
import com.ethioloadai.user.entity.User;

public interface DisputeService {

    /** File a new dispute for a freight shipment. */
    DisputeResponse createDispute(CreateDisputeRequest request, User caller);

    /** Get all disputes for a specific freight shipment (participant or admin). */
    DisputeListResponse getDisputesByFreightId(Long freightId, User caller);

    /** Get all disputes across the platform (admin only). */
    DisputeListResponse getAllDisputes(User caller);

    /** Adjudicate and resolve a dispute (admin only). */
    DisputeResponse resolveDispute(Long disputeId, ResolveDisputeRequest request, User caller);
}
