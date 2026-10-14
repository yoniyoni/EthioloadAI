package com.ethioloadai.booking.controller;

import com.ethioloadai.booking.dto.AdminUnpaidBookingsListResponse;
import com.ethioloadai.booking.dto.CashPaymentSettlementResponse;
import com.ethioloadai.booking.dto.BookingResponse;
import com.ethioloadai.booking.dto.BookingTripResponse;
import com.ethioloadai.booking.dto.BookingPageResponse;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.payment.dto.PaymentResponse;
import com.ethioloadai.payment.service.PaymentService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

/**
 * Controller handling booking endpoints, including admin unpaid bookings listing
 * and cash payment settlement.
 *
 * Mapped to both /api/admin/bookings and /api/bookings to support legacy and canonical routes.
 */
@RestController
@RequestMapping({"/admin/bookings", "/bookings"})
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Booking lifecycle and administrative settlement")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final BookingService bookingService;
    private final PaymentService paymentService;

    /**
     * Admin: retrieve bookings without an existing payment record.
     * Excludes cancelled bookings.
     */
    @GetMapping("/unpaid")
    @Operation(summary = "List unpaid bookings (admin)",
               description = "Returns a page of non-cancelled bookings that have no payment record. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUnpaidBookingsListResponse> getUnpaidBookings(
            @AuthenticationPrincipal User adminUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/admin/bookings/unpaid requested by admin={}", adminUser.getId());
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize);
        return ResponseEntity.ok(bookingService.getUnpaidBookings(adminUser, pageable));
    }

    /**
     * Admin: record a cash payment directly on an unpaid booking.
     * Creates a payment row with method 'cash', status 'PAID', and marks the booking CONFIRMED.
     */
    @PostMapping("/{id}/mark-cash-paid")
    @Operation(summary = "Mark cash payment paid (admin)",
               description = "Records a cash payment for an unpaid booking, setting payment to PAID and booking to CONFIRMED. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CashPaymentSettlementResponse> markCashPaid(
            @PathVariable Long id,
            @AuthenticationPrincipal User adminUser) {

        log.info("POST /api/admin/bookings/{}/mark-cash-paid requested by admin={}", id, adminUser.getId());
        PaymentResponse payment = paymentService.settleCashPayment(id, adminUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CashPaymentSettlementResponse("Cash payment recorded successfully", payment));
    }

    /**
     * Get booking details by ID with user authorization.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get booking by ID", description = "Returns booking details for authorized user")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        log.info("GET /api/bookings/{} requested by user={}", id, user != null ? user.getId() : null);
        return ResponseEntity.ok(bookingService.getBookingResponseWithAuthorization(id, user));
    }

    @GetMapping("/{id}/trip")
    @Operation(
            summary = "Resolve a booking's trip",
            description = "Returns the trip associated with one authorized booking, or a response with a null tripId when none exists.")
    public ResponseEntity<BookingTripResponse> getBookingTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getBookingTrip(id, user));
    }

    @GetMapping("/page")
    @Operation(summary = "List a bounded page of visible bookings",
            description = "Returns bookings visible to the authenticated caller. Only admins may filter by driver.")
    public ResponseEntity<BookingPageResponse> getBookingsPage(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long driverId) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        if (driverId != null && user.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("Only admins may filter by driverId");
        }
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(bookingService.getBookingsPageForCaller(user, pageable, driverId));
    }

    @GetMapping
    @Operation(summary = "List current user's bookings", description = "Returns only bookings visible to the authenticated caller")
    public ResponseEntity<List<BookingResponse>> getBookings(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.getBookingsForCaller(user));
    }
}
