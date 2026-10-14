# Phase 6 — Carrier Internal Dispatch + Driver/Vehicle Assignment + Driver Acceptance

## Overview

Phase 5 answered: *Which carrier company won the shipment and at what agreed price?*

Phase 6 answers: *Which specific driver and vehicle from that winning company will actually perform the shipment?*

The complete flow:

```
Shipper posts freight
      ↓
Carrier negotiation (Phase 5)
      ↓
Carrier wins — CarrierOffer ACCEPTED, freight.carrier_award_offer_id set
      ↓
[PHASE 6 STARTS]
      ↓
Carrier DISPATCHER / FLEET_MANAGER / OWNER
      ↓
Selects eligible Driver (same company)
      ↓
Selects eligible Vehicle (same company)
      ↓
Creates DispatchAssignment (ASSIGNED)
      ↓
Driver receives notification
      ↓
Driver reviews and responds
      ↓
ACCEPT → Booking created (ACCEPTED), Freight → MATCHED, Driver → ON_TRIP
REJECT → Resources released, Carrier can redispatch
      ↓
Existing Trip/Tracking system continues unchanged
```

---

## Architecture

### Entity Chain

```
Freight
  └─ carrier_award_offer_id → CarrierOffer (ACCEPTED)
                                 └─ carrier_company_id → CarrierCompany
                                 └─ agreed_amount (frozen)
                                      ↓
                               DispatchAssignment
                                 ├─ freight_id
                                 ├─ carrier_offer_id
                                 ├─ carrier_company_id (server-derived)
                                 ├─ driver_user_id   → User (DRIVER)
                                 ├─ vehicle_id       → Vehicle
                                 ├─ assigned_by      → User (dispatcher)
                                 ├─ agreed_amount    (copied, immutable)
                                 └─ booking_id       → Booking (on acceptance)
                                      ↓
                               Booking
                                 ├─ driver          → User
                                 ├─ vehicle         → Vehicle
                                 ├─ cargoRequest    → Freight
                                 ├─ estimatedPrice  (= agreed_amount)
                                 ├─ carrier_company_id
                                 ├─ carrier_offer_id
                                 └─ dispatch_assignment_id
                                      ↓
                               Trip (existing — ONGOING/COMPLETED)
```

### Package Structure

```
com.ethioloadai.carrier.dispatch
├── entity/
│   ├── DispatchAssignment.java     — JPA entity
│   └── DispatchStatus.java         — Lifecycle enum
├── exception/
│   └── DispatchException.java      — All dispatch-domain errors
├── dto/
│   ├── CreateDispatchRequest.java  — POST body (driverUserId, vehicleId, notes only)
│   ├── DriverRespondRequest.java   — Optional rejection reason
│   ├── DispatchAssignmentResponse.java
│   └── AwardedFreightSummary.java  — Carrier dispatch portal view
├── repository/
│   └── DispatchAssignmentRepository.java
├── service/
│   ├── DispatchService.java        — Interface
│   └── DispatchServiceImpl.java    — Implementation
└── controller/
    └── DispatchController.java     — REST endpoints
```

---

## Dispatch Lifecycle

```
ASSIGNED  ──────────────►  DRIVER_ACCEPTED  (driver accepts → Booking created)
    │
    ├──────────────────►  DRIVER_REJECTED   (driver rejects → resources released)
    │
    ├──────────────────►  CANCELLED         (carrier operator cancels)
    │
    └──────────────────►  SUPERSEDED        (new dispatch replaces this one)
```

### Rules

- `ASSIGNED` is the only non-terminal state.
- Terminal states (`DRIVER_ACCEPTED`, `DRIVER_REJECTED`, `CANCELLED`, `SUPERSEDED`) cannot be mutated.
- Only **one** active dispatch (`ASSIGNED` or `DRIVER_ACCEPTED`) may exist per awarded freight at a time — enforced by partial unique index `uq_active_dispatch_per_freight`.
- Only **one** pending dispatch per driver and per vehicle — enforced by `uq_pending_dispatch_per_driver` and `uq_pending_dispatch_per_vehicle`.

---

## Driver Eligibility Rules

A driver is eligible for dispatch when **all** of the following hold:

| Rule | Check |
|------|-------|
| Company ownership | `carrier_drivers.carrier_company_id = award's carrier_company_id` |
| Active fleet membership | `carrier_drivers.status = ACTIVE` |
| Not unavailable | `carrier_drivers.availability ≠ UNAVAILABLE` |
| Not already on trip | `carrier_drivers.availability ≠ ON_TRIP` |
| Active user account | `users.is_active = true` |
| Correct user role | `users.role = DRIVER` |
| No pending dispatch | No existing `ASSIGNED` dispatch for this driver |

---

## Vehicle Eligibility Rules

A vehicle is eligible for dispatch when **all** of the following hold:

| Rule | Check |
|------|-------|
| Company ownership | `vehicles.carrier_company_id = award's carrier_company_id` |
| Admin-approved | `vehicles.approval_status = APPROVED` |
| Not on trip | `vehicles.availability_status ≠ 'on_trip'` |
| Not in maintenance | `vehicles.availability_status ≠ 'maintenance'` |
| Not unavailable | `vehicles.availability_status ≠ 'unavailable'` |
| Sufficient capacity | `vehicles.capacity ≥ freight.weight_tons` (when both present) |
| No pending dispatch | No existing `ASSIGNED` dispatch for this vehicle |

---

## Winning-Carrier Enforcement

The server validates all of the following before creating a dispatch:

1. Freight exists and has a `carrier_award_offer_id`.
2. The winning `CarrierOffer` has `status = ACCEPTED`.
3. `CarrierOffer.carrier_company_id` matches the **caller's** company (from JWT → membership).
4. `agreed_amount` is present and positive on the winning offer.
5. The freight is not CANCELLED, COMPLETED, IN_TRANSIT, or DELIVERED.
6. No active dispatch already exists for this freight.

The `carrierCompanyId` is **always** derived server-side from the authenticated user's active `CompanyMembership`. It is never accepted from the request body.

---

## Phase 4 Driver↔Vehicle Assignment Integration

**Decision: Option C — Dispatch-specific pairing**

Phase 4 `VehicleDriverAssignment` tracks long-term fleet pairings (driver↔vehicle assignments within a company). Phase 6 does **not** require or create a `VehicleDriverAssignment` for each dispatch. The dispatcher selects a driver and vehicle independently for each shipment.

Rationale:
- A dispatcher may choose a driver and vehicle that are not permanently paired.
- Creating a fleet reassignment for every dispatch would clutter fleet history.
- The driver and vehicle availability states (`ASSIGNED`, `ON_TRIP`) are updated directly on `CarrierDriver` and `Vehicle` — the same states Phase 4 already manages.

Long-term fleet pairing (Phase 4) and per-shipment dispatch (Phase 6) remain independent concerns.

---

## Booking Creation

**Pattern: Create at driver acceptance**

A `Booking` is created **atomically** when the driver accepts the dispatch assignment. This avoids creating an operational booking for a dispatch the driver might reject.

The booking captures:
- `driver` → the accepted driver's `User` record
- `vehicle` → the selected `Vehicle`
- `cargoRequest` → the `Freight`
- `estimatedPrice` → `DispatchAssignment.agreed_amount` (= `CarrierOffer.agreed_amount`)
- `carrierCompanyId` → winning company
- `carrierOfferId` → Phase 5 commercial offer
- `dispatchAssignmentId` → this dispatch
- `bid` → `null` (carrier dispatch bypasses the legacy bid flow)
- `bookingStatus` → `ACCEPTED`

The agreed price is **immutable**. It flows: `CarrierOffer.agreed_amount` → `DispatchAssignment.agreed_amount` → `Booking.estimated_price`. Neither the driver, the dispatcher, nor the frontend can change it.

---

## Trip Integration

After driver acceptance:

- `Booking` is created with status `ACCEPTED`.
- `Freight` status transitions to `MATCHED`.
- Driver's `CarrierDriver.availability` transitions to `ON_TRIP`.
- The existing `TripLifecycleService.startTrip()` is called by the driver when they physically begin the trip — this is **unchanged** from prior phases.
- Trip status only has `ONGOING` and `COMPLETED` — there is no `READY` state.
- Phase 6 does **not** automatically start a trip. Driver acceptance means commercial responsibility accepted; trip starts only via the explicit trip-start action.

---

## Role Matrix

| Role | Create Dispatch | Cancel Dispatch | View Company Dispatches | View Own Assignments | Accept/Reject |
|------|:-:|:-:|:-:|:-:|:-:|
| CARRIER_OWNER | ✓ | ✓ | ✓ | — | — |
| FLEET_MANAGER | ✓ | ✓ | ✓ | — | — |
| DISPATCHER | ✓ | ✓ | ✓ | — | — |
| DRIVER | ✗ | ✗ | ✗ | ✓ | ✓ (own only) |
| SHIPPER | ✗ | ✗ | ✗ | — | — |
| ADMIN | via admin paths only | | | | |

DISPATCHER is a `CompanyRole` (not `User.Role`). It is the primary role for operational dispatch.

---

## Security

### Tenant Isolation

- Company identity is always resolved from JWT → `CompanyMembership` → `CarrierCompany`.
- `carrierCompanyId` is never trusted from the request payload.
- `@companySecurity.isMemberOf(#companyId, authentication)` gates all carrier endpoints.
- Company-scoped queries on `dispatch_assignments` use `carrier_company_id` in all WHERE clauses.

### IDOR Prevention

- Carrier dispatch detail: `findByIdAndCarrierCompanyId` — wrong company gets 404.
- Driver assignment detail: `findByIdAndDriverUserId` — wrong driver gets 404.
- Driver accept/reject: service validates `dispatch.driverUserId == caller.id`.

### Cross-Company Attack Prevention

| Attack | Defense |
|--------|---------|
| ABC dispatches XYZ driver | `CarrierDriver` scoped lookup by `(companyId, driverUserId)` — 403 if mismatch |
| ABC dispatches XYZ vehicle | `Vehicle.carrier_company_id` checked against award company — 403 if mismatch |
| XYZ dispatches ABC freight | Award company check: `offer.carrier_company_id ≠ caller.companyId` — 403 |
| XYZ views ABC dispatch | `findByIdAndCarrierCompanyId` — 404 |
| XYZ cancels ABC dispatch | Same company-scope check — 404 |
| Wrong driver accepts | `driverUserId` match check — 403 |

### Mass Assignment Prevention

`CreateDispatchRequest` only binds `driverUserId`, `vehicleId`, and `notes`. Sending extra fields (`agreedAmount`, `status`, `bookingId`, `carrierCompanyId`, etc.) in the request body is silently ignored by the DTO. These values are always derived server-side.

### Price Immutability

`DispatchAssignment.agreed_amount` is marked `updatable = false` in JPA. It is copied from `CarrierOffer.agreed_amount` at dispatch creation and can never be changed. `Booking.estimated_price` is set from this frozen value at acceptance.

---

## Concurrency

### Duplicate Active Dispatch

Two dispatchers attempting to create a dispatch simultaneously for the same freight:

1. Both acquire a pessimistic write lock on the active dispatch row (`findActiveByFreightIdForUpdate`).
2. One succeeds — the other gets `409 CONFLICT` from the unique partial index `uq_active_dispatch_per_freight`.

### Accept vs Cancel Race

`DispatchAssignment` has a `@Version` (optimistic locking) column. If a driver accepts and a carrier operator cancels simultaneously:

- One transaction wins; the other gets `ObjectOptimisticLockingFailureException` → `409 CONFLICT`.

### Duplicate Booking Prevention

- `uq_booking_dispatch_assignment` unique partial index on `bookings.dispatch_assignment_id` prevents two bookings from being created for the same dispatch.
- The service checks for an existing booking (idempotency guard) before creating one.

---

## Notifications

Phase 6 uses the existing `NotificationStoreService` (in-memory, no SMTP required):

| Event | Recipient | Type |
|-------|-----------|------|
| Dispatch created | Driver | `DISPATCH_ASSIGNMENT` |
| Driver accepts | Dispatcher (assignedBy) | `DISPATCH_DRIVER_ACCEPTED` |
| Driver rejects | Dispatcher (assignedBy) | `DISPATCH_DRIVER_REJECTED` |

Notification failure is non-fatal — the dispatch state is persisted first; a warning is logged if notification fails.

---

## Database Changes

### V67 — `dispatch_assignments` table

```sql
CREATE TABLE dispatch_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    freight_id          BIGINT NOT NULL REFERENCES freight(id),
    carrier_offer_id    BIGINT NOT NULL REFERENCES carrier_offers(id),
    carrier_company_id  BIGINT NOT NULL REFERENCES carrier_companies(id),
    driver_user_id      BIGINT NOT NULL REFERENCES users(id),
    vehicle_id          BIGINT NOT NULL REFERENCES vehicles(id),
    assigned_by         BIGINT NOT NULL REFERENCES users(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED'
                            CHECK (status IN ('ASSIGNED','DRIVER_ACCEPTED',
                                              'DRIVER_REJECTED','CANCELLED','SUPERSEDED')),
    agreed_amount       DECIMAL(15,2) NOT NULL CHECK (agreed_amount > 0),
    notes               VARCHAR(1000),
    rejection_reason    VARCHAR(1000),
    assigned_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    booking_id          BIGINT REFERENCES bookings(id),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Key indexes:
- `uq_active_dispatch_per_freight` — partial unique on `freight_id WHERE status IN ('ASSIGNED','DRIVER_ACCEPTED')`
- `uq_pending_dispatch_per_driver` — partial unique on `driver_user_id WHERE status = 'ASSIGNED'`
- `uq_pending_dispatch_per_vehicle` — partial unique on `vehicle_id WHERE status = 'ASSIGNED'`
- Standard indexes on `freight_id`, `carrier_company_id`, `driver_user_id`, `vehicle_id`, `status`

### V68 — Booking Phase 6 columns

```sql
ALTER TABLE bookings
    ADD COLUMN carrier_offer_id         BIGINT REFERENCES carrier_offers(id),
    ADD COLUMN dispatch_assignment_id   BIGINT REFERENCES dispatch_assignments(id),
    ADD COLUMN carrier_company_id       BIGINT REFERENCES carrier_companies(id);

CREATE UNIQUE INDEX uq_booking_dispatch_assignment
    ON bookings(dispatch_assignment_id)
    WHERE dispatch_assignment_id IS NOT NULL;
```

---

## API Reference

### Carrier-Facing Endpoints

All require `@companySecurity.isMemberOf(#companyId, authentication)` and a dispatch-eligible role (CARRIER_OWNER, FLEET_MANAGER, or DISPATCHER).

```
GET  /carrier-companies/{companyId}/awarded-freights
     → List<AwardedFreightSummary>
     Returns all shipments commercially awarded to this company,
     with active dispatch status (if any).

POST /carrier-companies/{companyId}/freight/{freightId}/dispatch
     Body: { "driverUserId": Long, "vehicleId": Long, "notes": String? }
     → DispatchAssignmentResponse (HTTP 201)
     Creates a new dispatch assignment.

GET  /carrier-companies/{companyId}/dispatches
     → Page<DispatchAssignmentResponse>
     All dispatch assignments for this company (paginated).

GET  /carrier-companies/{companyId}/dispatches/{dispatchId}
     → DispatchAssignmentResponse

POST /carrier-companies/{companyId}/dispatches/{dispatchId}/cancel
     → DispatchAssignmentResponse
     Cancel a pending (ASSIGNED) dispatch. Releases resources.

GET  /carrier-companies/{companyId}/freight/{freightId}/dispatches
     → List<DispatchAssignmentResponse>
     Full dispatch history for a specific awarded freight.
```

### Driver-Facing Endpoints

All require `hasRole('DRIVER')`.

```
GET  /api/driver/assignments
     → List<DispatchAssignmentResponse>
     Driver's assignment inbox (own assignments only).

GET  /api/driver/assignments/{dispatchId}
     → DispatchAssignmentResponse
     Single assignment detail. 404 if not assigned to this driver.

POST /api/driver/assignments/{dispatchId}/accept
     → DispatchAssignmentResponse
     Accept assignment. Creates Booking atomically.

POST /api/driver/assignments/{dispatchId}/reject
     Body: { "rejectionReason": String? }  (body optional)
     → DispatchAssignmentResponse
     Reject assignment. Releases resources. Carrier can redispatch.
```

---

## Frontend

### React — Carrier Dispatch Portal

File: `frontend/artifacts/freight-link/src/pages/carrier-dispatch.tsx`

Route: `/carrier-dispatch` or `/carrier-dispatch/company/:companyId`

Features:
- Lists all awarded shipments with dispatch status banners
- Dispatch dialog: selects eligible driver and vehicle from dropdowns
- Cancel pending dispatch with confirmation
- Full dispatch history per freight (dialog)
- Agreed price shown as fixed (immutable) throughout

### Flutter — Driver Assignment Inbox

Files:
- `lib/src/features/dispatch/dispatch_assignment.dart` — model + `DispatchStatus` enum
- `lib/src/features/dispatch/dispatch_repository.dart` — API calls + Riverpod providers
- `lib/src/features/dispatch/my_assignments_screen.dart` — inbox list + detail + accept/reject

Routes (to add to go_router):
- `/driver/assignments` → `MyAssignmentsScreen`
- `/driver/assignments/:id` → `AssignmentDetailScreen`

---

## Rejection and Redispatch

Driver rejection (`DRIVER_REJECTED`) does **not** revoke the commercial carrier award:

```
Driver A rejects
    ↓
DispatchAssignment A → DRIVER_REJECTED (history preserved)
Driver A → AVAILABLE (released)
Vehicle  → available (released)
    ↓
CarrierOffer remains ACCEPTED
freight.carrier_award_offer_id unchanged
    ↓
Carrier creates new DispatchAssignment B (Driver B)
    ↓
Driver B accepts → Booking created
```

---

## Automated Tests

File: `src/test/java/com/ethioloadai/carrier/dispatch/Phase6DispatchIntegrationTest.java`
Support: `DispatchTestSupport.java`

**41 tests across 13 categories:**

1. Role matrix (6 tests) — owner/fleet manager/dispatcher can dispatch; driver/shipper/unauthenticated cannot
2. Award eligibility (3 tests) — freight without award, wrong company, cancelled freight
3. Cross-company attacks (4 tests) — XYZ driver, XYZ vehicle, view, cancel
4. Dispatch creation (4 tests) — server derives company/amount, driver availability set, vehicle availability set, duplicate blocked
5. Driver eligibility (3 tests) — inactive, unavailable, ON_TRIP
6. Vehicle eligibility (3 tests) — unapproved, maintenance, insufficient capacity
7. Assignment inbox (2 tests) — driver sees own; IDOR blocked
8. Driver acceptance (5 tests) — creates booking, price correct, wrong driver blocked, XYZ driver blocked, idempotent
9. Rejection and redispatch (2 tests) — resources released, history preserved
10. Cancel (3 tests) — carrier can cancel, cancelled cannot be accepted, unrelated company blocked
11. Awarded freights view (2 tests) — carrier sees own, XYZ sees empty
12. Full workflow (1 test) — end-to-end happy path
13. Regression (3 tests) — Booking backward compat, Phase 5 offers still queryable, Phase 4 fleet ops still work

---

## PostgreSQL Verification

Script: `scripts/phase6-postgres-verification.sql`

35 checks covering:
- Flyway V66/V67/V68 present and successful, no failed migrations
- `dispatch_assignments` table structure (10 columns)
- All required indexes including partial unique indexes
- `bookings` Phase 6 columns (3 columns + unique index)
- Business integrity: no duplicate active dispatches, no duplicate bookings, no cross-company violations, price consistency
- Phase 1–5 backward compatibility (5 table/column checks)

---

## Phase 7 Readiness

Phase 6 leaves the project in a clean state for Phase 7 (Trip Completion + Proof of Delivery + Platform Commission + Carrier Settlement):

- `Booking.carrier_company_id` is populated — ready for carrier settlement queries.
- `Booking.carrier_offer_id` links back to the commercial award — agreed price is auditable.
- `Booking.commission_fee` is already calculated at booking creation.
- `Trip` already has `ONGOING` / `COMPLETED` states — completion triggers nothing financial yet.
- `Freight.status = MATCHED` after driver acceptance — ready to transition to `IN_TRANSIT` when trip starts, then `DELIVERED`/`COMPLETED`.
- Phase 7 should **not** change V67/V68 migrations. It should add V69+ for settlement tables, payment release records, and proof-of-delivery storage.
