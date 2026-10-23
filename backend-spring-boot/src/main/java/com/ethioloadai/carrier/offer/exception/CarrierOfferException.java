package com.ethioloadai.carrier.offer.exception;

import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import com.ethioloadai.exception.BaseException;

/**
 * Phase 5 — Domain exception for carrier offer lifecycle violations.
 *
 * <p>HTTP status codes:
 * <ul>
 *   <li>404 — offer or freight not found</li>
 *   <li>403 — authorization failure (IDOR, wrong actor)</li>
 *   <li>409 — lifecycle conflict (wrong state, duplicate active offer)</li>
 *   <li>422 — business rule violation (fixed-price counter, amount mismatch)</li>
 * </ul>
 */
public class CarrierOfferException extends BaseException {

    public CarrierOfferException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    // ── 404 ────────────────────────────────────────────────────────────────────

    public static CarrierOfferException offerNotFound(Long offerId) {
        return new CarrierOfferException(
            "Carrier offer not found: " + offerId,
            "CARRIER_OFFER_NOT_FOUND", 404);
    }

    public static CarrierOfferException freightNotFound(Long freightId) {
        return new CarrierOfferException(
            "Freight not found: " + freightId,
            "FREIGHT_NOT_FOUND", 404);
    }

    // ── 403 ────────────────────────────────────────────────────────────────────

    public static CarrierOfferException forbidden() {
        return new CarrierOfferException(
            "You are not authorized to perform this action on this offer",
            "CARRIER_OFFER_ACCESS_DENIED", 403);
    }

    public static CarrierOfferException driverCannotSubmitCompanyOffer() {
        return new CarrierOfferException(
            "Drivers cannot submit commercial carrier offers. Only CARRIER_OWNER or DISPATCHER may act commercially.",
            "DRIVER_COMMERCIAL_FORBIDDEN", 403);
    }

    public static CarrierOfferException commercialRoleRequired() {
        return new CarrierOfferException(
            "Commercial carrier offers require the CARRIER_OWNER or DISPATCHER role.",
            "COMMERCIAL_ROLE_REQUIRED", 403);
    }

    public static CarrierOfferException notYourTurn(com.ethioloadai.carrier.offer.entity.CarrierOffer.ActorType expected) {
        return new CarrierOfferException(
            "It is the " + expected.name() + "'s turn to respond",
            "NOT_YOUR_TURN", 409);
    }

    // ── 409 ────────────────────────────────────────────────────────────────────

    public static CarrierOfferException alreadyActive(Long freightId, Long companyId) {
        return new CarrierOfferException(
            "Company " + companyId + " already has an active offer on freight " + freightId + ". "
            + "Counter or withdraw the existing offer.",
            "DUPLICATE_ACTIVE_OFFER", 409);
    }

    public static CarrierOfferException illegalTransition(OfferStatus from, String action) {
        return new CarrierOfferException(
            "Cannot perform '" + action + "' on an offer in status " + from,
            "ILLEGAL_OFFER_TRANSITION", 409);
    }

    public static CarrierOfferException freightNotOpen(Long freightId) {
        return new CarrierOfferException(
            "Freight " + freightId + " is not open for carrier offers",
            "FREIGHT_NOT_OPEN", 409);
    }

    public static CarrierOfferException freightAlreadyAwarded(Long freightId) {
        return new CarrierOfferException(
            "Freight " + freightId + " already has an accepted carrier offer",
            "FREIGHT_ALREADY_AWARDED", 409);
    }

    // ── 422 ────────────────────────────────────────────────────────────────────

    public static CarrierOfferException fixedPriceCounterNotAllowed() {
        return new CarrierOfferException(
            "Counter-offers are not allowed on fixed-price freight. You may only accept or decline.",
            "FIXED_PRICE_COUNTER_NOT_ALLOWED", 422);
    }

    public static CarrierOfferException fixedPriceAmountMismatch(java.math.BigDecimal expected) {
        return new CarrierOfferException(
            "For fixed-price freight the offer amount must equal the freight budget ("
                + expected.toPlainString() + " ETB).",
            "FIXED_PRICE_AMOUNT_MISMATCH", 422);
    }

    public static CarrierOfferException invalidRequest(String message) {
        return new CarrierOfferException(message, "INVALID_CARRIER_APPLICATION", 422);
    }

    public static CarrierOfferException companyNotApproved() {
        return new CarrierOfferException(
            "Carrier company is not approved. Only APPROVED companies may submit commercial offers.",
            "COMPANY_NOT_APPROVED", 422);
    }
}
