package com.ethioloadai.delivery.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Typed exceptions for Proof of Delivery operations.
 */
public final class ProofOfDeliveryException extends RuntimeException {

    private ProofOfDeliveryException(String message) {
        super(message);
    }

    // ── Not found ─────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public static NotFoundException notFound(Long proofId) {
        return new NotFoundException("Proof of delivery not found: " + proofId);
    }

    public static NotFoundException notFoundForTrip(Long tripId) {
        return new NotFoundException("No proof of delivery found for trip: " + tripId);
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AuthorizationException extends RuntimeException {
        public AuthorizationException(String msg) { super(msg); }
    }

    public static AuthorizationException unauthorized(String reason) {
        return new AuthorizationException("Proof of delivery access denied: " + reason);
    }

    // ── Invalid state / business rule ─────────────────────────────────────────

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String msg) { super(msg); }
    }

    public static InvalidStateException invalidState(String reason) {
        return new InvalidStateException(reason);
    }

    public static InvalidStateException alreadySubmitted(Long tripId) {
        return new InvalidStateException(
                "A proof of delivery already exists for trip " + tripId +
                ". Reject the current proof before resubmitting.");
    }

    public static InvalidStateException tripNotArrived(Long tripId) {
        return new InvalidStateException(
                "Trip " + tripId + " must be in ARRIVED status before submitting proof.");
    }

    public static InvalidStateException alreadyVerified(Long proofId) {
        return new InvalidStateException(
                "Proof " + proofId + " is already VERIFIED and cannot be modified.");
    }

    // ── File validation ───────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class FileValidationException extends RuntimeException {
        public FileValidationException(String msg) { super(msg); }
    }

    public static FileValidationException invalidFileType(String contentType) {
        return new FileValidationException(
                "Unsupported file type: " + contentType +
                ". Allowed: image/jpeg, image/png, image/webp, application/pdf");
    }

    public static FileValidationException fileTooLarge(long sizeBytes, long maxBytes) {
        return new FileValidationException(
                String.format("File size %d bytes exceeds maximum %d bytes", sizeBytes, maxBytes));
    }

    public static FileValidationException emptyFile() {
        return new FileValidationException("Uploaded file is empty");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class MetadataValidationException extends RuntimeException {
        public MetadataValidationException(String msg) { super(msg); }
    }

    public static MetadataValidationException invalidMetadata(String message) {
        return new MetadataValidationException(message);
    }
}
