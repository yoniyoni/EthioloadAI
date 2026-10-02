# Carrier Company Phase 4 — Drivers, Vehicles, and Fleet Management

## Purpose

Phase 4 turns the carrier company infrastructure established in Phases 1–3 into a
fully operational fleet management system. A carrier company can now:

- enrol existing DRIVER users into its fleet via a `carrier_drivers` relationship
- register and manage company-owned vehicles with a direct `carrier_company_id` FK
- create, end, and track driver ↔ vehicle assignments with full history
- enforce that ABC Logistics can never access or modify XYZ Transport fleet data

Phase 3 tenant isolation (company membership checks via `CompanySecurityService`) is
the security foundation. Phase 4 builds the fleet data layer on top of it.

---

## Architecture

```
CarrierCompany
  │
  ├── CompanyMembership (Phase 2/3)
  │     ├── CARRIER_OWNER
  │     ├── FLEET_MANAGER
  │     └── DISPATCHER
  │
  ├── carrier_drivers  ──────────────── User (role=DRIVER)
  │     status: ACTIVE | INACTIVE | REMOVED
  │     availability: AVAILABLE | ASSIGNED | ON_TRIP | UNAVAILABLE
  │
  ├── vehicles (carrier_company_id FK added in Phase 4)
  │     approvalStatus: UNDER_REVIEW | APPROVED | REJECTED | SUSPENDED
  │     availabilityStatus: available | assigned | on_trip | maintenance | unavailable
  │
  └── vehicle_driver_assignments
        status: ACTIVE | ENDED | CANCELLED
        (partial unique: one ACTIVE per driver, one ACTIVE per vehicle)
```

The `User` global account is never deleted when a driver is removed from a company.
Only the `carrier_drivers` row is affected.

---

## Driver Model

### `carrier_drivers` table

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `carrier_company_id` | BIGINT FK | → `carrier_companies(id)` CASCADE |
| `driver_user_id` | BIGINT FK | → `users(id)` |
| `status` | VARCHAR(20) | ACTIVE / INACTIVE / REMOVED |
| `availability` | VARCHAR(20) | AVAILABLE / ASSIGNED / ON_TRIP / UNAVAILABLE |
| `added_by` | BIGINT FK | → `users(id)` |
| `joined_at` | TIMESTAMP | |
| `left_at` | TIMESTAMP | Set on REMOVED; null for ACTIVE |
| `notes` | VARCHAR(1000) | Fleet-manager notes |

**Unique constraint:** `(carrier_company_id, driver_user_id)` — one row per driver per company.
Multiple rows are **not** created when a driver is re-added after removal; the existing
row is reactivated instead.

### Driver lifecycle

```
(add) → ACTIVE → (deactivate) → INACTIVE → (activate) → ACTIVE
                  (remove)    → REMOVED   (cannot reactivate; must re-add)
```

INACTIVE and REMOVED drivers cannot receive vehicle assignments.
Removing a driver ends any active assignment automatically.

### Independent drivers

Users with `role=DRIVER` who have no `carrier_drivers` row remain valid independent
drivers in the platform (they can still bid on freight via the existing bid system).
Phase 4 adds company fleet management alongside, not instead of, the existing model.

---

## Vehicle Model

### Phase 4 addition to `vehicles` table

V61 adds:
```sql
ALTER TABLE vehicles ADD COLUMN carrier_company_id BIGINT
    REFERENCES carrier_companies(id) ON DELETE SET NULL;
```

Vehicles registered before Phase 4 (independent or legacy fleet) have
`carrier_company_id = NULL` — they continue to work normally.

### Vehicle approval vs availability

These are two independent concepts:

| Field | Values | Controlled by |
|---|---|---|
| `approval_status` | UNDER_REVIEW / APPROVED / REJECTED / SUSPENDED | Platform admin |
| `availability_status` | available / assigned / on_trip / maintenance / unavailable | System + fleet manager |

A vehicle must be `APPROVED` before it can be assigned. Fleet managers cannot
approve their own vehicles — only platform admins can, preserving the existing
admin approval workflow.

### Available → MAINTENANCE
Fleet managers can manually set a vehicle to `MAINTENANCE` or `UNAVAILABLE`.
`ASSIGNED` and `ON_TRIP` are system-managed and cannot be set manually.

---

## Availability States

### Driver availability transitions

| Trigger | From | To |
|---|---|---|
| addDriver / reactivate | — | AVAILABLE |
| assign | AVAILABLE | ASSIGNED |
| end assignment / reassign | ASSIGNED | AVAILABLE |
| deactivate / remove | any | UNAVAILABLE |
| manual updateDriver | AVAILABLE / UNAVAILABLE | UNAVAILABLE / AVAILABLE |

`ASSIGNED` and `ON_TRIP` cannot be set manually (protected in service layer).

### Vehicle availability transitions

| Trigger | From | To |
|---|---|---|
| registerVehicle | — | available |
| assign | available | assigned |
| end assignment / reassign | assigned | available |
| setVehicleAvailability | any | maintenance / unavailable / available |

`assigned` and `on_trip` cannot be set manually.

---

## Assignment Model

### `vehicle_driver_assignments` table

| Column | Notes |
|---|---|
| `carrier_company_id` | Tenant scope FK |
| `carrier_driver_id` | FK to `carrier_drivers.id` |
| `vehicle_id` | FK to `vehicles.id` |
| `status` | ACTIVE / ENDED / CANCELLED |
| `assigned_by` / `ended_by` | Audit trail |
| `assigned_at` / `ended_at` | Timestamps |

**Partial unique indexes** (enforced at DB level):
- `uq_active_driver_assignment` — one ACTIVE assignment per driver
- `uq_active_vehicle_assignment` — one ACTIVE assignment per vehicle

### Assign / Reassign / Unassign

**Assign:**
1. Validate driver belongs to company, is ACTIVE, and is AVAILABLE
2. Validate vehicle belongs to company, is APPROVED, and is assignable
3. End any existing ACTIVE assignment for the driver (supersedes old)
4. End any existing ACTIVE assignment for the vehicle
5. Create new ACTIVE assignment
6. Set driver → ASSIGNED, vehicle → assigned

**End assignment:**
1. Mark assignment ENDED with `ended_at` + `ended_by`
2. Restore driver → AVAILABLE (if still ACTIVE)
3. Restore vehicle → available (if still assigned)
4. History row remains permanently

**Reassign** = Assign to a new vehicle. Steps 3–4 automatically end the prior assignment.

---

## Role Matrix

| Action | CARRIER_OWNER | FLEET_MANAGER | DISPATCHER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Add / reactivate / remove driver | ✓ | ✓ | — | ✓ |
| List / view drivers | ✓ | ✓ | ✓ | ✓ |
| Register / update vehicle | ✓ | ✓ | — | ✓ |
| List / view vehicles | ✓ | ✓ | ✓ | ✓ |
| Set vehicle availability (MAINTENANCE etc.) | ✓ | ✓ | — | ✓ |
| Create / end assignment | ✓ | ✓ | — | ✓ |
| View assignments | ✓ | ✓ | ✓ | ✓ |
| Approve vehicle | — | — | — | ✓ (existing admin flow) |

Authorization is enforced via `@PreAuthorize("@companySecurity.canManageFleet(...)")` for
writes and `@PreAuthorize("@companySecurity.isMemberOf(...)")` for reads.

---

## APIs

All endpoints are under `/api/carrier-companies/{companyId}/fleet/`.
The `{companyId}` in the URL is **always validated** against the caller's active
company membership. A company ID in the request body is ignored.

### Carrier Drivers

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/drivers` | OWNER, FM | Add DRIVER user to company fleet |
| `GET` | `/drivers?status=ACTIVE` | Any member | List drivers (paged, filtered by status) |
| `GET` | `/drivers/{id}` | Any member | Get specific driver |
| `PATCH` | `/drivers/{id}` | OWNER, FM | Update notes / manual availability |
| `POST` | `/drivers/{id}/activate` | OWNER, FM | Re-activate INACTIVE driver |
| `POST` | `/drivers/{id}/deactivate` | OWNER, FM | Suspend driver (INACTIVE) |
| `DELETE` | `/drivers/{id}` | OWNER, FM | Remove driver (REMOVED, keeps history) |
| `GET` | `/drivers/{id}/assignments` | Any member | Assignment history for driver |

### Company Vehicles

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/vehicles` | OWNER, FM | Register new vehicle |
| `GET` | `/vehicles` | Any member | List company vehicles (paged) |
| `GET` | `/vehicles/{id}` | Any member | Get specific vehicle |
| `PATCH` | `/vehicles/{id}` | OWNER, FM | Update truck type, capacity, city |
| `PATCH` | `/vehicles/{id}/availability?availability=MAINTENANCE` | OWNER, FM | Set availability |
| `GET` | `/vehicles/{id}/assignments` | Any member | Assignment history for vehicle |

### Assignments

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/assignments` | OWNER, FM | Create driver ↔ vehicle assignment |
| `GET` | `/assignments` | Any member | List company assignments (paged, newest first) |
| `GET` | `/assignments/{id}` | Any member | Get specific assignment |
| `POST` | `/assignments/{id}/end` | OWNER, FM | End active assignment |

---

## Database Migrations

| Migration | Description |
|---|---|
| **V61** | Adds `carrier_company_id BIGINT NULL FK` to `vehicles` + index |
| **V62** | Creates `carrier_drivers` table with status/availability checks, unique constraint, indexes |
| **V63** | Creates `vehicle_driver_assignments` table with partial unique indexes (one ACTIVE per driver, one ACTIVE per vehicle), check constraints, backfill |

### V63 backfill

The backfill is **conservative**: only vehicles that already have a `carrier_company_id`
set AND have a valid CARRIER_OWNER membership are backfilled into `carrier_drivers`.
No records are silently assigned to the wrong company. In a fresh installation this
backfill inserts zero rows (no pre-existing company vehicles).

---

## Security

### Tenant isolation

Every service method that takes a `carrierDriverId` or `vehicleId` uses a
company-scoped repository query:

```java
driverRepo.findByIdAndCarrierCompanyId(id, companyId)   // 404 if not owned
vehicleRepo.findByIdAndCarrierCompanyId(id, companyId)  // 404 if not owned
assignmentRepo.findByIdAndCarrierCompanyId(id, companyId)
```

A crafted request using an XYZ resource ID against an ABC endpoint returns 404
(not 403) to avoid confirming the resource's existence.

### Mass-assignment protection

`RegisterCompanyVehicleRequest` and `AddCarrierDriverRequest` have no `carrierCompanyId`
field. Even if a client sends `"carrierCompanyId": 999` in the JSON body, the field
is not bound (Jackson ignores unknown properties) and the company is always set from
the URL path variable resolved against the caller's active membership.

### Concurrent assignment prevention

The assign operation uses a pessimistic write lock (`@Lock(PESSIMISTIC_WRITE)`) on the
active driver assignment row combined with the DB-level partial unique index. Two
simultaneous assign requests for the same driver will be serialised at the database level.

### Inactive membership revokes fleet access immediately

`CompanySecurityService` queries `company_memberships` live on every request. Deactivating
a team member's membership instantly removes their fleet access — no token invalidation
or cache flush needed.

---

## Testing

### Run Phase 4 automated tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
Remove-Item env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
cd backend-spring-boot
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test" "-Dtest=Phase4FleetManagementTest"
```

Expected: **66 tests, 0 failures**.

### Run full suite (all phases)

```powershell
Remove-Item env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test"
```

Expected: **939 tests, 0 failures**.

### Run PostgreSQL verification

Requires `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` env vars pointing to real PostgreSQL:

```powershell
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test" "-Dtest=Phase4PostgresVerification,Phase3PostgresVerification,Phase2PostgresVerification"
```

Expected: **42 tests, 0 failures** (Phase2=8, Phase3=13, Phase4=21).

---

## Phase 5 Boundary

Phase 4 does NOT implement:

- carrier commercial bidding or offer creation
- carrier shipment dispatch or assignment to freight requests
- driver acceptance of dispatch orders
- carrier commission redesign
- negotiation flows

These belong to **Phase 5 — Carrier Shipment Offers + Price Negotiation**.

The Phase 4 architecture is ready for Phase 5:
- `CarrierDriver` provides the verified driver list for dispatch
- `VehicleDriverAssignment` identifies which driver is in which vehicle
- `CompanySecurityService.canManageFleet()` is already wired for any new dispatch write endpoints
- The `carrier_company_id` FK on vehicles enables direct company-scoped vehicle queries for offer matching
