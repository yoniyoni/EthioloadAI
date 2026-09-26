package com.ethioloadai.document.service;

import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.exception.DocumentException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict document status state machine.
 *
 * <p>Permitted transitions (derived from the established KYC workflow):
 *
 * <pre>
 *   PENDING  → APPROVED   (admin approval)
 *   PENDING  → REJECTED   (admin rejection)
 *   REJECTED → PENDING    (driver re-submits after fixing issues)
 *   APPROVED → REJECTED   (admin revokes approval due to fraud/invalid document)
 *   APPROVED → (none)     terminal unless revoked
 *   REJECTED → APPROVED   (forbidden — must go through PENDING review again)
 * </pre>
 *
 * The Laravel reference does not implement re-submission, but the V12 migration
 * comment mentions "re-uploading replaces the existing row", so REJECTED → PENDING
 * is supported via re-submission (new file upload creates a fresh PENDING record).
 */
public final class DocumentStateMachine {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED =
        new EnumMap<>(DocumentStatus.class);

    static {
        // PENDING can be approved or rejected by admin
        ALLOWED.put(DocumentStatus.PENDING, EnumSet.of(DocumentStatus.APPROVED, DocumentStatus.REJECTED));
        
        // APPROVED can be revoked to REJECTED (fraud detected, invalid doc)
        // but cannot go back to PENDING (that would bypass review)
        ALLOWED.put(DocumentStatus.APPROVED, EnumSet.of(DocumentStatus.REJECTED));
        
        // REJECTED documents can be re-submitted → PENDING
        // (new upload creates fresh PENDING record, not a status change on existing record)
        // Direct REJECTED → APPROVED is forbidden (must be reviewed again)
        ALLOWED.put(DocumentStatus.REJECTED, EnumSet.of(DocumentStatus.PENDING));
    }

    private DocumentStateMachine() {}

    /**
     * Validate and enforce a transition. Throws if the transition is not permitted.
     *
     * @param from current status
     * @param to   requested status
     * @throws DocumentException.InvalidStatusTransitionException when forbidden
     */
    public static void assertTransitionAllowed(DocumentStatus from, DocumentStatus to) {
        Set<DocumentStatus> permitted = ALLOWED.getOrDefault(from, EnumSet.noneOf(DocumentStatus.class));
        if (!permitted.contains(to)) {
            throw new DocumentException.InvalidStatusTransitionException(from, to);
        }
    }

    /**
     * @return true if the given transition is permitted without throwing
     */
    public static boolean isTransitionAllowed(DocumentStatus from, DocumentStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(DocumentStatus.class)).contains(to);
    }

    /**
     * @return true if the status is terminal (no transitions allowed from this state)
     */
    public static boolean isTerminal(DocumentStatus status) {
        // Note: APPROVED and REJECTED are NOT terminal in this domain
        // APPROVED can be revoked; REJECTED can be re-submitted
        return false;
    }
}
