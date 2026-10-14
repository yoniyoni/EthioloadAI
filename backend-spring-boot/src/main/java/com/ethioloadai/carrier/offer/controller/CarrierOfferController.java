package com.ethioloadai.carrier.offer.controller;

import com.ethioloadai.carrier.offer.dto.CarrierOfferResponse;
import com.ethioloadai.carrier.offer.dto.CounterOfferRequest;
import com.ethioloadai.carrier.offer.dto.NegotiationEntryResponse;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.offer.service.CarrierOfferService;
import com.ethioloadai.freight.dto.FreightFilterRequest;
import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.service.FreightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Phase 5 — Carrier company commercial offer endpoints.
 *
 * <h2>URL design</h2>
 * <pre>
 *   Carrier-facing (requires active membership + commercial role):
 *     POST   /carrier-companies/{companyId}/freight/{freightId}/offers
 *     GET    /carrier-companies/{companyId}/freight/{freightId}/offers/mine
 *     POST   /carrier-companies/{companyId}/offers/{offerId}/counter
 *     POST   /carrier-companies/{companyId}/offers/{offerId}/accept
 *     POST   /carrier-companies/{companyId}/offers/{offerId}/withdraw
 *     GET    /carrier-companies/{companyId}/offers              (company portal)
 *     GET    /carrier-companies/{companyId}/offers/{offerId}
 *
 *   Shipper-facing (requires freight ownership):
 *     GET    /freight/{freightId}/carrier-offers
 *     GET    /freight/{freightId}/carrier-offers/{offerId}
 *     POST   /freight/{freightId}/carrier-offers/{offerId}/counter
 *     POST   /freight/{freightId}/carrier-offers/{offerId}/accept
 *     POST   /freight/{freightId}/carrier-offers/{offerId}/reject
 * </pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>Carrier company ID is ALWAYS taken from the URL path — never the request body.</li>
 *   <li>The service layer validates the caller's active membership + role.</li>
 *   <li>The {@code @PreAuthorize} expressions use Phase 3 {@code companySecurity} bean
 *       as a first gate; the service applies full business logic.</li>
 *   <li>DRIVER role is blocked from all carrier commercial endpoints.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Carrier Offers (Phase 5)", description = "Carrier company commercial offer lifecycle")
public class CarrierOfferController {
    private static final int MAX_PAGE_SIZE = 100;

    private final CarrierOfferService offerService;
    private final FreightService freightService;

    /** Browse posted freight without applying the member's personal shipper filter. */
    @GetMapping("/carrier-companies/{companyId}/available-freight")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Browse posted freight available to an active company member")
    public ResponseEntity<Page<FreightResponse>> listAvailableFreight(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FreightFilterRequest filter = FreightFilterRequest.builder()
                .status("posted")
                .bidsOpen(true)
                .build();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(freightService.searchFreightsPaged(filter, pageable));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Carrier-facing endpoints
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Submit an initial offer on a freight shipment.
     * CARRIER_OWNER or DISPATCHER only.
     */
    @PostMapping("/carrier-companies/{companyId}/freight/{freightId}/offers")
    @PreAuthorize("@companySecurity.canNegotiateOffers(#companyId, authentication)")
    @Operation(summary = "Submit a carrier offer on a freight shipment")
    public ResponseEntity<CarrierOfferResponse> submitOffer(
            @PathVariable Long companyId,
            @PathVariable Long freightId,
            @Valid @RequestBody SubmitOfferRequest request,
            @AuthenticationPrincipal User caller) {
        CarrierOfferResponse response = offerService.submitOffer(freightId, companyId, caller, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get the company's own offer on a specific freight.
     */
    @GetMapping("/carrier-companies/{companyId}/freight/{freightId}/offers/mine")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get company's own offer on a freight")
    public ResponseEntity<CarrierOfferResponse> getMyOfferForFreight(
            @PathVariable Long companyId,
            @PathVariable Long freightId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getCompanyOfferForFreight(freightId, companyId, caller));
    }

    /**
     * Counter-offer in response to a shipper counter.
     * CARRIER_OWNER or DISPATCHER only.
     */
    @PostMapping("/carrier-companies/{companyId}/offers/{offerId}/counter")
    @PreAuthorize("@companySecurity.canNegotiateOffers(#companyId, authentication)")
    @Operation(summary = "Submit a counter-offer (carrier responds to shipper)")
    public ResponseEntity<CarrierOfferResponse> carrierCounter(
            @PathVariable Long companyId,
            @PathVariable Long offerId,
            @Valid @RequestBody CounterOfferRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.carrierCounter(offerId, companyId, caller, request));
    }

    /**
     * Accept the shipper's most recent counter-offer.
     */
    @PostMapping("/carrier-companies/{companyId}/offers/{offerId}/accept")
    @PreAuthorize("@companySecurity.canNegotiateOffers(#companyId, authentication)")
    @Operation(summary = "Carrier accepts the shipper's counter-offer")
    public ResponseEntity<CarrierOfferResponse> carrierAccept(
            @PathVariable Long companyId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.carrierAccept(offerId, companyId, caller));
    }

    /**
     * Withdraw the company's active offer.
     */
    @PostMapping("/carrier-companies/{companyId}/offers/{offerId}/withdraw")
    @PreAuthorize("@companySecurity.canNegotiateOffers(#companyId, authentication)")
    @Operation(summary = "Carrier withdraws its active offer")
    public ResponseEntity<CarrierOfferResponse> withdraw(
            @PathVariable Long companyId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.withdraw(offerId, companyId, caller));
    }

    /**
     * Paginated list of all offers for a company (company portal).
     * Visible to any active company member.
     */
    @GetMapping("/carrier-companies/{companyId}/offers")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "List all offers submitted by the company")
    public ResponseEntity<Page<CarrierOfferResponse>> listCompanyOffers(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User caller) {
        Page<CarrierOfferResponse> result = offerService.getCompanyOffers(
                companyId, caller,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                        Sort.by("createdAt").descending()));
        return ResponseEntity.ok(result);
    }

    /**
     * Get a specific offer by ID (company-scoped).
     */
    @GetMapping("/carrier-companies/{companyId}/offers/{offerId}")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get a specific carrier offer (company portal)")
    public ResponseEntity<CarrierOfferResponse> getOfferForCompany(
            @PathVariable Long companyId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getOffer(offerId, caller));
    }

    /**
     * Full negotiation history for a company's offer.
     */
    @GetMapping("/carrier-companies/{companyId}/offers/{offerId}/history")
    @PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
    @Operation(summary = "Get negotiation history for a company offer")
    public ResponseEntity<List<NegotiationEntryResponse>> getHistoryForCompany(
            @PathVariable Long companyId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getNegotiationHistory(offerId, caller));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Shipper-facing endpoints
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * List all carrier offers on a freight.
     * Accessible to the freight owner (shipper), any authenticated carrier, or admin.
     */
    @GetMapping("/freight/{freightId}/carrier-offers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all carrier offers for a freight shipment")
    public ResponseEntity<List<CarrierOfferResponse>> listOffersForFreight(
            @PathVariable Long freightId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getOffersForFreight(freightId, caller));
    }

    /**
     * Get a specific carrier offer on a freight (shipper view).
     */
    @GetMapping("/freight/{freightId}/carrier-offers/{offerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a specific carrier offer on a freight")
    public ResponseEntity<CarrierOfferResponse> getOfferForFreight(
            @PathVariable Long freightId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getOffer(offerId, caller));
    }

    /**
     * Shipper counters a carrier's offer.
     */
    @PostMapping("/freight/{freightId}/carrier-offers/{offerId}/counter")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Shipper counter-offers a carrier's proposal")
    public ResponseEntity<CarrierOfferResponse> shipperCounter(
            @PathVariable Long freightId,
            @PathVariable Long offerId,
            @Valid @RequestBody CounterOfferRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.shipperCounter(freightId, offerId, caller, request));
    }

    /**
     * Shipper accepts a carrier's offer — commercial award.
     * This atomically supersedes all other active offers on this freight.
     */
    @PostMapping("/freight/{freightId}/carrier-offers/{offerId}/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Shipper accepts a carrier offer — commercial award")
    public ResponseEntity<CarrierOfferResponse> shipperAccept(
            @PathVariable Long freightId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.shipperAccept(freightId, offerId, caller));
    }

    /**
     * Shipper rejects a carrier's offer.
     */
    @PostMapping("/freight/{freightId}/carrier-offers/{offerId}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Shipper rejects a carrier offer")
    public ResponseEntity<CarrierOfferResponse> shipperReject(
            @PathVariable Long freightId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.shipperReject(freightId, offerId, caller));
    }

    /**
     * Negotiation history for a freight offer (shipper view).
     */
    @GetMapping("/freight/{freightId}/carrier-offers/{offerId}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get negotiation history for a carrier offer (shipper view)")
    public ResponseEntity<List<NegotiationEntryResponse>> getHistoryForShipper(
            @PathVariable Long freightId,
            @PathVariable Long offerId,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(offerService.getNegotiationHistory(offerId, caller));
    }
}
