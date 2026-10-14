-- Phase 5: Immutable negotiation history for carrier offers.
--
-- Every commercial action (offer, counter-offer, accept, reject, withdraw)
-- creates one append-only row here. Rows are NEVER updated or deleted.
--
-- actor_type: who performed the action (SHIPPER or CARRIER).
-- action:     what they did (OFFER, COUNTER_OFFER, ACCEPT, REJECT, WITHDRAW).
-- amount:     the monetary value proposed/agreed at this step (may be NULL
--             for ACCEPT/REJECT/WITHDRAW if the action carries no new price).

CREATE TABLE offer_negotiation_entries (
    id                  BIGSERIAL PRIMARY KEY,
    carrier_offer_id    BIGINT NOT NULL REFERENCES carrier_offers(id) ON DELETE CASCADE,
    actor_type          VARCHAR(10) NOT NULL
                            CHECK (actor_type IN ('SHIPPER','CARRIER')),
    actor_user_id       BIGINT NOT NULL REFERENCES users(id),
    action              VARCHAR(20) NOT NULL
                            CHECK (action IN ('OFFER','COUNTER_OFFER','ACCEPT',
                                              'REJECT','WITHDRAW')),
    -- Amount at this negotiation step (NULL only for ACCEPT/REJECT/WITHDRAW
    -- where the price is already locked on the parent offer).
    amount              DECIMAL(15, 2) CHECK (amount IS NULL OR amount > 0),
    note                VARCHAR(1000),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Amount required for price-proposing actions.
    CONSTRAINT ck_entry_amount_required CHECK (
        action NOT IN ('OFFER','COUNTER_OFFER')
        OR (amount IS NOT NULL AND amount > 0)
    )
);

-- History is almost always queried by carrier_offer_id ordered by created_at.
CREATE INDEX idx_neg_entry_offer_id    ON offer_negotiation_entries(carrier_offer_id, created_at ASC);
CREATE INDEX idx_neg_entry_actor_user  ON offer_negotiation_entries(actor_user_id);
