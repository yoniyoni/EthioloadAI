# Carrier Company Phase 3 — Verification Report

## Executive Result

**PASS**

All Phase 3 definition-of-done criteria are met. ABC Logistics users cannot access
XYZ Transport data by changing any ID. XYZ Transport users cannot access ABC
Logistics data. Inactive company members lose access immediately. Role changes take
effect on the next request. No JWT stale-permission vulnerability exists. All
regression tests pass.

---

## Phase 1 / 2 Audit

### What was checked

- V57–V60 Flyway migrations (carrier companies, memberships, backfill, invitations)
- `CarrierCompany` and `CarrierCompanyDocument` entities and repositories
- `CompanyMembership` and `CompanyInvitation` entities and repositories
- `CarrierCompanyServiceImpl` — all authorization paths
- `CompanyTeamAccess`, `CompanyMembershipService`, `CompanyInvitationService`
- `CompanyTeamController` and `CompanyInvitationController`
- `CarrierCompanyController` and `AdminCarrierCompanyController`
- `FleetController`, `FleetServiceImpl`, `DriverController`
- `VehicleController`, `VehicleRepository`
- `JwtAuthenticationFilter` (principal is raw `User`, not `UserDetailsImpl`)
- `JwtService` (token revocation via SHA-256 hash in `revoked_tokens` table)
- `SecurityConfig` (`@EnableMethodSecurity` active)
- `GlobalExceptionHandler` (all domain exceptions handled)
- All carrier company and team integration tests
- `Phase2PostgresVerification`
- `CarrierCompanyRequest` / `MemberRoleRequest` DTOs (mass assignment surface)

### Problems found

| ID | Severity | Location | Description |
|---|---|---|---|
| DEFECT-1 | High | `CarrierCompanyServiceImpl.readable()` | Only owner and ADMIN could read their own company. Active members (FLEET_MANAGER, DISPATCHER) received 404 on `GET /carrier-companies/{id}`, `GET .../documents`, and `GET .../documents/{docId}/file`. |
| DEFECT-3 | Medium | `DriverController.listDrivers()` | No role restriction — any authenticated user including drivers themselves could enumerate all platform drivers and harvest contact information. |
| DEFECT-4 | Architecture | `FleetController` | Scoped only by `User.Role.FLEET_OWNER` (platform role) — no link to carrier company membership. Phase 3 bridges this via the new company fleet endpoint. |
| DEFECT-5 | Architecture | (absent) | No centralized company-context resolution service. Each service re-implemented authorization ad-hoc. |

### What was acceptable (no change needed)

- DTOs (`CarrierCompanyRequest`, `MemberRoleRequest`, `InvitationRequest`) — no `ownerId` or `companyId` writable fields; mass-assignment is not possible.
- JWT — contains only platform role, no company claims. Dynamic DB lookup on every request is the correct design. Token revocation works.
- `CompanyTeamAccess.owner()` — correctly checks `ACTIVE` `CARRIER_OWNER` membership.
- `findByIdAndCarrierCompanyId` scoping — already correct in all repository lookups.
- `CompanyTeamException.missing()` / `CarrierCompanyException.missing()` — correctly returns 404 to avoid leaking resource existence.
- DB constraint `ck_owner_membership_active` — prevents CARRIER_OWNER deactivation at database level.

### What was repaired

| Defect | Fix | Regression test |
|---|---|---|
| DEFECT-1 | `CarrierCompanyServiceImpl.readable()` extended to allow active members to read their own company | `Phase3TenantIsolationTest.abcMemberCanReadOwnCompanyAfterPhase3Fix` |
| DEFECT-3 | `DriverController.listDrivers()` annotated `@PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")` | `Phase3TenantIsolationTest.plainDriverCannotListAllDrivers` |

---

## Architecture

```
Authenticated User
  └─ JWT → Spring Security sets principal = User entity
       └─ CompanyContextService.resolveForCompany(companyId, caller)
            └─ CompanyMembershipRepository.findByCarrierCompanyIdAndUserId()
                 └─ filter: status == ACTIVE
                      └─ CompanyContext { membership, company }
                           └─ CompanySecurityService methods / @PreAuthorize SpEL
                                └─ ALLOW or DENY (403 / 404)
```

The authentication principal is always the raw `User` entity (set by
`JwtAuthenticationFilter` via `UserDetailsImpl.getUser()`). No company identity
is ever taken from the JWT or the request body — it comes exclusively from the
`company_memberships` table queried live on each request.

---

## Company Context

**`CompanyContextService`** is the single source of truth for company resolution.

- `resolveForCompany(companyId, caller)` — standard path for all `{companyId}`
  endpoints; returns `CompanyContext` or throws 403 (returned as 404 for resource
  existence hiding)
- `resolveUnambiguous(caller)` — for future endpoints with no explicit company ID;
  fails with `COMPANY_CONTEXT_AMBIGUOUS` when multiple active memberships exist
- `findForCompany(companyId, caller)` — soft check returning `Optional`
- `isActiveMember(companyId, caller)` — boolean used by `CompanySecurityService`

---

## Authorization

**`CompanySecurityService`** (bean name `"companySecurity"`) provides all
authorization checks. Every method is a live database query — no cache, no stale
state.

`@PreAuthorize` SpEL (controller layer):
```java
@companySecurity.isMemberOf(#companyId, authentication)      // any active member
@companySecurity.isOwnerOf(#companyId, authentication)       // CARRIER_OWNER only
@companySecurity.canManageFleet(#companyId, authentication)  // OWNER or FLEET_MANAGER
@companySecurity.isMemberOrAdmin(#companyId, authentication) // member or ADMIN
```

Service-layer methods throw `CompanyTeamException` (403) on denial rather than
returning boolean, so controllers need no extra null-checks.

---

## Role Matrix

| Action | CARRIER_OWNER | FLEET_MANAGER | DISPATCHER | ADMIN |
|---|:---:|:---:|:---:|:---:|
| Read own company | ✓ | ✓ | ✓ | ✓ (any) |
| Edit DRAFT company | ✓ | — | — | — |
| Upload documents | ✓ | — | — | — |
| Download documents | ✓ | ✓ | ✓ | ✓ (any) |
| Manage team | ✓ | — | — | — |
| Send invitations | ✓ | — | — | — |
| View fleet drivers | ✓ | ✓ | ✓ | ✓ (any) |
| View fleet vehicles | ✓ | ✓ | ✓ | ✓ (any) |
| Manage fleet (Phase 4) | ✓ | ✓ | — | ✓ |
| List all platform drivers | ✓¹ | ✓¹ | — | ✓ |

¹ Via platform role (SHIPPER / FLEET_OWNER). DISPATCHER role does not have
  `hasAnyRole('SHIPPER','FLEET_OWNER')` so is excluded from global driver listing.

---

## Resources Scoped

| Module | Endpoint(s) | Scope mechanism |
|---|---|---|
| CarrierCompany | `GET /carrier-companies/{id}` | `readable()` — owner, active member, admin |
| CarrierCompany | `PUT`, `POST /submit`, `POST /documents` | `ownedForUpdate()` — owner only |
| CarrierCompanyDocument | `GET .../documents` | `readable()` |
| CarrierCompanyDocument | `GET .../documents/{docId}/file` | `readable()` + `findByIdAndCarrierCompanyId` |
| CompanyMembership | `GET .../team/members` | `CompanyTeamAccess.owner()` |
| CompanyMembership | `PATCH/POST/DELETE .../members/{id}` | `CompanyTeamAccess.owner()` + scoped repo query |
| CompanyInvitation | All `/team/invitations/**` | `CompanyTeamAccess.owner()` + scoped repo query |
| Company fleet (Phase 3) | `GET .../fleet/drivers`, `.../fleet/vehicles` | `@companySecurity.isMemberOf` + `CompanyContextService` |
| Driver directory | `GET /drivers` | `@PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")` |
| Admin companies | `GET/POST /admin/carrier-companies/**` | `@PreAuthorize("hasRole('ADMIN')")` |

---

## Database Changes

**No new Flyway migration.** Existing V57–V60 schema is sufficient for Phase 3.

The only repository change was adding two new query methods to
`CompanyMembershipRepository`:

```java
List<CompanyMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);
List<CompanyMembership> findActiveByUserId(@Param("userId") Long userId);  // JPQL
```

Phase 4 will add V61+ to put `carrier_company_id` directly on vehicles and drivers.

---

## Files Created

| File | Purpose |
|---|---|
| `carrier/team/service/CompanyContext.java` | Immutable tenant context record |
| `carrier/team/service/CompanyContextService.java` | Centralized company resolution |
| `carrier/team/service/CompanySecurityService.java` | `@PreAuthorize`-compatible authorization bean |
| `carrier/team/controller/CompanyFleetController.java` | Phase 3 company-scoped fleet read endpoints |
| `carrier/team/dto/CompanyFleetResponse.java` | Fleet response DTOs (driver + vehicle summaries) |
| `carrier/team/Phase3TenantIsolationTest.java` | 59 automated tenant isolation tests |
| `carrier/team/Phase3PostgresVerification.java` | 13 real PostgreSQL constraint verification tests |
| `CARRIER_COMPANY_PHASE3.md` | Architecture and usage documentation |
| `CARRIER_COMPANY_PHASE3_REPORT.md` | This report |

---

## Files Modified

| File | Change |
|---|---|
| `carrier/company/service/CarrierCompanyServiceImpl.java` | `readable()`: extended to allow active members (DEFECT-1 fix) |
| `carrier/team/repository/CompanyMembershipRepository.java` | Added `findActiveByUserId`, `findByUserIdAndStatus` (non-paged) |
| `carrier/company/controller/CarrierCompanyController.java` | Doc improvements (Phase 3 access semantics documented in `@Operation`) |
| `driver/controller/DriverController.java` | Added `@PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")` (DEFECT-3 fix) |

---

## Automated Tests

### Phase 3 tenant isolation tests (`Phase3TenantIsolationTest`)

| Category | Tests |
|---|---|
| Company context resolution | 7 |
| `CompanySecurityService` role checks | 8 |
| CarrierCompany IDOR | 6 |
| Company document IDOR | 4 |
| Company update/submit IDOR | 2 |
| Membership cross-company IDOR | 5 |
| Invitation cross-company IDOR | 4 |
| Company fleet tenant isolation | 7 |
| Membership deactivation / reactivation | 3 |
| Role change takes effect | 1 |
| Driver listing access control | 5 |
| Admin cross-company access | 3 |
| CompanyContext.belongsTo() | 2 |
| Multiple-membership handling | 3 |
| **Total** | **59** |

Result: **59 executed, 59 passed, 0 failed, 0 skipped**

### Full suite

| Suite | Before Phase 3 | After Phase 3 | Delta |
|---|---|---|---|
| All tests | 814 | 873 | +59 |
| Failures | 0 | 0 | 0 |
| Errors | 0 | 0 | 0 |

**873 executed, 873 passed, 0 failed.**

---

## PostgreSQL Verification

Run against real PostgreSQL 18.6 (`ethioloadai_spring` database, V60 current).

| Test class | Tests | Result |
|---|---|---|
| `Phase2PostgresVerification` | 8 | PASS |
| `Phase3PostgresVerification` | 13 | PASS |
| **Total** | **21** | **PASS** |

### Phase 3 PostgreSQL checks verified

1. Backfill created `ACTIVE` `CARRIER_OWNER` memberships for both companies
2. `uq_company_membership` prevents a user joining the same company twice
3. FK prevents membership referencing a non-existent company
4. FK prevents membership referencing a non-existent user
5. `ck_owner_membership_active` — owner membership cannot be set to INACTIVE
6. `ck_owner_membership_active` — owner membership cannot be set to REMOVED
7. FK prevents invitation referencing a non-existent company on INSERT
8. Company FK integrity on invitation insert verified
9. No orphan memberships (orphaned by company)
10. No orphan memberships (orphaned by user)
11. No duplicate memberships for same (company, user)
12. All required Phase 3 supporting indexes present
13. Backfill idempotency — running V59 twice produces exactly 2 owner memberships

---

## HTTP Tenant Tests

Run against live Spring Boot application (port 8080) with two freshly registered
companies (ABC Logistics, XYZ Transport).

| Category | Tests | Result |
|---|---|---|
| CarrierCompany IDOR | 7 | PASS |
| Company document IDOR | 3 | PASS |
| Team membership isolation | 6 | PASS |
| Company fleet isolation | 7 | PASS |
| Driver listing access control | 3 | PASS |
| Resource existence non-disclosure | 2 | PASS |
| **Total** | **28** | **PASS** |

### Selected attack scenarios verified

| Scenario | Expected | Actual |
|---|---|---|
| ABC owner reads XYZ company via path ID | 404 | 404 ✓ |
| XYZ owner reads ABC company via path ID | 404 | 404 ✓ |
| ABC owner updates XYZ company | 404 | 404 ✓ |
| ABC owner lists XYZ team members | 403 | 403 ✓ |
| ABC owner lists XYZ invitations | 403 | 403 ✓ |
| ABC owner sees XYZ fleet drivers | 403 | 403 ✓ |
| XYZ owner sees ABC fleet vehicles | 403 | 403 ✓ |
| DRIVER role lists all drivers | 403 | 403 ✓ |
| Unauthenticated fleet access | 401 | 401 ✓ |
| Non-existent company ID | 404 | 404 ✓ |

---

## Security Findings

### IDOR / object-level authorization

All tested. Every company-scoped endpoint resolves authorization from the database
before returning any data. A crafted URL substituting another company's ID yields
403 or 404 consistently.

### Role escalation

Confirmed prevented by `CompanyTeamAccess.employeeRole()` which rejects
`CARRIER_OWNER` as an assignable role. Only `FLEET_MANAGER` and `DISPATCHER` can
be assigned via invitation or role-change endpoints.

A team member cannot call team-management endpoints (403) — only the active
`CARRIER_OWNER` can modify other memberships.

### Inactive membership

Tested directly: deactivating a `FLEET_MANAGER` membership removes all company
permissions on the next request without affecting the user's global account or
requiring token invalidation.

### JWT staleness

Not a risk: company role is not in the JWT. Authorization resolves from the live
`company_memberships` table on every request. A 15-minute access token expiry
further limits exposure.

### Mass assignment

`CarrierCompanyRequest` has no `ownerId`, `companyId`, or `verificationStatus`
fields. `InvitationRequest` has no `companyId` or `status` field. The service
layer sets all sensitive fields from server context only.

### Document privacy

`CarrierCompanyDocument` download is gated by `readable()` (owner, active member,
admin) and the document lookup uses `findByIdAndCarrierCompanyId` — a document ID
alone without the matching company ID returns 404.

---

## Previous Phase Fixes

### DEFECT-1 (Phase 1) — CarrierCompanyServiceImpl.readable()

**Problem:** `readable()` only allowed `company.ownerId == caller.id OR role == ADMIN`.
Active FLEET_MANAGER and DISPATCHER members received 404 on all GET endpoints.

**Fix:** Added active-membership check after owner and admin checks:
```java
boolean isActiveMember = memberships
    .findByCarrierCompanyIdAndUserId(id, caller.getId())
    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
    .isPresent();
if (!isActiveMember) throw missing();
```

**Regression test:** `abcMemberCanReadOwnCompanyAfterPhase3Fix` — HTTP GET by
FLEET_MANAGER returns 200.

### DEFECT-3 (Phase 1/Global) — DriverController unrestricted listing

**Problem:** Any authenticated user (including drivers themselves) could call
`GET /drivers` and enumerate the full platform driver directory with names and
phone numbers.

**Fix:** Added `@PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")` to
`listDrivers()`.

**Regression tests:** `plainDriverCannotListAllDrivers` (403), `shipperCanListDrivers`
(200), `adminCanListDrivers` (200), `unauthenticatedDriverListingDenied` (401).

---

## External Integrations

### Brevo SMTP

**LIVE BREVO TEST = NOT RUN**

Reason: The test profile uses a mock `EmailService`. No live SMTP credentials were
provided in the test environment. Phase 2 SMTP integration was verified separately.
Phase 3 does not modify the invitation email path.

### Object storage (S3)

**OBJECT STORAGE TEST = NOT RUN**

Reason: S3 is mocked in tests (`@MockBean DocumentStorage`). Phase 3 does not
modify the document storage path. Phase 1 document upload/download flow verified
separately.

---

## Remaining Issues

None blocking Phase 3. Documented for Phase 4:

1. **`fleet_owner_id` ↔ `carrier_company_id` gap** — vehicles and drivers are
   currently linked to company data only via `fleet_owner_id = company.ownerId`.
   Phase 4 will add `carrier_company_id` as a direct FK on both tables via V61+.

2. **DISPATCHER driver listing** — DISPATCHER cannot currently call `GET /drivers`
   (no `SHIPPER`/`FLEET_OWNER` platform role). This is intentional for Phase 3.
   Phase 4 can grant this via company-member check if required.

3. **VehicleController `GET /vehicles`** — relies on `anyRequest().authenticated()`
   (admin-only in practice via service layer). Phase 4 should add explicit
   `@PreAuthorize("hasRole('ADMIN')")`.

4. **WebSocket company subscriptions** — existing location broadcast uses
   `tripId`-scoped channels. No company-private WebSocket channels exist yet.
   Phase 6 tracking work should add company membership validation to subscriptions.

---

## Phase 4 Readiness

**YES — architecture is ready for Phase 4.**

- `CompanyContextService` is wired and tested. Phase 4 controllers call
  `resolveForCompany(companyId, caller)` to get a verified `CompanyContext`.
- `CompanySecurityService.canManageFleet()` is ready — Phase 4 driver/vehicle
  write endpoints annotate with `@companySecurity.canManageFleet(#companyId, authentication)`.
- `CompanyRole.FLEET_MANAGER` and `CARRIER_OWNER` are the correct roles for Phase 4
  fleet management operations — no role system changes needed.
- The only schema work required is V61+ adding `carrier_company_id` to vehicles
  and (optionally) a `company_driver_id` table, as documented in the Phase 4 roadmap.
