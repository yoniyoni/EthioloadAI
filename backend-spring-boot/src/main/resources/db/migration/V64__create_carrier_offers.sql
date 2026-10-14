-- Phase 5: Carrier company commercial offers on freight.
--
-- A CarrierOffer represents a carrier company's commercial intent on a freight
-- shipment. It replaces the individual-driver Bid as the commercial actor when
-- the carrier-company workflow is active.
--
-- Status lifecycle:
--   PENDING      → initial state; carrier submitted offer, awaiting shipper response
--   NEGOTIATING  → at least one counter-offer has been exchanged; still active
--   ACCEPTED     → offer accepted by either party; commercial award granted
--   REJECTED     → shipper rejected the offer
--   WITHDRAWN    → carrier withdrew before acceptance
--   SUPERSEDED   → another offer on the same freight was accepted; this one closed
--
-- Design decisions:
--   * One active offer per company per freight (partial unique index).
--   * current_amount is a denormalized convenience field; authoritative history
--     is in offer_negotiation_entries.
--   * carrier_company_id and submitted_by are SERVER-SIDE derived; never trusted
--     from client request body.
--   * Freight stays in POSTED status until Phase 6 dispatch; only
--     carrier_award_offer_id on the freight row records the winning offer.
--   * driver_id and vehicle_id are intentionally absent — Phase 6 concern.

CREATE TABLE carrier_offers (
    id                  BIGSERIAL PRIMARY KEY,
    freight_id          BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id) ON DELETE CASCADE,
    submitted_by        BIGINT NOT NULL REFERENCES users(id),

    -- Pricing type mirrors the freight's price_type at submission time.
    -- Stored on the offer so history is self-contained even if freight changes.
    pricing_type        VARCHAR(20) NOT NULL
                            CHECK (pricing_type IN ('FIXED', 'NEGOTIABLE')),

    -- Denormalized current negotiated amount (ETB). Updated atomically with
    -- a negotiation history entry. Always positive.
    current_amount      DECIMAL(15, 2) NOT NULL CHECK (current_amount > 0),

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','NEGOTIATING','ACCEPTED',
                                              'REJECTED','WITHDRAWN','SUPERSEDED')),

    -- Whose turn it is to respond. Set server-side on every state change.
    -- NULL when offer is in a terminal state.
    next_actor          VARCHAR(10)
                            CHECK (next_actor IS NULL OR next_actor IN ('SHIPPER','CARRIER')),

    -- Timestamps for terminal events. Exactly one is set per terminal status.
    accepted_at         TIMESTAMP,
    rejected_at         TIMESTAMP,
    withdrawn_at        TIMESTAMP,

    -- The agreed price when accepted (copied from current_amount at acceptance).
    agreed_amount       DECIMAL(15, 2) CHECK (agreed_amount > 0),

    -- Version column for optimistic-locking (concurrency safety on accept).
    version             BIGINT NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Business invariants ──────────────────────────────────────────────────────

    -- Terminal states require the appropriate timestamp.
    CONSTRAINT ck_offer_accepted_at CHECK (
        (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND agreed_amount IS NOT NULL)
        OR (status <> 'ACCEPTED' AND accepted_at IS NULL AND agreed_amount IS NULL)),

    CONSTRAINT ck_offer_rejected_at CHECK (
        (status = 'REJECTED' AND rejected_at IS NOT NULL)
        OR (status <> 'REJECTED' AND rejected_at IS NULL)),

    CONSTRAINT ck_offer_withdrawn_at CHECK (
        (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
        OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)),

    -- next_actor must be NULL in terminal states.
    CONSTRAINT ck_offer_next_actor_terminal CHECK (
        status NOT IN ('ACCEPTED','REJECTED','WITHDRAWN','SUPERSEDED')
        OR next_actor IS NULL)
);

-- Prevent multiple active offers from the same company on the same freight.
-- "Active" = any status that is not yet terminal.
CREATE UNIQUE INDEX uq_active_offer_per_company_freight
    ON carrier_offers(freight_id, carrier_company_id)
    WHERE status IN ('PENDING','NEGOTIATING');

-- Performance indexes.
CREATE INDEX idx_offer_freight_id         ON carrier_offers(freight_id, status);
CREATE INDEX idx_offer_carrier_company_id ON carrier_offers(carrier_company_id, status);
CREATE INDEX idx_offer_submitted_by       ON carrier_offers(submitted_by);
CREATE INDEX idx_offer_status_created     ON carrier_offers(status, created_at DESC);
