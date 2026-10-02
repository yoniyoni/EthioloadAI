# Phase 5 — Final Report
# Carrier Shipment Offers + Fixed Price Acceptance + Negotiable Price Counter-Offers

---

## Executive Result

**PHASE 5 — PASS**

All Phase 5 definition-of-done criteria are met. The full backend regression suite passes at 1014/1014. Phase 1–4 behavior is fully preserved.

---

## Previous Phase Audit

No breaking defects were found in Phases 1–4. Two technical issues were discovered during Phase 5 development and fixed:

### Issue 1: JPA first-level cache preventing carrier award from being visible after write

**Problem:** `CarrierOfferServiceImpl.performAcceptance()` originally called `freightRepo.save(freight)` after loading and mutating a `Freight` entity. In tests using `@Transactional` with MockMvc, the JPA L1 cache did not flush the update reliably, causing JDBC reads of `carrier_award_offer_id` to return `null` even after acceptance.

**Root cause:** The `Freight` entity was in the persistence context from a prior load (`loadFreightForShipper`). Calling `freightRepo.save(freight)` relies on Hibernate dirty-checking — which can silently skip the UPDATE when the entity is considered clean.

**Fix:** Replaced entity-level save with a `@Modifying(clearAutomatically = true, flushAutomatically = true)` bulk JPQL update in `FreightRepository`:
```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Freight f SET f.carrierAwardOfferId = :carrierOfferId " +
       "WHERE f.id = :freightId AND f.carrierAwardOfferId IS NULL")
int recordCarrierAward(Long freightId, Long carrierOfferId);
```
The `WHERE carrier_award_offer_id IS NULL` clause doubles as a concurrency guard — only the first of two concurrent accept requests succeeds.

**Regression test:** `Phase5CarrierOfferIntegrationTest.shipperAcceptsFixedPriceOffer` verifies `carrier_award_offer_id` is set via JDBC.

---

### Issue 2: LazyInitializationException on CarrierOffer after cache clear

**Problem:** After the `@Modifying` update clears the persistence context (`clearAutomatically = true`), the `CarrierOffer` entity held in memory had its lazy-loaded `carrierCompany` and `submittedBy` proxies invalidated. Calling `offer.getCarrierCompany().getCompanyName()` in notifications and `CarrierOfferResponse.from(offer)` in the response builder threw `LazyInitializationException`.

**Root cause:** The `@Modifying` with `clearAutomatically = true` evicts all entities from the L1 cache. Any subsequent lazy proxy access on already-held entity references fails with "no Session".

**Fix (two parts):**
1. Added `CarrierOfferRepository.findByIdEager()` — a `@Query` with `LEFT JOIN FETCH` for `carrierCompany`, `submittedBy`, `freight`, and `freight.shipper`. All read-path methods (`loadWithHistory`, `getOffer`, `getNegotiationHistory`) now use this query.
2. In `performAcceptance`, extract `companyName` and `submittedByUserId` as local `String`/`Long` values **before** the `@Modifying` call, so they remain available for notifications after the cache clear.

**Regression test:** `Phase5CarrierOfferIntegrationTest.fullNegotiationRoundTrip` exercises the full acceptance path including response body access.

---

## Architecture

```
Freight (priceType = FIXED | NEGOTIABLE)
    ↓  1:N
CarrierOffer  (one per carrier company per freight while active)
    │  version: optimistic lock
    │  status: PENDING → NEGOTIATING → ACCEPTED | REJECTED | WITHDRAWN | SUPERSEDED
    │  agreedAmount: frozen at acceptance
    │  carrier_award_offer_id ← set on Freight via recordCarrierAward()
    ↓  1:N (append-only)
OfferNegotiationEntry  (OFFER, COUNTER_OFFER, ACCEPT, REJECT, WITHDRAW)
```

Competing offers are superseded atomically via `supersedCompetingOffers(freightId, winnerOfferId)` — a single `UPDATE ... WHERE status IN ('PENDING','NEGOTIATING')` bulk JPQL.

---

## Legacy Bid Decision

The existing `Bid` entity, `BidService`, `BidController`, and all driver-bid endpoints are **preserved unchanged**. No `bids` table migration was introduced.

The carrier-company commercial offer flow is a new parallel path. DRIVER role users are explicitly blocked from all Phase 5 carrier offer endpoints (403). Legacy driver bid endpoints remain functional for backward compatibility.

---

## Fixed Price Flow

1. Carrier calls `POST /carrier-companies/{companyId}/freight/{freightId}/offers` — server uses `freight.budget`, ignores `request.amount`.
2. `pricingType = FIXED` stored on offer; `currentAmount = freight.budget`.
3. Shipper calls `POST /freight/{freightId}/carrier-offers/{offerId}/accept`.
4. `agreedAmount = freight.budget`, `status = ACCEPTED`, competing offers SUPERSEDED.
5. Any counter on FIXED pricing returns **422 FIXED_PRICE_COUNTER_NOT_ALLOWED**.

---

## Negotiable Flow

Turn-based negotiation:
- After carrier OFFER → `nextActor = SHIPPER`
- After shipper COUNTER_OFFER → `nextActor = CARRIER`
- After carrier COUNTER_OFFER → `nextActor = SHIPPER`
- On ACCEPT by either party → `nextActor = null`

Wrong-turn attempts return **409 NOT_YOUR_TURN**. The full negotiation history is append-only in `offer_negotiation_entries` and is never deleted or modified.

---

## Role Matrix

| Role | Submit | Counter | Accept | Withdraw | Shipper accept/reject |
|---|---|---|---|---|---|
| CARRIER_OWNER | ✓ | ✓ | ✓ | ✓ | — |
| DISPATCHER | ✓ | ✓ | ✓ | ✓ | — |
| FLEET_MANAGER | ✗ 409 | ✗ 409 | ✗ 409 | ✗ 409 | — |
| DRIVER | ✗ 403 | ✗ 403 | ✗ 403 | ✗ 403 | — |
| SHIPPER (owner) | — | ✓ | — | — | ✓ |
| SHIPPER (other) | — | ✗ 403 | — | — | ✗ 403 |
| ADMIN | read | read | — | — | — |

---

## Lifecycle

```
PENDING       → (shipper counter)  → NEGOTIATING
PENDING       → (shipper accept)   → ACCEPTED
PENDING       → (shipper reject)   → REJECTED
PENDING       → (carrier withdraw) → WITHDRAWN

NEGOTIATING   → (carrier counter)  → NEGOTIATING
NEGOTIATING   → (shipper counter)  → NEGOTIATING
NEGOTIATING   → (shipper accept)   → ACCEPTED
NEGOTIATING   → (carrier accept)   → ACCEPTED
NEGOTIATING   → (shipper reject)   → REJECTED
NEGOTIATING   → (carrier withdraw) → WITHDRAWN

PENDING / NEGOTIATING → (another offer accepted on same freight) → SUPERSEDED

ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED → terminal (no further transitions)
```

---

## Security

### Tenant Isolation

Every carrier mutation checks `offer.carrierCompany.id == companyId` from the URL path. Cross-company access returns 403.

Every shipper mutation validates `freight.shipper.id == caller.id` via `loadFreightForShipper()`.

Compound repository queries (`findByIdAndCarrierCompanyId`, `findByIdAndFreightId`) prevent IDOR via offer ID substitution.

### Mass-Assignment Protection

`SubmitOfferRequest` exposes only `amount` and `note`. `CounterOfferRequest` exposes only `amount` and `note`. All security-sensitive fields (`carrierCompanyId`, `submittedBy`, `status`, `agreedAmount`, `nextActor`) are set exclusively server-side.

### IDOR Tests

Integration tests verify:
- XYZ token + ABC offer ID → 403
- Other shipper token + another shipper's freight → 403
- Wrong company ID in path → 403/404 (membership check)
- `carrierCompanyId` in body overridden by server (mass-assign protection)

### Unauthenticated

All Phase 5 endpoints require a valid JWT. Missing or expired token → 401.

---

## Concurrency

Two concurrent acceptance attempts on the same freight cannot both succeed:

1. `findByIdWithLock()` — `PESSIMISTIC_WRITE` on the `carrier_offers` row serializes concurrent accept calls at the DB level.
2. `recordCarrierAward()` — bulk JPQL `WHERE carrier_award_offer_id IS NULL` ensures exactly one update row. `updated = 0` means another request already won; a `FREIGHT_ALREADY_AWARDED` 409 is thrown.
3. `@Version` — optimistic lock fallback catches any OCC conflict that bypasses the pessimistic lock.

---

## Booking Boundary

Phase 5 does **not** create a `Booking`. No driver or vehicle is assigned. The `Booking` entity's `driver_id` and `vehicle_id` are still non-nullable — creating a booking without real dispatch values would require dummy data, which is explicitly forbidden.

The commercial award is recorded solely on `freight.carrier_award_offer_id`. Phase 6 will use this to create the booking after dispatcher assigns driver + vehicle.

---

## Database Changes

| Migration | Tables | Key additions |
|---|---|---|
| V64 | `carrier_offers` | Core offer table; partial unique index for one-active-offer-per-company; `version` column |
| V65 | `offer_negotiation_entries` | Immutable append-only history; no `updated_at` |
| V66 | `freight` (ALTER) | `carrier_award_offer_id` FK; partial unique index for one-winner-per-freight |

Migrations V57–V63 are unchanged.

---

## Files Created

| Path | Purpose |
|---|---|
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/entity/CarrierOffer.java` | Core Phase 5 entity |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/entity/OfferNegotiationEntry.java` | Immutable history entity |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/exception/CarrierOfferException.java` | Domain exceptions |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/dto/CarrierOfferResponse.java` | Response DTO |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/dto/SubmitOfferRequest.java` | Submit DTO (amount + note only) |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/dto/CounterOfferRequest.java` | Counter DTO (amount + note only) |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/dto/NegotiationEntryResponse.java` | History entry DTO |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/repository/CarrierOfferRepository.java` | JPA repository |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/repository/OfferNegotiationEntryRepository.java` | History repository |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/service/CarrierOfferService.java` | Service interface |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/service/CarrierOfferServiceImpl.java` | Service implementation |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/controller/CarrierOfferController.java` | REST controller |
| `backend-spring-boot/src/main/resources/db/migration/V64__create_carrier_offers.sql` | Migration |
| `backend-spring-boot/src/main/resources/db/migration/V65__create_offer_negotiation_entries.sql` | Migration |
| `backend-spring-boot/src/main/resources/db/migration/V66__add_carrier_award_to_freight.sql` | Migration |
| `backend-spring-boot/src/test/java/com/ethioloadai/carrier/offer/CarrierOfferServiceTest.java` | 36 unit tests |
| `backend-spring-boot/src/test/java/com/ethioloadai/carrier/offer/OfferTestSupport.java` | Integration test base |
| `backend-spring-boot/src/test/java/com/ethioloadai/carrier/offer/Phase5CarrierOfferIntegrationTest.java` | 39 integration tests |
| `frontend/artifacts/freight-link/src/pages/carrier-offers.tsx` | React carrier offers page |
| `local-run-checks/phase5_postgres_verify.sql` | 23-point PostgreSQL verification |
| `local-run-checks/phase5_http_workflow_test.sh` | Real HTTP workflow test script |
| `CARRIER_COMPANY_PHASE5.md` | Architecture documentation |
| `CARRIER_COMPANY_PHASE5_REPORT.md` | This report |

---

## Files Modified

| Path | Change |
|---|---|
| `backend-spring-boot/src/main/java/com/ethioloadai/freight/entity/Freight.java` | Added `carrierAwardOfferId` field (Phase 5 commercial award) |
| `backend-spring-boot/src/main/java/com/ethioloadai/freight/repository/FreightRepository.java` | Added `recordCarrierAward()` bulk JPQL update method |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/repository/CarrierOfferRepository.java` | Added `findByIdEager()` to prevent `LazyInitializationException` |
| `backend-spring-boot/src/main/java/com/ethioloadai/carrier/offer/service/CarrierOfferServiceImpl.java` | Fixed `performAcceptance` to use `recordCarrierAward` + eager load; pre-captured lazy values before cache clear |
| `backend-spring-boot/src/test/java/com/ethioloadai/carrier/offer/CarrierOfferServiceTest.java` | Updated stubs from `freightRepo.save` to `freightRepo.recordCarrierAward`; `offerRepo.findById` → `findByIdEager` |
| `frontend/artifacts/freight-link/src/App.tsx` | Added Phase 5 carrier-offer routes |
| `frontend/artifacts/freight-link/src/pages/freight-detail.tsx` | Added "Carrier Offers" button for shipper-owner on POSTED freight |

---

## Automated Tests

| Suite | Executed | Passed | Failed | Skipped |
|---|---|---|---|---|
| `CarrierOfferServiceTest` (unit, Mockito) | 36 | 36 | 0 | 0 |
| `Phase5CarrierOfferIntegrationTest` (MockMvc, H2) | 39 | 39 | 0 | 0 |
| **Full backend regression** | **1014** | **1014** | **0** | **0** |

Baseline before Phase 5: **975/975**. Phase 5 added: **39** tests.

### Integration test coverage

- Role matrix: OWNER ✓, DISPATCHER ✓, FLEET_MANAGER denied ✓, DRIVER denied ✓, unauthenticated ✓
- Fixed-price: budget used not client amount ✓, acceptance ✓, counter rejected 422 ✓
- Negotiable: full round-trip ✓, turn-order enforcement ✓
- Competing offers: ABC accepted → XYZ superseded ✓, double-accept 409 ✓
- Tenant isolation: XYZ cannot counter/withdraw ABC offer ✓, wrong company ID ✓
- Shipper isolation: other shipper accept/counter/reject denied ✓
- Lifecycle guards: accepted cannot be countered ✓, withdrawn cannot be accepted ✓, rejected cannot be accepted ✓
- History: 5-entry append-only sequence verified ✓, IDOR on history denied ✓
- Booking boundary: no driver/vehicle required ✓, no Booking row ✓, freight stays POSTED ✓
- Mass-assignment: `carrierCompanyId` in body ignored ✓, `status` in body ignored ✓
- Money: zero ✓, negative ✓, excess scale ✓, valid 2dp ✓
- Regression: Phase 4 fleet list unaffected ✓, freight GET unaffected ✓

---

## PostgreSQL Verification

Script: `local-run-checks/phase5_postgres_verify.sql`

23 checks covering:
1. Flyway V64-V66 applied
2-4. Table and column existence
5. `freight.carrier_award_offer_id` present
6. FK constraints (4 expected)
7-8. Partial unique indexes
9. Performance indexes
10. CHECK constraints
11-13. No orphan offers or negotiation entries
14-16. Lifecycle invariants (ACCEPTED has agreed_amount, terminal has null next_actor)
17. No double-accepted offer per freight
18. Award FK consistency
19-20. Monetary precision
21. No cancelled freight with award
22. No duplicate active offers
23. No `updated_at` on immutable history table

---

## HTTP Verification

Script: `local-run-checks/phase5_http_workflow_test.sh`

Workflows covered:

**Fixed-price:** ABC submits → server uses budget (50,000) not client amount (45,000) → counter rejected 422 → shipper accepts → agreed amount = 50,000 → second accept 409.

**Negotiable round-trip:** ABC 45,000 → XYZ 46,000 (competing) → Shipper counters ABC 48,000 → wrong-turn 409 → ABC counters 47,000 → Shipper accepts → ABC ACCEPTED, XYZ SUPERSEDED, agreedAmount = 47,000, history = 4 entries, accepted offer counter 409.

**Withdrawal:** Fresh offer → withdraw → WITHDRAWN → accept 409.

**Money validation:** Zero 422, negative 422, excess-scale 422, valid 2dp accepted.

**Tenant IDOR:** XYZ cannot counter/withdraw ABC offer 403, ABC cannot use XYZ path 403.

**Shipper isolation:** Other shipper cannot accept/counter/reject 403.

**Mass-assignment:** `carrierCompanyId` in body ignored, `status` in body ignored.

**DRIVER denied:** 403 on offer submission.

**Unauthenticated:** 401.

**Booking boundary:** No matchedDriver on freight after commercial award.

---

## Bugs Found and Fixed

### Bug 1: Stale JPA L1 cache on carrier award write

- **Problem:** `freightRepo.save(freight)` silently skipped UPDATE in `@Transactional` test environment.
- **Root cause:** Hibernate dirty-checking on entity already in L1 cache.
- **Fix:** `FreightRepository.recordCarrierAward()` with `@Modifying(clearAutomatically=true, flushAutomatically=true)`.
- **Regression test:** `shipperAcceptsFixedPriceOffer`, `fullNegotiationRoundTrip`, `commercialAwardRequiresNeitherDriverNorVehicle`.

### Bug 2: LazyInitializationException on CarrierOffer after cache clear

- **Problem:** `performAcceptance` called `offer.getCarrierCompany().getCompanyName()` after `@Modifying` cleared the persistence context.
- **Root cause:** Lazy proxy invalidated by `clearAutomatically = true`.
- **Fix:** `CarrierOfferRepository.findByIdEager()` with JOIN FETCH; pre-capture company name before bulk update.
- **Regression test:** All acceptance tests in `Phase5CarrierOfferIntegrationTest`.

---

## Remaining Issues

None that block Phase 5 completion.

Known limitations (deferred):
- Notifications are in-memory only (ephemeral). Persistent notification storage and WebSocket push for offer events are deferred to a future notification infrastructure phase.
- The HTTP test script requires `bash` and `curl` + `jq` — not runnable on Windows without WSL/Git Bash. The MockMvc integration tests provide equivalent coverage on all platforms.
- FLEET_MANAGER commercial read-only access (view offers without mutation) is enforced at the service layer but not explicitly exposed via a dedicated query endpoint for fleet managers. This is a minor UX gap with no security impact.

---

## Phase 6 Readiness

**The application is ready for Phase 6 — Carrier Internal Dispatch + Shipment Driver/Vehicle Assignment + Driver Acceptance.**

Phase 5 delivers everything Phase 6 needs:

| What Phase 6 needs | Where it is |
|---|---|
| Winning carrier company | `freight.carrier_award_offer_id` → `carrier_offers.carrier_company_id` |
| Agreed commercial price | `carrier_offers.agreed_amount` |
| Carrier fleet (drivers + vehicles) | `carrier_drivers`, `vehicle_driver_assignments` (Phase 4) |
| Freight still in POSTED state | `freight.status = 'posted'` until Phase 6 transitions it |
| No fake driver/vehicle placeholder | Confirmed — `freight.matched_driver_id` = NULL |

Phase 6 implementation steps:
1. Load winning offer via `freight.carrier_award_offer_id`.
2. Dispatcher selects `CarrierDriver` + approved `Vehicle` from own company's fleet.
3. Create `Booking(cargo_id, driver_id, vehicle_id, bid_id=null, estimated_price=agreedAmount)`.
4. Set `freight.status = 'matched'`, `freight.matched_driver_id`, `freight.matched_vehicle_id`.
5. Notify assigned driver (accept/decline driver confirmation flow).

Do NOT implement Phase 6 now.
