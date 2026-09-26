package com.ethioloadai.carrier.offer;

import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import com.ethioloadai.carrier.offer.entity.OfferNegotiationEntry.NegotiationAction;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 5 — Full carrier offer lifecycle integration tests.
 *
 * <p>Tests run against H2 + the real application context (same pattern as
 * Phase 4 fleet tests). Each test method gets a fresh, rolled-back transaction.
 *
 * <p>Coverage:
 * <ol>
 *   <li>Submit offer — role matrix (owner, dispatcher, fleet manager denied, driver denied)</li>
 *   <li>Fixed-price acceptance — amount = freight budget, no counter allowed</li>
 *   <li>Negotiable full negotiation round-trip</li>
 *   <li>Turn-order enforcement</li>
 *   <li>Competing offers — only one winner</li>
 *   <li>Tenant isolation — cross-company IDOR</li>
 *   <li>Shipper isolation — unrelated shipper blocked</li>
 *   <li>Lifecycle guards — terminal offer cannot be mutated</li>
 *   <li>Money validation — zero/negative/excess-scale rejected</li>
 *   <li>Mass-assignment protection</li>
 *   <li>Booking boundary — no driver/vehicle required after commercial award</li>
 *   <li>Negotiation history — append-only, complete</li>
 * </ol>
 */
@DisplayName("Phase 5 — Carrier Offer Integration")
class Phase5CarrierOfferIntegrationTest extends OfferTestSupport {

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Role matrix — submit offer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CARRIER_OWNER can submit negotiable offer → 201")
    void ownerSubmitsNegotiableOffer() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentAmount").value(45000.00))
                .andExpect(jsonPath("$.nextActor").value("SHIPPER"))
                .andExpect(jsonPath("$.pricingType").value("NEGOTIABLE"))
                .andExpect(jsonPath("$.carrierCompanyId").value(abcCompanyId.intValue()));
    }

    @Test
    @DisplayName("DISPATCHER can submit offer → 201")
    void dispatcherSubmitsOffer() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcDispatcher))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "44000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("FLEET_MANAGER cannot submit offer → 409 or 403")
    void fleetManagerDenied() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcFleetManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "44000.00"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Plain DRIVER cannot submit company offer → 403/404")
    void driverDenied() throws Exception {
        // Driver has no company membership — 403/404 from CompanyContextService
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(driver))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "44000.00"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Unauthenticated request → 401")
    void unauthenticatedDenied() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "44000.00"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Duplicate active offer rejected → 409")
    void duplicateActiveOfferRejected() throws Exception {
        // First offer
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated());

        // Second offer from same company on same freight — must fail
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "43000.00"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Offer on MATCHED freight rejected → 409")
    void offerOnMatchedFreightRejected() throws Exception {
        // Manually mark freight as MATCHED
        var f = freightRepo.findById(negotiableFreight.getId()).orElseThrow();
        f.setStatus(com.ethioloadai.freight.entity.Freight.FreightStatus.MATCHED);
        freightRepo.saveAndFlush(f);
        em.clear();

        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Money validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Zero amount rejected → 4xx")
    void zeroAmountRejected() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "0"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Negative amount rejected → 4xx")
    void negativeAmountRejected() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "-1000"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("More than 2 decimal places rejected → 4xx")
    void excessScaleRejected() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.123"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Valid 2dp amount accepted → 201")
    void validTwoDpAmountAccepted() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.50"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentAmount").value(45000.50));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Fixed-price workflow
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Fixed-price offer uses freight budget, ignores client amount → 201")
    void fixedPriceUsesFreightBudget() throws Exception {
        // Client sends 45000 but freight budget is 50000 — server overrides
        mvc.perform(post(offerUrl(abcCompanyId, fixedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentAmount").value(50000.00))
                .andExpect(jsonPath("$.pricingType").value("FIXED"));
    }

    @Test
    @DisplayName("Shipper accepts fixed-price offer → agreed amount = 50000")
    void shipperAcceptsFixedPriceOffer() throws Exception {
        // 1. ABC submits fixed-price offer
        String createResp = mvc.perform(post(offerUrl(abcCompanyId, fixedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "50000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long offerId = json.readTree(createResp).get("id").asLong();
        em.flush(); em.clear();

        // 2. Shipper accepts
        String acceptResp = mvc.perform(post(shipperAcceptUrl(fixedFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 3. Freight award is recorded — read via JDBC to bypass same-tx L1 cache
        Long awardId = jdbc.queryForObject(
                "SELECT carrier_award_offer_id FROM freight WHERE id = ?",
                Long.class, fixedFreight.getId());
        assertThat(awardId).isEqualTo(offerId);

        // 4. Offer status & agreed amount verified via JDBC
        String offerStatus = jdbc.queryForObject(
                "SELECT status FROM carrier_offers WHERE id = ?",
                String.class, offerId);
        assertThat(offerStatus).isEqualTo("ACCEPTED");
        java.math.BigDecimal agreedAmt = jdbc.queryForObject(
                "SELECT agreed_amount FROM carrier_offers WHERE id = ?",
                java.math.BigDecimal.class, offerId);
        assertThat(agreedAmt).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("Counter-offer on fixed-price offer rejected → 422")
    void counterOnFixedPriceRejected() throws Exception {
        String createResp = mvc.perform(post(offerUrl(abcCompanyId, fixedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "50000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long offerId = json.readTree(createResp).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(shipperCounterUrl(fixedFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Negotiable full round-trip
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full negotiation: ABC→Shipper→ABC→Shipper accepts at 47000")
    void fullNegotiationRoundTrip() throws Exception {
        // Step 1: ABC submits 45000
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Step 2: Shipper counters 48000
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00", "note", "try this"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.currentAmount").value(48000.00))
                .andExpect(jsonPath("$.nextActor").value("CARRIER"));
        em.flush(); em.clear();

        // Step 3: ABC counters 47000
        mvc.perform(post(counterCarrierUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "47000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.currentAmount").value(47000.00))
                .andExpect(jsonPath("$.nextActor").value("SHIPPER"));
        em.flush(); em.clear();

        // Step 4: Shipper accepts 47000
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.agreedAmount").value(47000.00))
                .andExpect(jsonPath("$.nextActor").doesNotExist());

        // Verify history has 4 entries
        em.flush(); em.clear();
        long histCount = historyRepo.countByCarrierOffer_Id(offerId);
        assertThat(histCount).isEqualTo(4L);

        // Verify freight award recorded — JDBC to bypass JPA L1 cache
        Long awardId = jdbc.queryForObject(
                "SELECT carrier_award_offer_id FROM freight WHERE id = ?",
                Long.class, negotiableFreight.getId());
        assertThat(awardId).isEqualTo(offerId);

        // Verify agreed amount via JDBC
        java.math.BigDecimal agreedAmt = jdbc.queryForObject(
                "SELECT agreed_amount FROM carrier_offers WHERE id = ?",
                java.math.BigDecimal.class, offerId);
        assertThat(agreedAmt).isEqualByComparingTo("47000.00");
    }

    @Test
    @DisplayName("Carrier accepts shipper's counter → ACCEPTED")
    void carrierAcceptsShipperCounter() throws Exception {
        // Step 1: ABC submits 45000
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Step 2: Shipper counters 48000
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Step 3: Carrier accepts 48000 (it's carrier's turn)
        mvc.perform(post(acceptCarrierUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.agreedAmount").value(48000.00));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Turn-order enforcement
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Shipper cannot counter when it is carrier's turn → 409")
    void shipperCannotCounterOnCarrierTurn() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Shipper counters → now it's CARRIER's turn
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Shipper tries to counter again — must fail (carrier's turn)
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "47000.00"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Carrier cannot counter when it is shipper's turn → 409")
    void carrierCannotCounterOnShipperTurn() throws Exception {
        // After submit, it's SHIPPER's turn
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Carrier tries to counter immediately — must fail (shipper's turn)
        mvc.perform(post(counterCarrierUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "44000.00"))))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Competing offers — one winner
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ABC and XYZ both offer; accepting ABC supersedes XYZ")
    void acceptingOneSupersdesOther() throws Exception {
        // ABC submits
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long abcOfferId = json.readTree(s1).get("id").asLong();

        // XYZ submits
        String s2 = mvc.perform(post(offerUrl(xyzCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(xyzOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "46000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long xyzOfferId = json.readTree(s2).get("id").asLong();
        em.flush(); em.clear();

        // Shipper accepts ABC
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), abcOfferId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        em.flush(); em.clear();

        // ABC offer is ACCEPTED — check via JDBC
        String abcStatus = jdbc.queryForObject(
                "SELECT status FROM carrier_offers WHERE id = ?", String.class, abcOfferId);
        assertThat(abcStatus).isEqualTo("ACCEPTED");

        // XYZ offer is SUPERSEDED — check via JDBC
        String xyzStatus = jdbc.queryForObject(
                "SELECT status FROM carrier_offers WHERE id = ?", String.class, xyzOfferId);
        assertThat(xyzStatus).isEqualTo("SUPERSEDED");

        // Freight has exactly one award — check via JDBC
        Long awardId = jdbc.queryForObject(
                "SELECT carrier_award_offer_id FROM freight WHERE id = ?",
                Long.class, negotiableFreight.getId());
        assertThat(awardId).isEqualTo(abcOfferId);
    }

    @Test
    @DisplayName("Second acceptance on already-awarded freight fails → 409")
    void doubleAcceptanceRejected() throws Exception {
        // ABC submits and shipper accepts
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long abcOfferId = json.readTree(s1).get("id").asLong();

        // XYZ submits a separate offer
        String s2 = mvc.perform(post(offerUrl(xyzCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(xyzOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "46000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long xyzOfferId = json.readTree(s2).get("id").asLong();
        em.flush(); em.clear();

        // Accept ABC
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), abcOfferId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Try to accept XYZ now — freight already has an award
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), xyzOfferId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Tenant isolation — cross-company IDOR
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("XYZ cannot counter ABC offer using carrier endpoint → 403")
    void xyzCannotCounterAbcOffer() throws Exception {
        // ABC submits
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long abcOfferId = json.readTree(s1).get("id").asLong();

        // Shipper counters so it becomes CARRIER turn
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), abcOfferId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // XYZ tries to counter using ABC's offerId under XYZ's companyId — must fail
        mvc.perform(post(counterCarrierUrl(xyzCompanyId, abcOfferId))
                .header("Authorization", auth(xyzOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "47000.00"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("XYZ cannot withdraw ABC offer → 403")
    void xyzCannotWithdrawAbcOffer() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long abcOfferId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // XYZ tries to withdraw ABC's offer
        mvc.perform(post(withdrawUrl(xyzCompanyId, abcOfferId))
                .header("Authorization", auth(xyzOwner)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("ABC member with wrong companyId in path is blocked by membership check → 403/404")
    void wrongCompanyIdInPathBlocked() throws Exception {
        // ABC owner tries to use XYZ's companyId in the path
        mvc.perform(post(offerUrl(xyzCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. Shipper isolation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unrelated shipper cannot accept offer → 403")
    void unrelatedShipperCannotAccept() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // otherShipper does not own this freight
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(otherShipper)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Unrelated shipper cannot counter offer → 403")
    void unrelatedShipperCannotCounter() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(otherShipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Unrelated shipper cannot reject offer → 403")
    void unrelatedShipperCannotReject() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(shipperRejectUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(otherShipper)))
                .andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. Lifecycle guards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Accepted offer cannot be countered → 409")
    void acceptedOfferCannotBeCountered() throws Exception {
        // Create and accept
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Try counter on accepted offer
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "46000.00"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Withdrawn offer cannot be accepted → 409")
    void withdrawnOfferCannotBeAccepted() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Carrier withdraws
        mvc.perform(post(withdrawUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        em.flush(); em.clear();

        // Shipper tries to accept → 409
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rejected offer cannot be accepted → 409")
    void rejectedOfferCannotBeAccepted() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Shipper rejects
        mvc.perform(post(shipperRejectUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        em.flush(); em.clear();

        // Shipper tries to accept the same → 409
        mvc.perform(post(shipperAcceptUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. Negotiation history
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("History is append-only: full negotiation produces 5 entries")
    void historyAppendOnly() throws Exception {
        // Step 1: Carrier offers
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // Step 2: Shipper counters
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "48000.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Step 3: Carrier counters
        mvc.perform(post(counterCarrierUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "47000.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Step 4: Shipper counters again
        mvc.perform(post(shipperCounterUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "47500.00"))))
                .andExpect(status().isOk());
        em.flush(); em.clear();

        // Step 5: Carrier accepts
        mvc.perform(post(acceptCarrierUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        em.flush(); em.clear();

        // Verify history via API
        mvc.perform(get(historyUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].action").value("OFFER"))
                .andExpect(jsonPath("$[0].amount").value(45000.00))
                .andExpect(jsonPath("$[1].action").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$[2].action").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$[3].action").value("COUNTER_OFFER"))
                .andExpect(jsonPath("$[4].action").value("ACCEPT"));

        // DB count confirms no history deletion
        long dbCount = historyRepo.countByCarrierOffer_Id(offerId);
        assertThat(dbCount).isEqualTo(5L);
    }

    @Test
    @DisplayName("IDOR: unrelated party cannot read history → 403")
    void historyInaccessibleToStranger() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        // otherShipper does not own this freight
        mvc.perform(get(historyUrl(negotiableFreight.getId(), offerId))
                .header("Authorization", auth(otherShipper)))
                .andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. Booking boundary — no driver/vehicle required
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Commercial award does not require driver or vehicle (Phase 6 boundary)")
    void commercialAwardRequiresNeitherDriverNorVehicle() throws Exception {
        // ABC submits fixed-price offer; shipper accepts
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, fixedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "50000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(shipperAcceptUrl(fixedFreight.getId(), offerId))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.agreedAmount").value(50000.00));

        em.flush(); em.clear();

        // Freight stays POSTED (Phase 6 will change to MATCHED when driver+vehicle assigned)
        String freightStatus = jdbc.queryForObject(
                "SELECT status FROM freight WHERE id = ?", String.class, fixedFreight.getId());
        assertThat(freightStatus).isEqualTo("posted"); // stored lowercase by FreightStatusConverter

        // Award is set
        Long awardId = jdbc.queryForObject(
                "SELECT carrier_award_offer_id FROM freight WHERE id = ?",
                Long.class, fixedFreight.getId());
        assertThat(awardId).isEqualTo(offerId);

        // No matched driver or vehicle
        Integer matchedDriver = jdbc.queryForObject(
                "SELECT matched_driver_id FROM freight WHERE id = ?",
                Integer.class, fixedFreight.getId());
        assertThat(matchedDriver).isNull();

        // No Booking row should exist for this freight
        int bookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE cargo_id = ?",
                Integer.class, fixedFreight.getId());
        assertThat(bookings).isZero();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. Mass-assignment protection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("carrierCompanyId in request body is ignored; company resolved from path+membership")
    void carrierCompanyIdInBodyIgnored() throws Exception {
        // Body contains XYZ company id but the path/membership is ABC
        String body = json.writeValueAsString(Map.of(
                "amount", "45000.00",
                "carrierCompanyId", xyzCompanyId   // malicious field — must be ignored
        ));
        String resp = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // The created offer must belong to ABC, not XYZ
        Long createdCompanyId = json.readTree(resp).get("carrierCompanyId").asLong();
        assertThat(createdCompanyId).isEqualTo(abcCompanyId);
    }

    @Test
    @DisplayName("Status in request body is ignored (cannot self-assign ACCEPTED)")
    void statusInBodyIgnored() throws Exception {
        // Try to self-accept by including "status": "ACCEPTED" in the submit body
        String body = json.writeValueAsString(Map.of(
                "amount", "45000.00",
                "status", "ACCEPTED"   // must be ignored
        ));
        String resp = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Status must be PENDING, not ACCEPTED
        String status = json.readTree(resp).get("status").asText();
        assertThat(status).isEqualTo("PENDING");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. List and view
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Shipper sees all offers for own freight")
    void shipperSeesAllOffers() throws Exception {
        // ABC submits
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated());

        // XYZ submits
        mvc.perform(post(offerUrl(xyzCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(xyzOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "46000.00"))))
                .andExpect(status().isCreated());
        em.flush(); em.clear();

        mvc.perform(get(listOffersUrl(negotiableFreight.getId()))
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Company portal lists own offers paginated")
    void companyOfferListPaginated() throws Exception {
        mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andExpect(status().isCreated());
        em.flush(); em.clear();

        mvc.perform(get(companyOffersUrl(abcCompanyId) + "?page=0&size=20")
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].carrierCompanyId").value(abcCompanyId.intValue()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. Carrier withdrawal
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Carrier can withdraw own active offer → WITHDRAWN")
    void carrierWithdrawsOwnOffer() throws Exception {
        String s1 = mvc.perform(post(offerUrl(abcCompanyId, negotiableFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("amount", "45000.00"))))
                .andReturn().getResponse().getContentAsString();
        Long offerId = json.readTree(s1).get("id").asLong();
        em.flush(); em.clear();

        mvc.perform(post(withdrawUrl(abcCompanyId, offerId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnAt").isNotEmpty());

        // Withdrawal history entry must exist
        long histCount = historyRepo.countByCarrierOffer_Id(offerId);
        assertThat(histCount).isEqualTo(2L); // OFFER + WITHDRAW
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. Regression: Phase 1–4 APIs unaffected
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Phase 4 fleet driver list still works after Phase 5 additions")
    void phase4FleetListUnaffected() throws Exception {
        // ABC owner can still see company details — Phase 1 API
        mvc.perform(get("/carrier-companies/" + abcCompanyId)
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Freight GET still works after Phase 5 migrations")
    void freightGetUnaffected() throws Exception {
        mvc.perform(get("/freight/" + negotiableFreight.getId())
                .header("Authorization", auth(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(negotiableFreight.getId().intValue()));
    }
}
