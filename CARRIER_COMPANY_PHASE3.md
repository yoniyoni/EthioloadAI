# Carrier Company Phase 3 — Tenant Isolation / Company-Scoped Authorization

## Purpose

Phase 3 establishes strict multi-company data isolation for the EthioLoadAI platform.
The fundamental rule is:

> **ABC Logistics users must only access ABC Logistics data.**
> **XYZ Transport users must only access XYZ Transport data.**

This isolation is enforced entirely by the Spring Boot backend. Frontend state,
URL construction, or request body values supplied by the client are never trusted
as the source of company identity.

---

## Tenant Model

Each authenticated user may belong to one or more carrier companies through
`CompanyMembership`. Membership status must be `ACTIVE` to grant any
company-scoped access. The user's global account (`User`) and their company
membership are fully separate — deactivating a membership does not touch the
user account, and a disabled user cannot authenticate at all.

```
User (authenticated via JWT)
  └─▶ CompanyMembership (ACTIVE required)
        ├─ carrierpany_id  ─▶  CarrierCompany
        └─ role            ─▶  CompanyRole (CARRIER_OWNER | FLEET_MANAGER | DISPATCHER)
```

Resources are always authorised against this chain. The client supplies a
`companyId` in the URL path for company-scoped endpoints; the backend validates
that the authenticated caller has an `ACTIVE` membership in that specific company
before proceeding. Supplying a different company's ID in the URL simply yields
403 or 404 — it never grants access.

---

## Current-Company Resolution

**`CompanyContextService`** (`carrier/team/service/CompanyContextService.java`)
is the single place that answers: *"Which carrier company is this user acting for,
and what is their role?"*

### Resolution strategies

| Method | When to use |
|---|---|
| `resolveForCompany(companyId, caller)` | Path-variable endpoints — validate caller belongs to the URL company |
| `resolveUnambiguous(caller)` | Derive company when caller has exactly one active membership |
| `findForCompany(companyId, caller)` | Soft check — returns `Optional`, never throws |
| `isActiveMember(companyId, caller)` | Boolean check used by `CompanySecurityService` |

`resolveForCompany` is the standard method for all controller endpoints that carry
`{companyId}` in the path. It returns an immutable `CompanyContext` record
containing the verified membership and company. All subsequent service calls use
this context rather than any client-supplied identity.

### `CompanyContext` record

```java
record CompanyContext(CompanyMembership membership, CarrierCompany company) {
    Long companyId()          // tenant boundary
    CompanyRole role()        // caller's role in this company
    boolean isOwner()         // CARRIER_OWNER
    boolean isFleetManager()  // FLEET_MANAGER
    boolean isDispatcher()    // DISPATCHER
    boolean belongsTo(Long)   // IDOR guard
}
```

### Multiple-membership users

A user may belong to multiple companies (Phase 2 `multiCompanyMembershipIsAllowed`
test confirms this is permitted). When multiple active memberships exist,
`resolveUnambiguous` fails with `COMPANY_CONTEXT_AMBIGUOUS (400)` — callers must
supply an explicit company ID in the URL. `resolveForCompany` always works
regardless of membership count.

---

## Active Membership Requirement

Only `MembershipStatus.ACTIVE` memberships grant company-scoped access.

- `INACTIVE` — owner deactivated the employee; no company access on the next request
- `REMOVED` — employee left or was removed; must receive a new invitation to rejoin

Because authorization is resolved live from the database on every request,
deactivating a membership takes effect immediately — there is no JWT invalidation
or cache flush required. The JWT contains only the user's platform role (not
company role or membership state), so stale token content cannot bypass a
membership deactivation.

### Database enforcement

V58 migration adds `CONSTRAINT ck_owner_membership_active CHECK (role <> 'CARRIER_OWNER' OR status = 'ACTIVE')`.
A `CARRIER_OWNER` membership can never be set to `INACTIVE` or `REMOVED` at the
database level — preventing even a DBA from accidentally deactivating an owner.

---

## Company Role Matrix

### CARRIER_OWNER

Full company management. Must be `ACTIVE`.

| Permission | Endpoint |
|---|---|
| Read own company application | `GET /carrier-companies/{id}` |
| Edit DRAFT / MORE_INFO application | `PUT /carrier-companies/{id}` |
| Submit for admin review | `POST /carrier-companies/{id}/submit` |
| Upload/replace private documents | `POST /carrier-companies/{id}/documents` |
| List company documents | `GET /carrier-companies/{id}/documents` |
| Download private document | `GET /carrier-companies/{id}/documents/{docId}/file` |
| List team members | `GET /carrier-companies/{id}/team/members` |
| Change employee role | `PATCH /carrier-companies/{id}/team/members/{memberId}/role` |
| Deactivate / activate / remove employee | `POST .../deactivate`, `.../activate`, `DELETE .../members/{id}` |
| Send invitation | `POST /carrier-companies/{id}/team/invitations` |
| List invitations | `GET /carrier-companies/{id}/team/invitations` |
| Resend / revoke invitation | `POST .../invitations/{id}/resend`, `.../revoke` |
| View company fleet (Phase 3) | `GET /carrier-companies/{id}/fleet/drivers` |
| View company vehicles (Phase 3) | `GET /carrier-companies/{id}/fleet/vehicles` |

### FLEET_MANAGER

Fleet and driver operations within the company. Cannot manage team or invitations.

| Permission | Endpoint |
|---|---|
| Read own company data | `GET /carrier-companies/{id}` |
| Read company documents | `GET /carrier-companies/{id}/documents` |
| Download company document | `GET /carrier-companies/{id}/documents/{docId}/file` |
| View company fleet | `GET /carrier-companies/{id}/fleet/drivers` |
| View company vehicles | `GET /carrier-companies/{id}/fleet/vehicles` |
| List drivers globally (shipper network) | `GET /drivers` |

Cannot: manage team members, send invitations, edit company profile, access another company's resources.

### DISPATCHER

Read-only access to company-scoped fleet data.

| Permission | Endpoint |
|---|---|
| Read own company data | `GET /carrier-companies/{id}` |
| Read company documents | `GET /carrier-companies/{id}/documents` |
| View company fleet (read) | `GET /carrier-companies/{id}/fleet/drivers` |
| View company vehicles (read) | `GET /carrier-companies/{id}/fleet/vehicles` |

Cannot: manage fleet, manage team, send invitations, access another company's resources.

### ADMIN (platform)

Platform admins retain cross-company administrative access through explicitly
authorized admin endpoints. Admin access does **not** bypass tenant checks on
regular carrier endpoints — admins must use the `/admin/carrier-companies/...`
paths for cross-company operations.

| Permission | Endpoint |
|---|---|
| List all company applications | `GET /admin/carrier-companies` |
| View any company application | `GET /admin/carrier-companies/{id}` |
| Approve / reject / request information | `POST /admin/carrier-companies/{id}/approve` etc. |
| All driver and vehicle admin operations | existing admin paths |

---

## Authorization Architecture

### `CompanySecurityService` (`carrier/team/service/CompanySecurityService.java`)

The central authorization bean registered as Spring bean `"companySecurity"`.
Every method queries the live database, so role changes and membership deactivations
take effect on the next request with no cache to invalidate.

**`@PreAuthorize` SpEL usage (controller layer):**

```java
// Anyone with an active membership in this company
@PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")

// Only CARRIER_OWNER
@PreAuthorize("@companySecurity.isOwnerOf(#companyId, authentication)")

// CARRIER_OWNER or FLEET_MANAGER (fleet writes)
@PreAuthorize("@companySecurity.canManageFleet(#companyId, authentication)")

// Member or platform ADMIN (cross-company admin read)
@PreAuthorize("@companySecurity.isMemberOrAdmin(#companyId, authentication)")
```

**Service-layer direct calls:**

```java
companySecurityService.requireOwner(companyId, caller);       // 403 on failure
companySecurityService.requireMember(companyId, caller);      // 403 on failure
companySecurityService.requireFleetManager(companyId, caller);// 403 on failure
companySecurityService.requireOwnerOrAdmin(companyId, caller);// 403 on failure
```

### `CompanyTeamAccess` (existing Phase 2, preserved)

Used internally by `CompanyMembershipService` and `CompanyInvitationService`.
Checks active CARRIER_OWNER for team write operations. Phase 3 does not replace
this — it adds `CompanySecurityService` as the broader cross-cutting bean while
`CompanyTeamAccess` remains the team-specific guard.

---

## Protected Resources

| Resource | Phase | How scoped |
|---|---|---|
| CarrierCompany (read) | 1 fix | `readable()` — owner OR active member OR admin |
| CarrierCompany (write) | 1 | `ownedForUpdate()` — owner only |
| CarrierCompanyDocument (list/download) | 1 | `readable()` — same as company read |
| CarrierCompanyDocument (upload) | 1 | `ownedForUpdate()` — owner only |
| CompanyMembership (list) | 2 | `CompanyTeamAccess.owner()` — active owner only |
| CompanyMembership (role/status change) | 2 | `CompanyTeamAccess.owner()` + `findByIdAndCarrierCompanyId` |
| CompanyInvitation (all writes) | 2 | `CompanyTeamAccess.owner()` + `findByIdAndCarrierCompanyId` |
| Company fleet drivers | 3 | `@companySecurity.isMemberOf` + `CompanyContextService` |
| Company fleet vehicles | 3 | `@companySecurity.isMemberOf` + `CompanyContextService` |
| Driver directory `GET /drivers` | 3 | `@PreAuthorize("hasAnyRole('ADMIN','FLEET_OWNER','SHIPPER')")` |

---

## Repository-Level Scoping

All cross-company IDOR attacks are defeated at the repository layer, not just the
service layer:

```java
// Membership: scoped by both company and membership ID
findByIdAndCarrierCompanyId(id, companyId)

// Document: scoped by both document and company ID
findByIdAndCarrierCompanyId(documentId, companyId)

// Invitation: scoped by both invitation and company ID
findByIdAndCarrierCompanyId(id, companyId)

// Fleet: scoped by fleet_owner_id (owner's user ID)
findByFleetOwnerId(company.getOwnerId())
```

This means an attacker who submits a valid ABC resource ID to an XYZ endpoint
receives a 403/404 before any business logic executes.

---

## Database Changes

No new Flyway migration was created. The existing V57–V60 schema provides all
necessary structure for Phase 3 tenant isolation:

- `V57` — `carrier_companies`, `carrier_company_documents` with FK constraints
- `V58` — `company_memberships` with `UNIQUE(carrier_company_id, user_id)`,
  `ck_owner_membership_active`, covering indexes
- `V59` — backfill of owner memberships (idempotent)
- `V60` — `company_invitations` with partial unique index on pending invitations

A migration (V61+) will be created in Phase 4 to add `carrier_company_id` directly
to vehicles and drivers for full schema-level tenant scoping.

---

## Inactive Membership Behavior

1. Owner calls `POST /carrier-companies/{id}/team/members/{memberId}/deactivate`
2. `CompanyMembership.status` → `INACTIVE` in the database
3. On the member's very next request:
   - `CompanyContextService.resolveForCompany()` filters to `ACTIVE` only → not found → 403
   - `CompanySecurityService.isMemberOf()` → `false` → `@PreAuthorize` fails → 403
4. The member's global `User` account is untouched — they can still authenticate
   and use non-company endpoints (e.g. `GET /freight`, personal bookings)
5. Reactivation: owner calls `.../activate` → membership returns to `ACTIVE` →
   member regains access on the next request

---

## Role-Change Behavior

Company role is **not** stored in the JWT. It is resolved live from `company_memberships`
on every request. When an owner changes a FLEET_MANAGER to DISPATCHER:

1. Database updated immediately (transactional)
2. On the member's next request — `CompanySecurityService` sees `DISPATCHER`
3. Any endpoint requiring `canManageFleet` returns 403
4. `canViewFleet` (read-only) still passes

No token invalidation, logout, or cache flush required.

---

## Phase 3 APIs Added

| Method | Path | Access |
|---|---|---|
| `GET` | `/carrier-companies/{companyId}/fleet/drivers` | Active members |
| `GET` | `/carrier-companies/{companyId}/fleet/vehicles` | Active members |

---

## Phase 4 Boundary

Phase 3 establishes the authorization architecture. Phase 4 will expand it with:

- Full carrier driver registration / onboarding flows
- Full vehicle ownership redesign (add `carrier_company_id` FK to vehicles)
- Driver-vehicle assignment under company context
- V61+ migration adding direct company FK to vehicles and drivers
- `CompanySecurityService.canManageFleet` already wired — Phase 4 controllers
  use it immediately

Do not implement Phase 4 driver/vehicle management during Phase 3.

---

## Testing Instructions

### Automated H2 tests (no external DB needed)

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd backend-spring-boot
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test" "-Dtest=Phase3TenantIsolationTest"
```

Expected: **59 tests, 0 failures**.

### Full suite

```powershell
# Ensure no PostgreSQL env vars are set
Remove-Item env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test"
```

Expected: **873 tests, 0 failures**.

### PostgreSQL verification (requires live PostgreSQL)

```powershell
$env:SPRING_DATASOURCE_URL      = "jdbc:postgresql://127.0.0.1:5432/ethioloadai_spring"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
.\mvnw.cmd test --no-transfer-progress "-Dspring.profiles.active=test" `
    "-Dtest=Phase3PostgresVerification,Phase2PostgresVerification"
```

Expected: **21 tests, 0 failures** (Phase2=8, Phase3=13).

### Live HTTP security tests (requires running application)

```powershell
& "c:\Users\HP\phase3_http_test.ps1"
```

Expected: **28 tests, 0 failures**.
