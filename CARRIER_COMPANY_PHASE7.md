# EthioLoadAI — Phase 7: Trip Execution, Proof of Delivery, Shipment Completion, Platform Commission & Carrier Settlement

## Overview

Phase 7 completes the end-to-end freight workflow from dispatch acceptance through final financial settlement. It builds directly on Phases 1–6 without creating duplicate systems.

---

## End-to-End Architecture

```
Shipper posts Freight
        ↓
Carrier submits CarrierOffer (Phase 5)
        ↓
Shipper accepts → CarrierOffer.agreedAmount frozen (Phase 5)
        ↓
Carrier Dispatcher creates DispatchAssignment (Phase 6)
        ↓
Driver accepts → Booking created (Phase 6)
        ↓
Driver starts Trip → Freight → IN_TRANSIT (Phase 7)
        ↓
GPS tracking (existing)
        ↓
Driver marks Arrival → Trip → ARRIVED (Phase 7)
        ↓
Driver uploads ProofOfDelivery (Phase 7)
        ↓
Trip → DELIVERY_PENDING_VERIFICATION (Phase 7)
        ↓
Shipper reviews proof (Phase 7)
       ↙                    ↘
VERIFY                    REJECT
   ↓                          ↓
Trip → COMPLETED        Trip → ARRIVED (driver resubmits)
Booking → DELIVERED
Freight → DELIVERED
Driver → AVAILABLE
Vehicle → available
CarrierSettlement created
        ↓
Admin marks settlement PAID
        ↓
Rating available (post-completion)
```

---

## Trip Lifecycle

| Status | Description | Transition To |
|---|---|---|
| `ONGOING` | Trip active, driver en route | `ARRIVED` (driver marks arrival) |
| `ARRIVED` | Driver at destination | `DELIVERY_PENDING_VERIFICATION` (proof submitted) |
| `DELIVERY_PENDING_VERIFICATION` | Proof submitted, awaiting shipper | `COMPLETED` (verified) / `ARRIVED` (rejected) |
| `COMPLETED` | Delivery complete, terminal | — |

---

## Proof of Delivery

### Storage
Files stored via existing `DocumentStorage` interface (dev: local filesystem, prod: Backblaze B2).
`storageKey` is NEVER returned in API responses — only a server-side download endpoint.

### Lifecycle
- `SUBMITTED` → `VERIFIED` (shipper confirms)
- `SUBMITTED` → `REJECTED` (shipper rejects; driver may resubmit)
- One proof per trip (DB unique constraint on `trip_id`)

### Allowed File Types
`image/jpeg`, `image/png`, `image/webp`, `application/pdf` — max 10 MB

### Access Control
| Role | Submit | View/Download | Verify/Reject |
|---|---|---|---|
| Assigned Driver | ✅ (own trip only) | ✅ | ❌ |
| Freight Shipper | ❌ | ✅ | ✅ |
| Carrier Member | ❌ | ✅ | ❌ |
| Admin | ❌ | ✅ | ✅ |
| Unrelated users | ❌ | ❌ | ❌ |

---

## Delivery Verification

**Who verifies:** Only the freight's shipper (or ADMIN override).

**Driver cannot self-verify.** Carrier cannot self-verify.

**On VERIFY:**
1. ProofOfDelivery → `VERIFIED`
2. Trip → `COMPLETED`
3. Booking → `DELIVERED`
4. Freight → `DELIVERED`
5. Driver (CarrierDriver) → `AVAILABLE`
6. Vehicle → `available`
7. CarrierSettlement created (idempotent)

**On REJECT:**
1. ProofOfDelivery → `REJECTED`
2. Trip → `ARRIVED` (driver may resubmit)
3. No settlement created

---

## Dispute Integration

Rejected proofs do NOT trigger automatic dispute creation — the shipper may open a dispute through the existing Dispute module (`POST /disputes`) referencing the freight and booking. Settlement is not created while there is an open dispute. Admin may manually block settlement via `POST /settlements/{id}/block`.

---

## Financial Model

### Commercial Amount Source
```
CarrierOffer.agreedAmount (frozen at Phase 5 acceptance)
        ↓
DispatchAssignment.agreedAmount (immutable copy)
        ↓
Booking.estimatedPrice (authoritative server value)
        ↓
CarrierSettlement.grossAmount
```
**No client can modify this chain.**

### Commission Calculation
```
grossAmount     = Booking.estimatedPrice
commissionRate  = CommissionService.getCommissionRate(false)  // 0.10 = 10% standard
commissionAmount = grossAmount × commissionRate  (BigDecimal.HALF_UP, scale=2)
netAmount       = grossAmount − commissionAmount
```

Carrier dispatch bookings always use the **standard rate (10%)** — no fleet discount applies.

### Currency
All amounts in **ETB** (Ethiopian Birr). Scale=2, RoundingMode.HALF_UP throughout.

### Settlement Recipient
**The Carrier Company**, not the individual driver. Driver payroll is out of scope.

---

## Payment Architecture

### Existing Payment Module
The legacy `Payment` entity targets `driverNetAmount` and is used for the older bid/driver booking flow. It is **not modified** for Phase 7.

### New CarrierSettlement
A dedicated `CarrierSettlement` entity handles carrier dispatch bookings. Fields:
- `grossAmount`, `commissionRate`, `commissionAmount`, `netAmount`, `currency`
- `status`: `PENDING → PROCESSING → PAID` (or `FAILED`, `BLOCKED`, `CANCELLED`)
- `idempotencyKey`: `"settlement-booking-{bookingId}"` (DB unique constraint)
- `proofOfDeliveryId`: links to the triggering proof

### External Gateway Status
**NOT LIVE-VERIFIED.** No real ETB payment provider is integrated. The settlement lifecycle (PENDING → PROCESSING → PAID) is fully implemented with admin-driven transitions. A real gateway (Telebirr, CBE Birr, Chapa) can be wired in by implementing the `markProcessing`/`markPaid` flow with the provider SDK.

---

## Settlement Lifecycle

```
PENDING → PROCESSING (admin initiates)
        → PAID       (admin confirms external payment)
        → FAILED     (admin records failure; delivery stays COMPLETED)
        → BLOCKED    (open dispute; admin blocks)
FAILED  → PENDING    (admin retries — same amounts, no recalculation)
BLOCKED → PENDING    (dispute resolved)
```

### Idempotency
- DB unique constraint on `booking_id` prevents duplicate settlements
- `idempotencyKey` prevents duplicate gateway callbacks
- Retry reuses frozen commercial amounts — NEVER recalculates

### Payment Failure Semantics
Payment failure does **NOT** roll back the verified delivery. Delivery stays COMPLETED. Settlement status becomes `FAILED` and can be retried independently.

---

## Notifications

Phase 7 events send in-memory notifications via `NotificationStoreService`:

| Event | Recipient | Type |
|---|---|---|
| Proof submitted | Shipper | `PROOF_SUBMITTED` |
| Delivery verified | Driver | `DELIVERY_VERIFIED` |
| Settlement created | Carrier Owner | `SETTLEMENT_CREATED` |
| Settlement paid | Carrier Owner | `SETTLEMENT_PAID` |
| Proof rejected | Driver | `PROOF_REJECTED` |

**Note:** Notifications are in-memory only (lost on restart). Persistent notification storage is a post-Phase 7 enhancement.

---

## Ratings

- Rating only allowed after booking reaches `DELIVERED`, `COMPLETED`, or `CONFIRMED` status
- Each party (shipper / driver) may rate once per booking (DB unique constraint)
- No rating before completion; unrelated users cannot rate

---

## APIs

### Proof of Delivery
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/proof-of-delivery/trips/{tripId}/proof` | DRIVER | Submit proof (multipart) |
| `GET` | `/proof-of-delivery/trips/{tripId}/proof` | Authorized | Get proof for trip |
| `GET` | `/proof-of-delivery/{proofId}` | Authorized | Get proof by ID |
| `GET` | `/proof-of-delivery/{proofId}/download` | Authorized | Download proof file |
| `POST` | `/proof-of-delivery/{proofId}/verify` | SHIPPER/ADMIN | Verify or reject |

### Trip (Phase 7 additions)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/trips/{id}/start` | DRIVER | Start trip (existing + Phase 7 freight update) |
| `POST` | `/trips/{id}/arrive` | DRIVER | Mark arrival at destination |

### Settlements
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/settlements/{id}` | Carrier/Shipper/Admin | Get by ID |
| `GET` | `/settlements/booking/{bookingId}` | Carrier/Shipper/Admin | Get by booking |
| `GET` | `/settlements/carrier/{companyId}` | Carrier Member/Admin | Carrier financial history |
| `GET` | `/settlements` | ADMIN | All settlements |
| `POST` | `/settlements/{id}/process` | ADMIN | Mark PROCESSING |
| `POST` | `/settlements/{id}/pay` | ADMIN | Mark PAID |
| `POST` | `/settlements/{id}/fail` | ADMIN | Mark FAILED |
| `POST` | `/settlements/{id}/retry` | ADMIN | Retry FAILED |
| `POST` | `/settlements/{id}/block` | ADMIN | Block (dispute) |
| `POST` | `/settlements/{id}/unblock` | ADMIN | Unblock |

### Ratings
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/ratings` | SHIPPER/DRIVER | Submit rating |
| `GET` | `/ratings/booking/{bookingId}` | Shipper/Driver/Admin | Ratings for booking |
| `GET` | `/ratings/driver/{driverId}` | Any | Driver's rating profile |

---

## Database Migrations

| Migration | Description |
|---|---|
| `V69__phase7_trip_delivery_extensions.sql` | Adds `arrived`, `delivery_pending_verification` to trip_status CHECK; adds `arrived_at` column |
| `V70__create_proof_of_delivery.sql` | Creates `proof_of_delivery` table with FKs, status CHECK, unique constraint on `trip_id` |
| `V71__create_carrier_settlements.sql` | Creates `carrier_settlements` table with amount CHECKs, unique booking constraint, idempotency_key |

---

## Security Summary

| Attack | Defense |
|---|---|
| IDOR: unrelated user reads proof | `authorizeProofAccess` checks driver/shipper/carrier membership |
| Driver self-verifies | `authorizeVerification` rejects non-shippers |
| Carrier self-verifies | Same rejection |
| XYZ views ABC settlement | `authorizeRead` checks carrier company membership |
| Amount tampering in verify body | `VerifyDeliveryRequest` has no financial fields; amounts from server |
| Double settlement | DB unique constraint on `booking_id` + idempotency check |
| Duplicate verify | `handleVerify` returns idempotently if proof already VERIFIED |
| Payment failure erases delivery | Settlement and delivery status are independent |

---

## React UI (Phase 7)

| File | Route | Description |
|---|---|---|
| `proof-verification.tsx` | `/freight/:freightId/proof` | Shipper views and verifies/rejects proof |
| `carrier-settlement.tsx` | `/carrier/:companyId/settlements` | Carrier financial history and settlement breakdown |

---

## Flutter Driver UI (Phase 7)

| File | Description |
|---|---|
| `active_trip_screen.dart` | Full active trip lifecycle: start → arrive → proof upload → pending → completed |
| `proof_of_delivery.dart` | `ProofOfDelivery` model + `ProofStatus` enum |
| `delivery_repository.dart` | API calls for startTrip, markArrived, submitProof, getProofStatus |

---

## Role Matrix

| Action | CARRIER_OWNER | FLEET_MANAGER | DISPATCHER | DRIVER | SHIPPER | ADMIN |
|---|---|---|---|---|---|---|
| Start trip | — | — | — | ✅ (own) | — | — |
| Mark arrived | — | — | — | ✅ (own) | — | — |
| Submit proof | — | — | — | ✅ (own) | — | — |
| Verify proof | — | — | — | ❌ | ✅ (own freight) | ✅ |
| View settlement | ✅ (own company) | ✅ (own company) | ✅ (own company) | — | ✅ (own freight) | ✅ |
| Mark settlement PAID | — | — | — | — | — | ✅ |
| Block/unblock settlement | — | — | — | — | — | ✅ |
| Submit rating | — | — | — | ✅ (own booking) | ✅ (own booking) | — |

---

## Deployment Considerations

1. **Secrets management**: `JWT_SECRET`, SMTP credentials, B2 storage keys — use environment variables or secrets manager (AWS Secrets Manager / HashiCorp Vault)
2. **Object storage**: B2 `b2.key-id`, `b2.application-key`, `b2.bucket-name`, `b2.endpoint` required for production proof file storage
3. **Payment provider**: Telebirr/CBE Birr/Chapa API keys needed for real settlement payouts
4. **PostgreSQL**: Ensure Flyway migrations V1–V71 run cleanly on first deploy; verify with `phase7_postgres_verification.sql`
5. **Notification persistence**: In-memory notifications are lost on restart; consider Redis-backed or DB-backed store for production
6. **Docker**: Dockerize Spring Boot + React; separate Flutter mobile app APK/IPA build pipeline
7. **TLS/Domain**: Reverse proxy (nginx) with Let's Encrypt for production HTTPS
8. **Backups**: Daily PostgreSQL dumps; B2 bucket versioning for proof files
9. **CI/CD**: GitHub Actions: test → build → Docker push → deploy on merge to main
10. **Monitoring**: Spring Boot Actuator metrics exposed; Prometheus + Grafana for dashboards
