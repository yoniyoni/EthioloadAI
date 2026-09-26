package com.ethioloadai.document.exception;

import com.ethioloadai.document.entity.DriverDocument;

/**
 * Domain exceptions for the driver document KYC workflow.
 */
public class DocumentException extends RuntimeException {

    public DocumentException(String message) {
        super(message);
    }

    // ── Concrete subtypes ─────────────────────────────────────────────────────

    /** Thrown when a document is not found or access is denied. */
    public static class DocumentNotFoundException extends DocumentException {
        public DocumentNotFoundException(Long documentId) {
            super("Document not found: " + documentId);
        }
        public DocumentNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when the caller is not authorized to access/modify a document. */
    public static class DocumentAuthorizationException extends DocumentException {
        public DocumentAuthorizationException(String message) {
            super(message);
        }
    }

    /** Thrown when a state transition is not permitted. */
    public static class InvalidStatusTransitionException extends DocumentException {
        public InvalidStatusTransitionException(DriverDocument.DocumentStatus from,
                                                DriverDocument.DocumentStatus to) {
            super(String.format("Document status transition %s → %s is not permitted", from, to));
        }
    }

    /** Thrown when document validation fails. */
    public static class DocumentValidationException extends DocumentException {
        public DocumentValidationException(String message) {
            super(message);
        }
    }

    /** Thrown when KYC activation requirements are not met. */
    public static class KYCIncompleteException extends DocumentException {
        public KYCIncompleteException(Long userId, long approvedCount) {
            super(String.format(
                "Driver %d cannot be activated: only %d/5 documents approved",
                userId, approvedCount));
        }
    }
}
