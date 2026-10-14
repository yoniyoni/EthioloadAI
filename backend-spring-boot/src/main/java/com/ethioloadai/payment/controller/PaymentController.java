package com.ethioloadai.payment.controller;

import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.dto.ProcessPaymentRequest;
import com.ethioloadai.payment.service.PaymentService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the payment lifecycle.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   POST   /api/payments                   — initiate a payment (shipper/driver/admin)
 *   POST   /api/payments/{id}/complete     — admin confirms a manual cash payment
 *   POST   /api/payments/{id}/fail         — mark payment FAILED (admin only)
 *   GET    /api/payments/{id}              — get by payment ID
 *   GET    /api/payments/booking/{bookingId} — get by booking ID
 *   GET    /api/payments                   — list caller's payments
 * </pre>
 *
 * <p>All monetary amounts in responses are server-computed.
 * Client-supplied amount fields are rejected at the DTO level.
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment lifecycle management")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    // ── Initiate ──────────────────────────────────────────────────────────────

    /**
     * Initiate a payment for a DELIVERED booking.
     *
     * <p>The response amount is server-calculated from booking.estimatedPrice.
     * Client MUST NOT submit amount, commission, or driverNetAmount.
     */
    @PostMapping
    @Operation(summary = "Initiate payment",
               description = "Creates a PENDING payment for a delivered booking. " +
                             "Amount is server-authoritative; client supplies bookingId " +
                             "and paymentMethod only.")
    @PreAuthorize("hasAnyRole('SHIPPER','DRIVER','FLEET_OWNER','ADMIN')")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody ProcessPaymentRequest request,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/payments bookingId={} caller={}", request.getBookingId(), callerUser.getId());
        PaymentResponse response = paymentService.initiatePayment(request, callerUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    /**
     * Manually confirm a PENDING cash payment and close the associated booking.
     *
     * <p>Calling on an already-PAID payment returns 409.
     * This endpoint is not provider verification; only an administrator may use it.
     */
    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete payment",
               description = "Admin manually confirms a cash payment, transitioning PENDING → PAID " +
                             "and booking → COMPLETED. No external provider is verified.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> completePayment(
            @PathVariable Long id,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/payments/{}/complete caller={}", id, callerUser.getId());
        PaymentResponse response = paymentService.completePayment(id, callerUser);
        return ResponseEntity.ok(response);
    }

    // ── Fail (admin only) ─────────────────────────────────────────────────────

    /**
     * Record a payment failure. Admin-only.
     * Transitions PENDING → FAILED.
     */
    @PostMapping("/{id}/fail")
    @Operation(summary = "Record payment failure (admin)",
               description = "Transitions PENDING → FAILED. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> failPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal User callerUser) {

        log.info("POST /api/payments/{}/fail caller={}", id, callerUser.getId());
        PaymentResponse response = paymentService.failPayment(id, callerUser);
        return ResponseEntity.ok(response);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    @PreAuthorize("hasAnyRole('SHIPPER','DRIVER','FLEET_OWNER','ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable Long id,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(paymentService.getPaymentById(id, callerUser));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking ID")
    @PreAuthorize("hasAnyRole('SHIPPER','DRIVER','FLEET_OWNER','ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentByBookingId(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(paymentService.getPaymentByBookingId(bookingId, callerUser));
    }

    @GetMapping("/freight/{freightId}")
    @Operation(summary = "Get payment by freight ID",
               description = "Retrieves the payment associated with the active booking of a freight shipment.")
    @PreAuthorize("hasAnyRole('SHIPPER','DRIVER','FLEET_OWNER','ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentByFreightId(
            @PathVariable Long freightId,
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(paymentService.getPaymentByFreightId(freightId, callerUser));
    }

    @GetMapping
    @Operation(summary = "List payments for the authenticated caller")
    @PreAuthorize("hasAnyRole('SHIPPER','DRIVER','FLEET_OWNER','ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal User callerUser) {

        return ResponseEntity.ok(paymentService.getPaymentsForCaller(callerUser));
    }
}
