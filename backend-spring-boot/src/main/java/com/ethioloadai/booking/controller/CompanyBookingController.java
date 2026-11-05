package com.ethioloadai.booking.controller;

import com.ethioloadai.booking.dto.CompanyBookingResponse;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.service.CompanyBookingService;
import com.ethioloadai.user.entity.User;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/carrier-companies/{companyId}/bookings")
@RequiredArgsConstructor
public class CompanyBookingController {

    private static final int MAX_PAGE_SIZE = 100;
    private final CompanyBookingService companyBookingService;

    @GetMapping
    public ResponseEntity<Page<CompanyBookingResponse>> getBookings(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) Booking.BookingStatus status,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(companyBookingService.getBookings(
                companyId,
                status,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
                caller));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<CompanyBookingResponse> getBooking(
            @PathVariable Long companyId,
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(companyBookingService.getBooking(companyId, bookingId, caller));
    }
}
