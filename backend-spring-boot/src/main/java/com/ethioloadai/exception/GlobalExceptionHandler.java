package com.ethioloadai.exception;

import com.ethioloadai.carrier.fleet.exception.CarrierFleetException;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.payment.exception.PaymentException;
import com.ethioloadai.document.exception.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        log.error("BaseException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(ex.getMessage(), ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.error("AuthenticationException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(ex.getMessage(), ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException ex) {
        log.error("AuthorizationException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(ex.getMessage(), ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        log.error("ValidationException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(ex.getMessage(), ex.getCode(), ex.getErrors());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("Request field validation failed");
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        String message = errors.values().iterator().next();
        ErrorResponse response = new ErrorResponse(message, "VALIDATION_ERROR", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        log.error("BadCredentialsException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
            "Invalid credentials. Check your email/phone and password.",
            "AUTH_ERROR"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledException(DisabledException ex) {
        log.error("DisabledException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
            "Account is inactive. Please contact support.",
            "ACCOUNT_DISABLED"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("AccessDeniedException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse("Forbidden", "FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.error("MethodArgumentTypeMismatchException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse("Invalid parameter type", "INVALID_PARAMETER");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("IllegalArgumentException: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(ex.getMessage(), "INVALID_ARGUMENT");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ── Payment exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(PaymentException.DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayment(PaymentException.DuplicatePaymentException ex) {
        log.warn("DuplicatePaymentException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), "DUPLICATE_PAYMENT"));
    }

    @ExceptionHandler(PaymentException.BookingNotDeliveredException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotDelivered(PaymentException.BookingNotDeliveredException ex) {
        log.warn("BookingNotDeliveredException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getMessage(), "BOOKING_NOT_DELIVERED"));
    }

    @ExceptionHandler(PaymentException.InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(PaymentException.InvalidStatusTransitionException ex) {
        log.warn("InvalidStatusTransitionException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), "INVALID_STATUS_TRANSITION"));
    }

    @ExceptionHandler(PaymentException.PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentException.PaymentNotFoundException ex) {
        log.warn("PaymentNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), "PAYMENT_NOT_FOUND"));
    }

    @ExceptionHandler(PaymentException.PaymentAuthorizationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentAuthorization(PaymentException.PaymentAuthorizationException ex) {
        log.warn("PaymentAuthorizationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(ex.getMessage(), "PAYMENT_FORBIDDEN"));
    }

    @ExceptionHandler(PaymentException.InvalidPaymentAmountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAmount(PaymentException.InvalidPaymentAmountException ex) {
        log.error("InvalidPaymentAmountException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getMessage(), "INVALID_PAYMENT_AMOUNT"));
    }

    // ── Carrier fleet exceptions (Phase 4) ───────────────────────────────────

    @ExceptionHandler(CarrierFleetException.class)
    public ResponseEntity<ErrorResponse> handleCarrierFleet(CarrierFleetException ex) {
        log.warn("CarrierFleetException [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
            .body(new ErrorResponse(ex.getMessage(), ex.getCode()));
    }

    // ── Fleet exceptions ──────────────────────────────────────────────────────

    @ExceptionHandler(FleetException.FleetResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFleetNotFound(FleetException.FleetResourceNotFoundException ex) {
        log.warn("FleetResourceNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), "FLEET_RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(FleetException.FleetAuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleFleetAuthorization(FleetException.FleetAuthorizationException ex) {
        log.warn("FleetAuthorizationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(ex.getMessage(), "FLEET_FORBIDDEN"));
    }

    @ExceptionHandler(FleetException.FleetValidationException.class)
    public ResponseEntity<ErrorResponse> handleFleetValidation(FleetException.FleetValidationException ex) {
        log.warn("FleetValidationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getMessage(), "FLEET_VALIDATION_ERROR"));
    }

    @ExceptionHandler(FleetException.ResourceAlreadyAssignedException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyAssigned(FleetException.ResourceAlreadyAssignedException ex) {
        log.warn("ResourceAlreadyAssignedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), "FLEET_RESOURCE_ALREADY_ASSIGNED"));
    }

    // ── Document exceptions ───────────────────────────────────────────────────

    @ExceptionHandler(DocumentException.DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentException.DocumentNotFoundException ex) {
        log.warn("DocumentNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage(), "DOCUMENT_NOT_FOUND"));
    }

    @ExceptionHandler(DocumentException.DocumentAuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleDocumentAuthorization(DocumentException.DocumentAuthorizationException ex) {
        log.warn("DocumentAuthorizationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse(ex.getMessage(), "DOCUMENT_FORBIDDEN"));
    }

    @ExceptionHandler(DocumentException.InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleDocumentInvalidTransition(DocumentException.InvalidStatusTransitionException ex) {
        log.warn("InvalidStatusTransitionException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.getMessage(), "INVALID_DOCUMENT_TRANSITION"));
    }

    @ExceptionHandler(DocumentException.DocumentValidationException.class)
    public ResponseEntity<ErrorResponse> handleDocumentValidation(DocumentException.DocumentValidationException ex) {
        log.warn("DocumentValidationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getMessage(), "DOCUMENT_VALIDATION_ERROR"));
    }

    @ExceptionHandler(DocumentException.KYCIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleKYCIncomplete(DocumentException.KYCIncompleteException ex) {
        log.warn("KYCIncompleteException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getMessage(), "KYC_INCOMPLETE"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Request JSON could not be parsed");
        // Extract a user-friendly message from Jackson parse failures (e.g. bad date format, unknown enum)
        String message = "Invalid request body: malformed JSON or unrecognised field value.";
        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            message = "Invalid value for field '" + ife.getPath().stream()
                    .map(r -> r.getFieldName())
                    .filter(n -> n != null)
                    .reduce((a, b) -> a + "." + b)
                    .orElse("unknown")
                    + "': " + ife.getValue();
        } else if (cause instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException upe) {
            message = "Unknown field: '" + upe.getPropertyName() + "'";
        }
        ErrorResponse response = new ErrorResponse(message, "INVALID_REQUEST_BODY");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ── Phase 7: Proof of Delivery exceptions ────────────────────────────────

    @ExceptionHandler(com.ethioloadai.delivery.exception.ProofOfDeliveryException.NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProofNotFound(
            com.ethioloadai.delivery.exception.ProofOfDeliveryException.NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage(), "PROOF_NOT_FOUND"));
    }

    @ExceptionHandler(com.ethioloadai.delivery.exception.ProofOfDeliveryException.AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleProofForbidden(
            com.ethioloadai.delivery.exception.ProofOfDeliveryException.AuthorizationException ex) {
        log.warn("ProofOfDelivery authorization: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage(), "PROOF_FORBIDDEN"));
    }

    @ExceptionHandler(com.ethioloadai.delivery.exception.ProofOfDeliveryException.InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleProofInvalidState(
            com.ethioloadai.delivery.exception.ProofOfDeliveryException.InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage(), "PROOF_INVALID_STATE"));
    }

    @ExceptionHandler(com.ethioloadai.delivery.exception.ProofOfDeliveryException.FileValidationException.class)
    public ResponseEntity<ErrorResponse> handleProofFileValidation(
            com.ethioloadai.delivery.exception.ProofOfDeliveryException.FileValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage(), "PROOF_FILE_INVALID"));
    }

    // ── Phase 7: Settlement exceptions ────────────────────────────────────────

    @ExceptionHandler(com.ethioloadai.settlement.exception.SettlementException.NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSettlementNotFound(
            com.ethioloadai.settlement.exception.SettlementException.NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage(), "SETTLEMENT_NOT_FOUND"));
    }

    @ExceptionHandler(com.ethioloadai.settlement.exception.SettlementException.AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleSettlementForbidden(
            com.ethioloadai.settlement.exception.SettlementException.AuthorizationException ex) {
        log.warn("Settlement authorization: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage(), "SETTLEMENT_FORBIDDEN"));
    }

    @ExceptionHandler(com.ethioloadai.settlement.exception.SettlementException.InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleSettlementInvalidState(
            com.ethioloadai.settlement.exception.SettlementException.InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage(), "SETTLEMENT_INVALID_STATE"));
    }

    @ExceptionHandler(com.ethioloadai.settlement.exception.SettlementException.DuplicateSettlementException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSettlement(
            com.ethioloadai.settlement.exception.SettlementException.DuplicateSettlementException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage(), "DUPLICATE_SETTLEMENT"));
    }

    // ── Phase 7: Rating exceptions ────────────────────────────────────────────

    @ExceptionHandler(com.ethioloadai.rating.exception.RatingException.AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleRatingForbidden(
            com.ethioloadai.rating.exception.RatingException.AuthorizationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage(), "RATING_FORBIDDEN"));
    }

    @ExceptionHandler(com.ethioloadai.rating.exception.RatingException.InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleRatingInvalidState(
            com.ethioloadai.rating.exception.RatingException.InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage(), "RATING_INVALID_STATE"));
    }

    @ExceptionHandler(com.ethioloadai.rating.exception.RatingException.AlreadyRatedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyRated(
            com.ethioloadai.rating.exception.RatingException.AlreadyRatedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage(), "ALREADY_RATED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ErrorResponse response = new ErrorResponse("Internal server error", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    public record ErrorResponse(
        String message,
        String code,
        Map<String, String> errors
    ) {
        public ErrorResponse(String message, String code) {
            this(message, code, null);
        }
    }
}
