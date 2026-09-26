package com.ethioloadai.delivery.service;

import com.ethioloadai.delivery.dto.ProofOfDeliveryResponse;
import com.ethioloadai.delivery.dto.VerifyDeliveryRequest;
import com.ethioloadai.user.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 7 — Proof of Delivery lifecycle.
 */
public interface ProofOfDeliveryService {

    /**
     * Driver submits proof for their active trip.
     *
     * @param tripId       the trip (must be ARRIVED)
     * @param file         proof file (image or PDF)
     * @param recipientName optional name of the person who received the goods
     * @param note         optional delivery note
     * @param driver       authenticated driver (must be the assigned trip driver)
     */
    ProofOfDeliveryResponse submitProof(Long tripId, MultipartFile file,
                                        String recipientName, String note,
                                        User driver);

    /**
     * Authenticated user downloads the proof file.
     * Authorization verified before returning the resource.
     *
     * @param proofId the proof to download
     * @param caller  must be the shipper, assigned driver, carrier member, or admin
     */
    com.ethioloadai.document.storage.DocumentStorage.StoredDocument downloadProof(
            Long proofId, User caller);

    /**
     * Retrieve proof metadata (no file content).
     *
     * @param tripId the trip
     * @param caller must be authorized
     */
    ProofOfDeliveryResponse getProofForTrip(Long tripId, User caller);

    /**
     * Retrieve proof by ID with authorization check.
     */
    ProofOfDeliveryResponse getProofById(Long proofId, User caller);

    /**
     * Shipper (or admin) verifies or rejects a submitted proof.
     *
     * <ul>
     *   <li>VERIFY → triggers trip completion + settlement creation</li>
     *   <li>REJECT → reverts trip to ARRIVED; driver may resubmit</li>
     * </ul>
     *
     * @param proofId the proof to act on
     * @param request VERIFY or REJECT with optional reason
     * @param caller  must be the freight shipper or ADMIN
     */
    ProofOfDeliveryResponse verifyOrRejectProof(Long proofId,
                                                VerifyDeliveryRequest request,
                                                User caller);
}
