package com.ethioloadai.bid.controller;

import com.ethioloadai.bid.dto.*;
import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.mapper.BidMapper;
import com.ethioloadai.bid.service.BidService;
import com.ethioloadai.booking.dto.BookingResponse;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.service.FreightService;
import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Validated
@Tag(name = "Bid Management", description = "Bid management endpoints")
public class BidController {
    private static final Logger log = LoggerFactory.getLogger(BidController.class);

    private final BidService bidService;
    private final FreightService freightService;
    private final BidMapper bidMapper;
    private final BookingService bookingService;

    private static final int MAX_PAGE_SIZE = 100;

    @PostMapping({"/freight/{freightId}/bids", "/cargo-requests/{freightId}/bids"})
    @Operation(summary = "Create a bid on freight", 
               description = "Driver or fleet owner creates a bid on a freight request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bid created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid bid data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to bid"),
        @ApiResponse(responseCode = "404", description = "Freight not found"),
        @ApiResponse(responseCode = "422", description = "Business validation failed")
    })
    public ResponseEntity<BidResponse> createBid(
            @PathVariable Long freightId,
            @Valid @RequestBody CreateBidRequest request,
            Authentication authentication) {
        
        log.info("Creating bid on freight {} by user {}", freightId, getUserId(authentication));

        // Get authenticated user
        User user = getUserFromAuthentication(authentication);
        
        // Create bid (service will validate freight exists and user can access it)
        Bid bid = bidService.createBid(
                freightId, 
                user, 
                request.getVehicleId(), 
                request.getAmount(), 
                request.getNote(), 
                request.getAvailableDatetime()
        );

        BidResponse response = bidMapper.toResponse(bid);
        log.info("Bid created successfully - ID: {}", bid.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/freight/{freightId}/bids", "/cargo-requests/{freightId}/bids"})
    @Operation(summary = "Get all bids for freight", 
               description = "Shipper or admin can view all bids for their freight")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bids retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view these bids"),
        @ApiResponse(responseCode = "404", description = "Freight not found")
    })
    public ResponseEntity<List<BidResponse>> getBidsForFreight(
            @PathVariable Long freightId,
            Authentication authentication) {
        
        log.info("Getting bids for freight {} by user {}", freightId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        
        List<Bid> bids = bidService.getBidsForFreight(freightId, user);
        List<Bid> rankedBids = bidService.rankBids(bids);
        
        List<BidResponse> responses = rankedBids.stream()
                .map(bidMapper::toResponse)
                .toList();
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/driver/bids")
    @PreAuthorize("hasRole('DRIVER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Get driver's own bids", 
               description = "Driver can view their own bid history with pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Driver bids retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Page<BidResponse>> getDriverBids(
            @Parameter(description = "Page number (0-based, default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 20, max " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        Long driverId = getUserId(authentication);
        log.info("Getting bids for driver {}", driverId);

        // Validate page size
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Bid> bids = bidService.getDriverBids(driverId, pageable);
        
        Page<BidResponse> responses = bids.map(bidMapper::toResponse);
        
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/bids/{bidId}")
    @Transactional(readOnly = true)
    @Operation(summary = "Get specific bid", 
               description = "Get bid details (authorized users only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bid retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to view this bid"),
        @ApiResponse(responseCode = "404", description = "Bid not found")
    })
    public ResponseEntity<BidResponse> getBid(
            @PathVariable Long bidId,
            Authentication authentication) {
        
        log.info("Getting bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        Bid bid = bidService.getBidWithAuthorization(bidId, user);
        
        BidResponse response = bidMapper.toResponse(bid);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bids/{bidId}/counter")
    @Operation(summary = "Create counter-offer on bid", 
               description = "Shipper or driver creates a counter-offer")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Counter-offer created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid counter-offer data"),
        @ApiResponse(responseCode = "403", description = "Not authorized to counter this bid"),
        @ApiResponse(responseCode = "404", description = "Bid not found"),
        @ApiResponse(responseCode = "422", description = "Counter-offer not allowed in current state")
    })
    @Transactional
    public ResponseEntity<BidResponse> createCounterOffer(
            @PathVariable Long bidId,
            @Valid @RequestBody CounterOfferRequest request,
            Authentication authentication) {
        
        log.info("Creating counter-offer on bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        Bid bid = bidService.getBidWithAuthorization(bidId, user);
        
        Bid updatedBid = bidService.createCounterOffer(
                bid, 
                user, 
                request.getCounterAmount(), 
                request.getCounterNote()
        );

        BidResponse response = bidMapper.toResponse(updatedBid);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bids/{bidId}/withdraw")
    @Transactional
    @Operation(summary = "Withdraw bid", 
               description = "Driver withdraws their own bid")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bid withdrawn successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to withdraw this bid"),
        @ApiResponse(responseCode = "404", description = "Bid not found"),
        @ApiResponse(responseCode = "422", description = "Bid cannot be withdrawn in current state")
    })
    public ResponseEntity<BidResponse> withdrawBid(
            @PathVariable Long bidId,
            Authentication authentication) {
        
        log.info("Withdrawing bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        Bid bid = bidService.getBidWithAuthorization(bidId, user);
        
        Bid updatedBid = bidService.withdrawBid(bid, user);

        BidResponse response = bidMapper.toResponse(updatedBid);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bids/{bidId}/reject")
    @Transactional
    @Operation(summary = "Reject bid", 
               description = "Shipper rejects a bid on their freight")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bid rejected successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to reject this bid"),
        @ApiResponse(responseCode = "404", description = "Bid not found"),
        @ApiResponse(responseCode = "422", description = "Bid cannot be rejected in current state")
    })
    public ResponseEntity<BidResponse> rejectBid(
            @PathVariable Long bidId,
            Authentication authentication) {
        
        log.info("Rejecting bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        Bid bid = bidService.getBidWithAuthorization(bidId, user);
        
        Bid updatedBid = bidService.rejectBid(bid, user);

        BidResponse response = bidMapper.toResponse(updatedBid);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bids/{bidId}/accept")
    @Operation(summary = "Accept bid (CRITICAL - Concurrency Safe)", 
               description = "Shipper accepts a bid and creates booking transactionally. Uses pessimistic locking to prevent race conditions.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bid accepted and booking created successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to accept this bid"),
        @ApiResponse(responseCode = "404", description = "Bid not found"),
        @ApiResponse(responseCode = "422", description = "Bid cannot be accepted in current state or freight unavailable")
    })
    public ResponseEntity<BookingResponse> acceptBid(
            @PathVariable Long bidId,
            Authentication authentication) {
        
        log.info("Accepting bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        
        // CRITICAL: This uses pessimistic locking to fix Laravel's race condition
        Booking booking = bookingService.acceptBid(bidId, user);

        BookingResponse response = convertBookingToResponse(booking);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bids/{bidId}/accept-counter")
    @Operation(summary = "Accept counter-offer (CRITICAL - Concurrency Safe)", 
               description = "Accept the standing counter-offer and create booking transactionally")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Counter-offer accepted and booking created successfully"),
        @ApiResponse(responseCode = "403", description = "Not authorized to accept this counter-offer"),
        @ApiResponse(responseCode = "404", description = "Bid not found"),
        @ApiResponse(responseCode = "422", description = "No valid counter-offer to accept or freight unavailable")
    })
    public ResponseEntity<BookingResponse> acceptCounterOffer(
            @PathVariable Long bidId,
            Authentication authentication) {
        
        log.info("Accepting counter-offer on bid {} by user {}", bidId, getUserId(authentication));

        User user = getUserFromAuthentication(authentication);
        
        // CRITICAL: This uses pessimistic locking to fix Laravel's race condition
        Booking booking = bookingService.acceptCounterOffer(bidId, user);

        BookingResponse response = convertBookingToResponse(booking);
        return ResponseEntity.ok(response);
    }

    // Helper methods

    private User getUserFromAuthentication(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    private Long getUserId(Authentication authentication) {
        return getUserFromAuthentication(authentication).getId();
    }

    private BookingResponse convertBookingToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .freightId(booking.getCargoRequest().getId())
                .freightDescription(booking.getCargoRequest().getCargoDescription())
                .vehicleId(booking.getVehicle().getId())
                .vehiclePlateNumber(booking.getVehicle().getPlateNumber())
                .vehicleType(booking.getVehicle().getTruckType())
                .driverId(booking.getDriver().getId())
                .driverName(booking.getDriver().getFullName())
                .bidId(booking.getBid() != null ? booking.getBid().getId() : null)
                .bookingStatus(booking.getBookingStatus().name().toLowerCase())
                .estimatedPrice(booking.getEstimatedPrice())
                .commissionFee(booking.getCommissionFee())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    // Helper DTOs for OpenAPI documentation
    record ErrorResponse(String message, String code) {}
}