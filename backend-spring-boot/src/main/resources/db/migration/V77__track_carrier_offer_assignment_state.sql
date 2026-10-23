ALTER TABLE carrier_offers
    ADD COLUMN assignment_status VARCHAR(30);

UPDATE carrier_offers co
SET assignment_status = CASE
    WHEN EXISTS (
        SELECT 1
        FROM bookings booking
        WHERE booking.carrier_offer_id = co.id
    ) OR EXISTS (
        SELECT 1
        FROM dispatch_assignments dispatch
        WHERE dispatch.carrier_offer_id = co.id
          AND dispatch.status = 'DRIVER_ACCEPTED'
    ) THEN 'BOOKING_CREATED'
    WHEN EXISTS (
        SELECT 1
        FROM dispatch_assignments dispatch
        WHERE dispatch.carrier_offer_id = co.id
          AND dispatch.status = 'ASSIGNED'
    ) THEN 'DISPATCH_PENDING'
    ELSE 'PENDING_ASSIGNMENT'
END
WHERE co.status = 'ACCEPTED';

ALTER TABLE carrier_offers
    ADD CONSTRAINT chk_accepted_carrier_offer_assignment_status
    CHECK (
        status <> 'ACCEPTED'
        OR assignment_status IN ('PENDING_ASSIGNMENT', 'DISPATCH_PENDING', 'BOOKING_CREATED')
    );
