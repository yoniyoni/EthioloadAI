-- Phase 5: Record the winning carrier offer on the freight row.
--
-- carrier_award_offer_id: FK to carrier_offers.id — the single accepted offer.
-- Nullable: most freight rows will have no carrier award yet.
-- A partial unique index enforces that at most one accepted offer exists per freight.
--
-- Freight status is NOT changed to MATCHED here; that happens in Phase 6
-- when a driver + vehicle are assigned. Phase 5 commercial award is recorded
-- solely through this column and the ACCEPTED status on carrier_offers.

ALTER TABLE freight
    ADD COLUMN carrier_award_offer_id BIGINT
        REFERENCES carrier_offers(id)
        ON DELETE SET NULL;

-- Enforce uniqueness: at most one winning offer per freight.
CREATE UNIQUE INDEX uq_freight_carrier_award
    ON freight(carrier_award_offer_id)
    WHERE carrier_award_offer_id IS NOT NULL;

CREATE INDEX idx_freight_carrier_award
    ON freight(carrier_award_offer_id);
