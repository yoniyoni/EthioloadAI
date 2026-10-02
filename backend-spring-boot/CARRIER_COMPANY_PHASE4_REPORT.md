# Carrier Company Phase 4 — Verification Report

## Executive Result

**PASS**

All Phase 4 definition-of-done criteria are met. ABC Logistics and XYZ Transport
each manage their own drivers, vehicles, and assignments. Cross-company access
is denied at every tested entry point. All 939 automated tests pass. All 42
PostgreSQL constraint checks pass. All 56 live HTTP workflow and attack-scenario
tests pass.

---

## Previous Phase Audit

### Phase 1 — No defects found

All carrier company, document upload, admin review, and approval workflows are
intact. Tested via `CarrierCompanyIntegrationTest` (939-suite run).

### Phase 2 — No defects found

Memberships, invitations, team management, Brevo SMTP path all intact.
CARRIER_OWNER deactivation prevention (DB constraint `ck_owner_membership_active`)
verified. Tested via `CompanyTeamIntegrationTest`, `Phase2PostgresVerification`.

### Phase 3 — One residual architecture issue discovered and fixed

**Bug found:** `CompanyFleetController` (Phase 3 read-only fleet endpoints at
`GET /carrier-companies/{id}/fleet/drivers` and `.../vehicles`) mapped to the same
URL paths as the new Phase 4 `CarrierFleetController`, causing an ambiguous-mapping
`BeanCreationException` that prevented the entire Spring application context from loading.

**Root cause:** Phase 3's controller was not cleaned up when Phase 4 superseded it.

**Fix:** `CompanyFleetController` emptied — all driver/vehicle/assignment endpoints
now live exclusively in `CarrierFleetController`. The fleet read endpoints available
in Phase 3 are a strict superset in Phase 4 (richer data, same paths).

**Regression test:** `Phase3TenantIsolationTest` — all 59 tests still pass.
The Phase 3 HTTP paths (`GET .../fleet/drivers`, `GET .../fleet/vehicles`) now
return `CarrierDriverResponse` and `CompanyVehicleResponse` instead of the Phase 3
`CompanyFleetResponse.DriverSummary` — the data is richer, not less.

---

## Architecture

```
Authenticated User (JWT)
  └─ CompanyMembership (ACTIVE, resolved by CompanySecurityService)
       └─ CarrierCompany  (tenant boundary)
            ├─ carrier_drivers
            │    └─ User (role=DRIVER)
            ├─ vehicles  (carrier_company_id FK)
            └─ vehicle_driver_assignments
                 ├─ carrier_driver_id  → carrier_drivers
                 └─ vehicle_id         → vehicles
```

Authorization:
- Writes: `@PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")`
- Reads: `@PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")`
- All service lookups use company-scoped repository queries (IDOR protection)

---

## Existing Model Integration

| Field | Before Phase 4 | After Phase 4 |
|---|---|---|
| `vehicles.user_id` | Assigned driver's user ID (legacy) | Kept; updated to driver user ID on assignment for backward compat |
| `vehicles.fleet_owner_id` | Platform fleet owner user ID (legacy) | Kept; set to caller's ID on registerVehicle |
| `vehicles.carrier_company_id` | Did not exist | Added V61; set from URL companyId on registerVehicle |
| `users.fleet_owner_id` | Links DRIVER to FLEET_OWNER (legacy) | Unchanged; independent driver concept preserved |
| `carrier_drivers` | Did not exist | New table; separates driver identity from company relationship |

Legacy fleet module (`/fleet` endpoints, `FleetServiceImpl`) is entirely unchanged.
Independent FLEET_OWNER users and their drivers/vehicles continue to work exactly as before.

---

## Driver Model

The `carrier_drivers` table holds the relationship between a `User` (role=DRIVER)
and a `CarrierCompany`. One row per (company, driver) pair.

**Status lifecycle:** ACTIVE → INACTIVE → ACTIVE (reactivate) / REMOVED

**Availability:** AVAILABLE → ASSIGNED (on assignment) → AVAILABLE (on end)

Deactivating or removing a driver automatically ends any active vehicle assignment.
The driver's global `User` account is never deleted.

---

## Vehicle Model

`carrier_company_id` added to `vehicles` (nullable, backward compatible).
Vehicles registered before Phase 4 have `carrier_company_id = NULL` and continue
to work normally.

**Approval** (admin-controlled, unchanged from Phase 1): UNDER_REVIEW → APPROVED.
Only APPROVED vehicles can be assigned.

**Availability** (fleet-manager-controlled): available / assigned / on_trip /
maintenance / unavailable. `ASSIGNED` and `ON_TRIP` are system-managed.

---

## Availability Summary

| State | Driver | Vehicle | Assignable? |
|---|---|---|---|
| AVAILABLE / available | ✓ | ✓ | YES |
| ASSIGNED / assigned | ✓ (in use) | ✓ (in use) | Superseded by reassign |
| ON_TRIP / on_trip | — | — | NO (system-managed) |
| UNAVAILABLE / maintenance | — | — | NO |
| INACTIVE / REMOVED | — | — | NO (driver lifecycle) |

---

## Assignment

**Create:** validates company ownership for both driver and vehicle, ends any
prior active assignments (reassign flow), sets ASSIGNED/assigned, records
`assignedBy`.

**End:** marks ENDED with timestamp, restores AVAILABLE/available, records
`endedBy`. History row is permanent.

**Concurrency:** pessimistic write lock on active driver assignment + DB
partial unique index prevents duplicate ACTIVE assignments under concurrent
requests.

---

## Security

| Attack | Result | Tested |
|---|---|---|
| XYZ lists ABC drivers | 403 | HTTP + automated |
| XYZ lists ABC vehicles | 403 | HTTP + automated |
| XYZ lists ABC assignments | 403 | HTTP + automated |
| Direct driver ID with wrong company scope | 404 | HTTP + automated |
| Direct vehicle ID with wrong company scope | 404 | HTTP + automated |
| XYZ assigns ABC driver to ABC vehicle | 404 (driver not found in XYZ scope) | automated |
| XYZ ends ABC assignment | 404 | automated |
| carrierCompanyId in request body | Ignored (not bound to DTO) | HTTP + automated |
| Dispatcher creates assignment | 403 | HTTP + automated |
| Plain DRIVER lists fleet | 403 | HTTP + automated |
| Inactive membership | 403 (live DB check) | automated |
| Unauthenticated | 401 | HTTP + automated |

---

## Role Matrix

| Action | CARRIER_OWNER | FLEET_MANAGER | DISPATCHER | DRIVER | ADMIN |
|---|:---:|:---:|:---:|:---:|:---:|
| Add / remove driver | ✓ | ✓ | — | — | ✓ |
| List / view drivers | ✓ | ✓ | ✓ | — | ✓ |
| Register / update vehicle | ✓ | ✓ | — | — | ✓ |
| List / view vehicles | ✓ | ✓ | ✓ | — | ✓ |
| Set vehicle availability | ✓ | ✓ | — | — | ✓ |
| Create / end assignment | ✓ | ✓ | — | — | ✓ |
| View assignments | ✓ | ✓ | ✓ | — | ✓ |
| Approve vehicle | — | — | — | — | ✓ |

---

## Database Changes

| Migration | Tables | Key additions |
|---|---|---|
| V61 | `vehicles` | `carrier_company_id BIGINT NULL FK`, index |
| V62 | `carrier_drivers` (new) | PK, company FK, user FK, status/availability enums, `uq_carrier_driver_active`, 4 indexes |
| V63 | `vehicle_driver_assignments` (new) | PK, company FK, carrier_driver FK, vehicle FK, status check, `uq_active_driver_assignment` (partial), `uq_active_vehicle_assignment` (partial), 5 indexes, conservative backfill |

Applied to live database: 2026-09-24 16:28.

---

## Files Created

| File | Purpose |
|---|---|
| `carrier/fleet/entity/CarrierDriverStatus.java` | ACTIVE/INACTIVE/REMOVED enum |
| `carrier/fleet/entity/DriverAvailability.java` | AVAILABLE/ASSIGNED/ON_TRIP/UNAVAILABLE enum |
| `carrier/fleet/entity/VehicleAvailability.java` | AVAILABLE/ASSIGNED/ON_TRIP/MAINTENANCE/UNAVAILABLE enum |
| `carrier/fleet/entity/AssignmentStatus.java` | ACTIVE/ENDED/CANCELLED enum |
| `carrier/fleet/entity/CarrierDriver.java` | JPA entity for `carrier_drivers` |
| `carrier/fleet/entity/VehicleDriverAssignment.java` | JPA entity for `vehicle_driver_assignments` |
| `carrier/fleet/repository/CarrierDriverRepository.java` | Company-scoped driver queries |
| `carrier/fleet/repository/VehicleDriverAssignmentRepository.java` | Assignment queries with pessimistic lock |
| `carrier/fleet/exception/CarrierFleetException.java` | Domain exceptions (404/403/409/422) |
| `carrier/fleet/dto/AddCarrierDriverRequest.java` | Add driver DTO (no carrierCompanyId field) |
| `carrier/fleet/dto/UpdateCarrierDriverRequest.java` | Patch driver DTO |
| `carrier/fleet/dto/CarrierDriverResponse.java` | Driver response with enriched User data |
| `carrier/fleet/dto/RegisterCompanyVehicleRequest.java` | Register vehicle DTO |
| `carrier/fleet/dto/UpdateCompanyVehicleRequest.java` | Patch vehicle DTO |
| `carrier/fleet/dto/CompanyVehicleResponse.java` | Vehicle response |
| `carrier/fleet/dto/CreateAssignmentRequest.java` | Create assignment DTO |
| `carrier/fleet/dto/EndAssignmentRequest.java` | End assignment DTO |
| `carrier/fleet/dto/AssignmentResponse.java` | Assignment response |
| `carrier/fleet/service/CarrierFleetService.java` | Service interface |
| `carrier/fleet/service/CarrierFleetServiceImpl.java` | Full implementation |
| `carrier/fleet/controller/CarrierFleetController.java` | All Phase 4 endpoints |
| `db/migration/V61__add_carrier_company_to_vehicles.sql` | Schema migration |
| `db/migration/V62__create_carrier_drivers.sql` | Schema migration |
| `db/migration/V63__create_vehicle_driver_assignments.sql` | Schema migration |
| `carrier/fleet/FleetTestSupport.java` | Test base class (2 companies, owners, FM, dispatcher, drivers, vehicles) |
| `carrier/fleet/Phase4FleetManagementTest.java` | 66 integration tests |
| `carrier/fleet/Phase4PostgresVerification.java` | 21 real PostgreSQL checks |
| `CARRIER_COMPANY_PHASE4.md` | Architecture documentation |
| `CARRIER_COMPANY_PHASE4_REPORT.md` | This report |

---

## Files Modified

| File | Change |
|---|---|
| `vehicle/entity/Vehicle.java` | Added `carrierCompanyId` field (`@Column carrier_company_id`) |
| `vehicle/repository/VehicleRepository.java` | Added `findByCarrierCompanyId`, `findByIdAndCarrierCompanyId`, `existsByIdAndCarrierCompanyId` |
| `exception/GlobalExceptionHandler.java` | Added `@ExceptionHandler(CarrierFleetException.class)` |
| `carrier/team/controller/CompanyFleetController.java` | Emptied — all endpoints moved to `CarrierFleetController` to eliminate ambiguous URL mapping |

---

## Tests

### Automated (H2 in-memory)

| Suite | Tests | Result |
|---|---|---|
| Phase 4 fleet (`Phase4FleetManagementTest`) | 66 | **PASS** |
| All previous phases | 873 | **PASS** |
| **Total** | **939** | **PASS** |

Phase 4 test categories (66 total):
- Driver basics: add, list, get, notes, availability — 12 tests
- Vehicle basics: register, list, get, maintenance, availability — 8 tests
- Assignments: assign, end, reassign, history, deactivate ends assignment, remove ends assignment — 13 tests
- Tenant isolation service layer: driver IDOR, vehicle IDOR, cross-company assignment — 8 tests
- HTTP authorization (MockMvc): dispatcher cannot write, driver cannot list fleet, XYZ denied HTTP — 12 tests
- Mass-assignment protection — 2 tests
- Inactive membership revokes access — 1 test
- Phase 3 regression — 3 tests
- Edge cases: idempotent add, cannot reactivate REMOVED, cannot manually set ASSIGNED — 7 tests

### PostgreSQL verification (real PostgreSQL)

| Suite | Tests | Result |
|---|---|---|
| Phase 2 | 8 | **PASS** |
| Phase 3 | 13 | **PASS** |
| Phase 4 | 21 | **PASS** |
| **Total** | **42** | **PASS** |

Phase 4 PostgreSQL checks cover: V61 column + FK + index, V62 FK constraints,
status/availability CHECK constraints, `left_at` CHECK constraint, unique constraint,
required indexes, V63 partial unique indexes (no duplicate ACTIVE per driver/vehicle),
ENDED assignments allow multiple rows, check constraint on ended_at, no orphan rows.

### Live HTTP workflow (real Spring Boot + PostgreSQL)

| Category | Tests | Result |
|---|---|---|
| Driver management (add, list, get, FM denied, driver denied) | 7 | PASS |
| Vehicle management (register, list, get, IDOR, maintenance, availability) | 9 | PASS |
| Assignment workflow (create, check availability, list, get, history, end, restore) | 13 | PASS |
| Tenant isolation attacks | 14 | PASS |
| Driver lifecycle (deactivate, reactivate, remove, user preserved) | 9 | PASS |
| Phase 3 regression | 4 | PASS |
| **Total** | **56** | **PASS** |

---

## Bugs Found and Fixed

### Bug 1 — URL Mapping Conflict (Phase 3 regression fixed in Phase 4)

**Problem:** `CompanyFleetController` (Phase 3) and `CarrierFleetController` (Phase 4)
both mapped `GET /carrier-companies/{companyId}/fleet/drivers` and
`GET /carrier-companies/{companyId}/fleet/vehicles`. Spring reported an ambiguous
mapping at startup, causing `BeanCreationException` and all tests to fail with
`ApplicationContext failure threshold exceeded`.

**Root cause:** Phase 3 controller not cleaned up when Phase 4 superseded it.

**Fix:** Emptied `CompanyFleetController`. All fleet endpoints now exclusively in
`CarrierFleetController`. Phase 3 URL paths still work — they are now served by the
richer Phase 4 implementation.

**Regression test:** `Phase3TenantIsolationTest` 59/59 PASS.

### Bug 2 — H2 test data collision (FleetTestSupport fix)

**Problem:** `@BeforeEach` in `FleetTestSupport` inserted vehicles with short
plate-number suffixes (4 chars of UUID). Across 66 tests in the shared Spring context
H2 database, rare collisions on the `UNIQUE(plate_number)` constraint caused 2 tests
to fail with `CarrierFleetException: Plate number already registered`.

**Root cause:** 4-char UUID suffix (16^4 = 65536 combinations) insufficient for 66
tests each creating 4+ vehicles.

**Fix:** Increased UUID suffix to 8 chars (16^8 = ~4 billion) and added `@Transactional`
to `FleetTestSupport` so each test rolls back its data, preventing any cross-test data
leakage.

---

## External Integrations

### Brevo SMTP

**LIVE BREVO TEST = NOT RUN**

Phase 4 does not add new email functionality. Brevo integration unchanged from Phase 2.
`EmailService` is mocked in all Phase 4 tests.

### Object storage (S3)

**OBJECT STORAGE TEST = NOT RUN**

Phase 4 does not add new S3 operations. `DocumentStorage` is mocked in all Phase 4 tests.

---

## Remaining Issues

None blocking. Documented for Phase 5:

1. **`vehicle.availability_status` is still a raw String** — Phase 4 added the
   `VehicleAvailability` enum on the Java side but the DB column remains `VARCHAR`.
   A V64 migration could add a CHECK constraint to formalize the allowed values.
   Not done in Phase 4 to avoid a breaking change on existing records.

2. **ON_TRIP availability is defined but not wired to Trip lifecycle** — when a trip
   starts the driver/vehicle availability should become `ON_TRIP`. Phase 6 (tracking/dispatch
   redesign) should add this transition. Phase 4 defines the availability states and
   protects them from manual override, making Phase 6 integration straightforward.

3. **`users.fleet_owner_id` legacy field** — drivers enrolled via Phase 4
   `carrier_drivers` do not have `users.fleet_owner_id` set. The legacy
   `/fleet` module still works for FLEET_OWNER platform users. A future cleanup
   migration could deprecate `fleet_owner_id` once all fleet management goes through
   `carrier_drivers`.

4. **React admin panel** — not updated in Phase 4. The new
   `/carrier-companies/{id}/fleet/` endpoints are ready for the frontend. Phase 5
   UI work should consume them.

---

## Phase 5 Readiness

**YES — architecture is ready for Phase 5.**

| Phase 5 requirement | Phase 4 foundation |
|---|---|
| Know which drivers are available | `carrier_drivers WHERE status=ACTIVE AND availability=AVAILABLE` |
| Know which vehicles are available | `vehicles WHERE carrier_company_id=X AND approval_status=APPROVED AND availability_status=available` |
| Identify driver's current vehicle | `vehicle_driver_assignments WHERE carrier_driver_id=X AND status=ACTIVE` |
| Company-scoped offer creation | `CompanySecurityService.canManageFleet()` already wired |
| Prevent cross-company offer assignment | company-scoped repositories in place |
| Dispatcher read access | `isMemberOf()` already covers read endpoints |

Phase 5 should implement: carrier shipment offers, price negotiation, bid/counter-bid
against freight requests, and carrier-side commission settlement.
