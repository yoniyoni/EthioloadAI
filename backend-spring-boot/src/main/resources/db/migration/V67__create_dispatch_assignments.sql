-- Phase 6: Carrier Internal Dispatch — assigns a specific driver + vehicle to an awarded freight.
--
-- After a CarrierOffer is accepted (Phase 5), the winning carrier company must
-- internally dispatch one of their drivers and one of their vehicles to actually
-- perform the shipment. This table records that dispatch decision and the driver's
-- response (accept / reject).
--
-- Lifecycle:
--   ASSIGNED        — dispatcher created the assignment; driver notified
--   DRIVER_ACCEPTED — driver accepted; triggers Booking creation
--   DRIVER_REJECTED — driver rejected; carrier may redispatch
--   CANCELLED       — carrier operator cancelled before driver responded
--   SUPERSEDED      — a new dispatch replaced this one (e.g. after rejection)
--
-- Business invariants:
--   * Only one ACTIVE dispatch per awarded freight at a time
--     (active = ASSIGNED or DRIVER_ACCEPTED).
--   * carrier_company_id must equal the winning offer's carrier_company_id.
--   * driver_user_id must belong to that same carrier company (carrier_drivers table).
--   * vehicle_id must belong to that same carrier company (vehicles.carrier_company_id).
--   * agreed_amount is copied from CarrierOffer.agreed_amount at creation — immutable.
--
-- Security: carrierCompanyId, freightId, carrierOfferId, and agreedAmount are ALL
-- derived server-side. The client only supplies driverUserId, vehicleId, and optional notes.

CREATE TABLE dispatch_assignments (
    id                  BIGSERIAL PRIMARY KEY,

    -- Core relationships
    freight_id          BIGINT NOT NULL REFERENCES freight(id) ON DELETE CASCADE,
    carrier_offer_id    BIGINT NOT NULL REFERENCES carrier_offers(id),
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id),

    -- Selected resources (both must belong to carrier_company_id)
    driver_user_id      BIGINT NOT NULL REFERENCES users(id),
    vehicle_id          BIGINT NOT NULL REFERENCES vehicles(id),

    -- Audit
    assigned_by         BIGINT NOT NULL REFERENCES users(id),

    -- Status lifecycle
    status              VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED'
                            CHECK (status IN (
                                'ASSIGNED',
                                'DRIVER_ACCEPTED',
                                'DRIVER_REJECTED',
                                'CANCELLED',
                                'SUPERSEDED'
                            )),

    -- Agreed commercial amount — copied from CarrierOffer.agreed_amount at dispatch time.
    -- Immutable after creation. Driver/dispatcher CANNOT change this.
    agreed_amount       DECIMAL(15,2) NOT NULL CHECK (agreed_amount > 0),

    -- Optional dispatcher note
    notes               VARCHAR(1000),

    -- Optional driver rejection reason
    rejection_reason    VARCHAR(1000),

    -- Timestamps
    assigned_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,

    -- Link back to the Booking created on acceptance (set after DRIVER_ACCEPTED)
    booking_id          BIGINT REFERENCES bookings(id),

    -- Optimistic locking
    version             BIGINT NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ── Business invariants ──────────────────────────────────────────────────

    -- responded_at must be set for terminal driver-response states
    CONSTRAINT ck_dispatch_responded_at CHECK (
        (status IN ('DRIVER_ACCEPTED', 'DRIVER_REJECTED') AND responded_at IS NOT NULL)
        OR (status NOT IN ('DRIVER_ACCEPTED', 'DRIVER_REJECTED'))
    ),

    -- cancelled_at must be set for CANCELLED
    CONSTRAINT ck_dispatch_cancelled_at CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED')
    ),

    -- booking_id only set when DRIVER_ACCEPTED
    CONSTRAINT ck_dispatch_booking_accepted CHECK (
        (status = 'DRIVER_ACCEPTED' AND booking_id IS NOT NULL)
        OR (status <> 'DRIVER_ACCEPTED')
    )
);

-- ── Indexes ──────────────────────────────────────────────────────────────────

CREATE INDEX idx_dispatch_freight_id        ON dispatch_assignments(freight_id, status);
CREATE INDEX idx_dispatch_carrier_offer_id  ON dispatch_assignments(carrier_offer_id);
CREATE INDEX idx_dispatch_carrier_company   ON dispatch_assignments(carrier_company_id, status);
CREATE INDEX idx_dispatch_driver_user_id    ON dispatch_assignments(driver_user_id, status);
CREATE INDEX idx_dispatch_vehicle_id        ON dispatch_assignments(vehicle_id, status);
CREATE INDEX idx_dispatch_status_created    ON dispatch_assignments(status, created_at DESC);

-- ── Uniqueness constraints ────────────────────────────────────────────────────

-- At most one ASSIGNED or DRIVER_ACCEPTED dispatch per awarded freight.
-- Prevents two dispatchers simultaneously creating competing active dispatches.
CREATE UNIQUE INDEX uq_active_dispatch_per_freight
    ON dispatch_assignments(freight_id)
    WHERE status IN ('ASSIGNED', 'DRIVER_ACCEPTED');

-- At most one ASSIGNED (pending) dispatch per driver at a time.
-- Prevents the same driver being double-dispatched while still pending.
CREATE UNIQUE INDEX uq_pending_dispatch_per_driver
    ON dispatch_assignments(driver_user_id)
    WHERE status = 'ASSIGNED';

-- At most one ASSIGNED (pending) dispatch per vehicle at a time.
CREATE UNIQUE INDEX uq_pending_dispatch_per_vehicle
    ON dispatch_assignments(vehicle_id)
    WHERE status = 'ASSIGNED';
