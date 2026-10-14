# Phase 5 — Carrier Shipment Offers + Fixed Price Acceptance + Negotiable Price Counter-Offers

## Overview

Phase 5 changes the commercial actor from an individual driver to a **carrier company**. When a shipper posts freight, carrier companies (not individual drivers) discover it, submit commercial offers, negotiate prices, and reach a binding agreement. No driver or vehicle is selected in Phase 5 — that is Phase 6 dispatch.

---

## Commercial Architecture

```
SHIPPER posts Freight (POSTED, priceType = FIXED | NEGOTIABLE)
        ↓
Carrier Companies discover open freight via GET /freight
        ↓
CARRIER_OWNER or DISPATCHER submits CarrierOffer
   • server derives carrierCompanyId from JWT → membership
   • amount ignored for FIXED freight (server uses freight.budget)
        ↓
[NEGOTIABLE only] Shipper ↔ Carrier alternate counter-offers
        ↓
Either party accepts → CarrierOffer.status = ACCEPTED
   • freight.carrier_award_offer_id set atomically (bulk JPQL, no JPA cache issue)
   • all competing active offers SUPERSEDED
        ↓
Phase 5 ends here: commercial agreement recorded
        ↓
Phase 6: Dispatcher assigns Driver + Vehicle → Booking created
```

---

## Fixed-Price Flow

Freight `priceType = FIXED`, `budget = 50,000 ETB`.

1. Carrier calls `POST /carrier-companies/{companyId}/freight/{freightId}/offers` with any amount — the server ignores the client amount and sets `currentAmount = freight.budget`.
2. `pricingType = FIXED` is recorded on the offer.
3. Shipper sees the offer and calls `POST /freight/{freightId}/carrier-offers/{offerId}/accept`.
4. `agreedAmount = 50,000 ETB` is frozen on the offer.
5. Counter-offers on FIXED pricing return **HTTP 422** (`FIXED_PRICE_COUNTER_NOT_ALLOWED`).

---

## Negotiable Flow

Freight `priceType = NEGOTIABLE`.

```
1. Carrier → OFFER 45,000          nextActor = SHIPPER
2. Shipper → COUNTER_OFFER 48,000  nextActor = CARRIER
3. Carrier → COUNTER_OFFER 47,000  nextActor = SHIPPER
4. Shipper → ACCEPT                agreedAmount = 47,000, nextActor = null
```

Turn order is enforced: `nextActor` field determines who must act next. Wrong-turn attempts return **HTTP 409** (`NOT_YOUR_TURN`).

Either side may accept on their turn: the shipper uses the shipper-accept endpoint; the carrier uses the carrier-accept endpoint (only valid after a shipper counter).

---

## Domain Model

### CarrierOffer

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `freight_id` | BIGINT FK | references `freight(id)` |
| `carrier_company_id` | BIGINT FK | references `carrier_companies(id)` — server-derived, never from request body |
| `submitted_by` | BIGINT FK | references `users(id)` — server-derived from JWT |
| `pricing_type` | VARCHAR(20) | `FIXED` or `NEGOTIABLE` — mirrors freight at submission time |
| `current_amount` | DECIMAL(15,2) | denormalized current negotiated amount; updated atomically with history entry |
| `status` | VARCHAR(20) | lifecycle status (see below) |
| `next_actor` | VARCHAR(10) | `SHIPPER` or `CARRIER`; NULL in terminal states |
| `accepted_at` | TIMESTAMP | set when ACCEPTED |
| `rejected_at` | TIMESTAMP | set when REJECTED |
| `withdrawn_at` | TIMESTAMP | set when WITHDRAWN |
| `agreed_amount` | DECIMAL(15,2) | frozen at acceptance; never changed after |
| `version` | BIGINT | optimistic-lock column |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### OfferNegotiationEntry (append-only)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `carrier_offer_id` | BIGINT FK | references `carrier_offers(id)` |
| `actor_type` | VARCHAR(10) | `SHIPPER` or `CARRIER` |
| `actor_user_id` | BIGINT | who performed the action |
| `action` | VARCHAR(20) | `OFFER`, `COUNTER_OFFER`, `ACCEPT`, `REJECT`, `WITHDRAW` |
| `amount` | DECIMAL(15,2) | set for OFFER and COUNTER_OFFER; NULL for ACCEPT/REJECT/WITHDRAW |
| `note` | VARCHAR(1000) | optional message |
| `created_at` | TIMESTAMP | immutable; no `updated_at` |

### freight (modified)

New column: `carrier_award_offer_id BIGINT REFERENCES carrier_offers(id) ON DELETE SET NULL`

Updated atomically via `FreightRepository.recordCarrierAward()` (bulk JPQL `@Modifying`, `clearAutomatically = true`). Freight status remains `POSTED` until Phase 6 dispatch.

---

## Offer Status Lifecycle

```
              PENDING
             /       \
    (negotiate)     (accept direct / reject / withdraw)
            ↓              ↓
       NEGOTIATING     ACCEPTED | REJECTED | WITHDRAWN
            ↓
    ACCEPTED | REJECTED | WITHDRAWN

Any active offer (PENDING or NEGOTIATING) when another offer on the same
freight is accepted → SUPERSEDED
```

Valid transitions:

| From | Action | To |
|---|---|---|
| PENDING | shipper counter | NEGOTIATING |
| NEGOTIATING | carrier counter | NEGOTIATING |
| PENDING / NEGOTIATING | shipper accept | ACCEPTED |
| NEGOTIATING | carrier accept | ACCEPTED |
| PENDING / NEGOTIATING | shipper reject | REJECTED |
| PENDING / NEGOTIATING | carrier withdraw | WITHDRAWN |
| PENDING / NEGOTIATING | another offer accepted | SUPERSEDED |

Invalid transitions produce **HTTP 409** (`ILLEGAL_OFFER_TRANSITION`).

---

## Role Matrix

| Role | Submit offer | Counter | Carrier accept | Withdraw | Shipper accept/reject | View own offers | View freight offers |
|---|---|---|---|---|---|---|---|
| CARRIER_OWNER | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ (own) |
| DISPATCHER | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ (own) |
| FLEET_MANAGER | ✗ 409 | ✗ 409 | ✗ 409 | ✗ 409 | — | ✓ read-only | ✓ read-only |
| DRIVER | ✗ 403 | ✗ 403 | ✗ 403 | ✗ 403 | — | ✗ | ✗ |
| SHIPPER (owner) | — | ✓ | — | — | ✓ | — | ✓ (own freight) |
| SHIPPER (other) | — | ✗ 403 | — | — | ✗ 403 | — | ✗ 403 |
| ADMIN | read via admin paths | read via admin paths | — | — | — | ✓ all | ✓ all |

Role enforcement is split between two layers:

1. `@PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")` — first gate on carrier endpoints.
2. `CarrierOfferServiceImpl.requireCommercialRole(ctx, caller)` — rejects FLEET_MANAGER and DRIVER.

---

## Security

### Mass-Assignment Protection

`carrierCompanyId`, `submittedBy`, `status`, `agreedAmount`, `acceptedAt`, `nextActor` are **never read from the request body**. They are always derived server-side:

- `carrierCompanyId` → from URL path `{companyId}` + active membership validation via `CompanyContextService`
- `submittedBy` → from the authenticated JWT principal
- `status` → only changed via service lifecycle methods
- `agreedAmount` → set once at acceptance from `currentAmount`

DTOs (`SubmitOfferRequest`, `CounterOfferRequest`) only expose `amount` and `note`.

### Tenant Isolation (IDOR)

Every mutating operation includes a company-ownership check:

```java
if (!offer.getCarrierCompany().getId().equals(companyId)) {
    throw CarrierOfferException.forbidden(); // 403
}
```

Every shipper operation includes a freight-ownership check via `loadFreightForShipper()`.

Cross-freight offer lookups use compound queries (`findByIdAndFreightId`, `findByIdAndCarrierCompanyId`) to prevent IDOR via offer ID substitution.

### Concurrency — One Winner Guarantee

Two-layer protection:

1. **Pessimistic lock** — `findByIdWithLock()` uses `PESSIMISTIC_WRITE` so concurrent accept attempts block each other.
2. **Atomic bulk update** — `freightRepo.recordCarrierAward(freightId, offerId)` uses `WHERE carrier_award_offer_id IS NULL`, so only the first update succeeds; the second returns `updated = 0` and throws `FREIGHT_ALREADY_AWARDED` (409).
3. **Optimistic lock fallback** — `@Version` on `CarrierOffer` catches any race that bypasses the pessimistic lock.

---

## API Reference

### Carrier-facing endpoints

```
POST   /api/carrier-companies/{companyId}/freight/{freightId}/offers
         Body: { "amount": 45000.00, "note"?: "..." }
         → 201 CarrierOfferResponse

GET    /api/carrier-companies/{companyId}/freight/{freightId}/offers/mine
         → 200 CarrierOfferResponse

POST   /api/carrier-companies/{companyId}/offers/{offerId}/counter
         Body: { "amount": 47000.00, "note"?: "..." }
         → 200 CarrierOfferResponse

POST   /api/carrier-companies/{companyId}/offers/{offerId}/accept
         → 200 CarrierOfferResponse

POST   /api/carrier-companies/{companyId}/offers/{offerId}/withdraw
         → 200 CarrierOfferResponse

GET    /api/carrier-companies/{companyId}/offers?page=0&size=20
         → 200 Page<CarrierOfferResponse>

GET    /api/carrier-companies/{companyId}/offers/{offerId}
         → 200 CarrierOfferResponse

GET    /api/carrier-companies/{companyId}/offers/{offerId}/history
         → 200 List<NegotiationEntryResponse>
```

### Shipper-facing endpoints

```
GET    /api/freight/{freightId}/carrier-offers
         → 200 List<CarrierOfferResponse>

GET    /api/freight/{freightId}/carrier-offers/{offerId}
         → 200 CarrierOfferResponse

POST   /api/freight/{freightId}/carrier-offers/{offerId}/counter
         Body: { "amount": 48000.00, "note"?: "..." }
         → 200 CarrierOfferResponse

POST   /api/freight/{freightId}/carrier-offers/{offerId}/accept
         → 200 CarrierOfferResponse

POST   /api/freight/{freightId}/carrier-offers/{offerId}/reject
         → 200 CarrierOfferResponse

GET    /api/freight/{freightId}/carrier-offers/{offerId}/history
         → 200 List<NegotiationEntryResponse>
```

### Error codes

| HTTP | Code | Meaning |
|---|---|---|
| 404 | `CARRIER_OFFER_NOT_FOUND` | Offer or freight not found |
| 403 | `CARRIER_OFFER_ACCESS_DENIED` | IDOR or wrong actor |
| 403 | `DRIVER_COMMERCIAL_FORBIDDEN` | DRIVER/FLEET_MANAGER denied |
| 409 | `DUPLICATE_ACTIVE_OFFER` | Company already has active offer on freight |
| 409 | `ILLEGAL_OFFER_TRANSITION` | Invalid lifecycle move |
| 409 | `FREIGHT_NOT_OPEN` | Freight not in POSTED status |
| 409 | `FREIGHT_ALREADY_AWARDED` | Concurrent second acceptance attempt |
| 409 | `NOT_YOUR_TURN` | Turn-order violation |
| 422 | `FIXED_PRICE_COUNTER_NOT_ALLOWED` | Counter on FIXED freight |
| 422 | `COMPANY_NOT_APPROVED` | Carrier not yet APPROVED |

---

## Database Migrations

| Version | File | Description |
|---|---|---|
| V64 | `V64__create_carrier_offers.sql` | `carrier_offers` table with constraints, partial unique index, version column |
| V65 | `V65__create_offer_negotiation_entries.sql` | `offer_negotiation_entries` immutable audit table |
| V66 | `V66__add_carrier_award_to_freight.sql` | `carrier_award_offer_id` on `freight` + partial unique index |

Key constraints:

- `uq_active_offer_per_company_freight` — partial unique index prevents duplicate active offers per company per freight
- `uq_freight_carrier_award` — partial unique index enforces at most one winner per freight
- `ck_offer_accepted_at` — ACCEPTED requires `accepted_at IS NOT NULL AND agreed_amount IS NOT NULL`
- `ck_offer_next_actor_terminal` — terminal states require `next_actor IS NULL`
- `amount > 0` on `current_amount` and `agreed_amount`

---

## Booking Boundary

Phase 5 ends at commercial award. No `Booking` entity is created. No driver or vehicle is selected.

The freight row records the winning offer via `carrier_award_offer_id`. Freight status remains `POSTED` — Phase 6 will transition it to `MATCHED` when a dispatcher assigns a driver and vehicle.

**Important**: Never create a dummy driver or placeholder vehicle to satisfy an old booking constraint. The `Booking` entity's `driver_id` and `vehicle_id` are still non-nullable, which is why Booking creation is deferred entirely to Phase 6.

---

## Legacy Driver Bid Compatibility

The existing `Bid` entity and all legacy driver-bid endpoints (`/bids`, `/driver/bids`, etc.) are **preserved unchanged**. No migration removes or alters the `bids` table.

The carrier-company workflow is a **new parallel path**, not a replacement. Legacy individual-driver bids are effectively deprecated in favor of the carrier-company commercial offer flow, but they remain functional for backward compatibility with any existing integrations.

DRIVER role is explicitly blocked from all Phase 5 carrier offer endpoints (403).

---

## Notifications

Phase 5 sends in-memory notifications (via `NotificationStoreService`) on these events:

| Event | Recipient | Type |
|---|---|---|
| Carrier submits offer | Shipper | `CARRIER_OFFER_SUBMITTED` |
| Shipper counters | Carrier submitter | `SHIPPER_COUNTER_OFFER` |
| Carrier counters | Shipper | `CARRIER_COUNTER_OFFER` |
| Offer accepted | Shipper + Carrier | `CARRIER_OFFER_ACCEPTED` / `OFFER_ACCEPTED_NOTIFICATION` |
| Carrier withdraws | Shipper | `CARRIER_OFFER_WITHDRAWN` |
| Shipper rejects | Carrier | `SHIPPER_REJECTED_OFFER` |

Notifications are in-memory (ephemeral — lost on restart). No persistent notification table or WebSocket push for Phase 5. This is a known limitation deferred to a future phase.

---

## Money

All monetary fields use `DECIMAL(15,2)` in PostgreSQL and `BigDecimal` in Java. Scale is validated at service layer (max 2 decimal places). Zero and negative amounts are rejected (HTTP 422).

---

## Frontend

New page: `frontend/artifacts/freight-link/src/pages/carrier-offers.tsx`

Routes added to `App.tsx`:
- `/carrier-offers/freight/:freightId` — shipper inbox view
- `/carrier-offers/company/:companyId` — carrier portal view
- `/carrier-offers/company/:companyId/freight/:freightId` — carrier submitting on a freight

`freight-detail.tsx` gains a "Carrier Offers" button visible to the freight owner when freight is `POSTED`.

---

## Running Tests

```bash
# Unit tests (Mockito, no Spring context)
cd backend-spring-boot
./mvnw test -Dtest=CarrierOfferServiceTest

# Integration tests (H2, full Spring Boot context)
./mvnw test -Dtest=Phase5CarrierOfferIntegrationTest

# Full regression
./mvnw test
```

## PostgreSQL Verification

```bash
psql $DATABASE_URL -f local-run-checks/phase5_postgres_verify.sql
```

## Real HTTP Workflow Tests

```bash
# Requires running Spring Boot app on localhost:8080
./local-run-checks/phase5_http_workflow_test.sh
```

---

## Phase 6 Readiness

The application is ready for **Phase 6 — Carrier Internal Dispatch + Shipment Driver/Vehicle Assignment + Driver Acceptance**.

What Phase 6 will find:

- `freight.carrier_award_offer_id` points to the winning `CarrierOffer` with `status = ACCEPTED` and `agreedAmount` frozen.
- Freight is still `POSTED`; Phase 6 transitions it to `MATCHED` after dispatch.
- `CarrierOffer.carrierCompany` and `agreedAmount` are the inputs to Phase 6 booking creation.
- No fake driver or vehicle placeholder exists anywhere.

Phase 6 should:
1. Load the winning `CarrierOffer` via `freight.carrier_award_offer_id`.
2. Allow the company's DISPATCHER to assign a `CarrierDriver` + `Vehicle` from the fleet.
3. Create a `Booking` row with `driver_id`, `vehicle_id`, `cargo_id`, `estimated_price = agreedAmount`.
4. Set `freight.status = MATCHED`.
5. Notify the assigned driver (accept/decline flow).
