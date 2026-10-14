-- Phase 6: Extend bookings table to record carrier dispatch context.
--
-- carrier_offer_id      — the winning CarrierOffer that led to this booking.
-- dispatch_assignment_id — the DispatchAssignment whose driver acceptance created this booking.
-- carrier_company_id   — denormalized for fast carrier-scoped queries.
--
-- All three columns are nullable: existing bookings created through the legacy
-- bid-acceptance flow do not have carrier offer / dispatch context.

ALTER TABLE bookings
    ADD COLUMN carrier_offer_id         BIGINT REFERENCES carrier_offers(id),
    ADD COLUMN dispatch_assignment_id   BIGINT REFERENCES dispatch_assignments(id),
    ADD COLUMN carrier_company_id       BIGINT REFERENCES carrier_companies(id);

-- A dispatch assignment should produce at most one booking.
CREATE UNIQUE INDEX uq_booking_dispatch_assignment
    ON bookings(dispatch_assignment_id)
    WHERE dispatch_assignment_id IS NOT NULL;

-- Performance indexes for carrier-scoped booking queries.
CREATE INDEX idx_booking_carrier_offer_id       ON bookings(carrier_offer_id);
CREATE INDEX idx_booking_carrier_company_id     ON bookings(carrier_company_id, booking_status);
CREATE INDEX idx_booking_dispatch_assignment_id ON bookings(dispatch_assignment_id);
