# Fleet company onboarding and employee invitation verification

## Result

**FLEET COMPANY ONBOARDING + EMPLOYEE INVITATION WORKFLOW — PASS**

Verified on September 28, 2026 using the actual React app, Spring Boot API, PostgreSQL database, and the application's SMTP email sender connected to loopback capture. New Fleet Manager **and new Dispatcher** account creation were completed through the browser. A separate, already-registered Dispatcher also completed the login/acceptance path. All three subsequently logged out, logged back in, and retained their company role.

This is a local-stack result. Brevo inbox delivery is **not** claimed: the saved configuration points to Brevo, but test recipients were isolated `example.test` addresses, so email tests used the explicitly permitted local SMTP capture. Saved Brevo settings were not edited and were restored for the running application after testing.

## Baseline

- Ran the full backend suite before changing application code: **1,113 tests, zero failures/errors/skips**, Maven exit 0.
- Created a normal `shipper` account and logged in through React.
- Reproduced the first failure: navigating to `/fleet/companies/new` returned to `/dashboard`; no company form appeared.
- Root cause: React required the global `fleet_owner` role, while the existing backend intentionally allows an authenticated user to create a company and establishes ownership through `CompanyMembership`.
- The invitation registration endpoint and form added earlier in this session were already present at baseline. This audit reused and verified them; it did not introduce another authentication or invitation system.

Evidence: `local-run-checks/onboarding-baseline-tests.log`, `onboarding-browser-baseline.json`.

## Owner Account

PASS. The browser registered a new normal account, obtained a JWT, used logout, and logged in again. After the route repair, that account could register its company without changing its platform role. A separate isolated audit account was provisioned as ADMIN in PostgreSQL to exercise administrative review; no existing user's privileges were changed.

## Company Registration

PASS. Created ABC and XYZ audit companies through React with name, TIN, registration number, phone, email, address and city. Both began as DRAFT. The authenticated creator became owner and received an ACTIVE CARRIER_OWNER membership. Initial attempts to submit without documents failed. Company ownership is refreshed in React immediately after company creation.

Final primary audit company IDs: **ABC 22**, **XYZ 23**. Additional explicitly named audit applications exercised review-state and polling checks.

## Documents

PASS. Uploaded BUSINESS_LICENSE, BUSINESS_REGISTRATION and TIN_CERTIFICATE through browser file inputs. Metadata and file storage persisted. Owner and administrator downloaded the stored PDF successfully; a member of the unrelated company received 404. The administrator also used the browser's download action before approval.

These are `carrier_company_documents`, not driver documents. Existing policy permits active members of the same company to read company documents; unrelated-company access remains denied.

## Submission and Admin Approval

PASS. Complete applications transitioned DRAFT → PENDING → APPROVED. The administrator reviewed and approved ABC and XYZ in React. Request-information and rejection were also exercised using the actual administrative API and persisted MORE_INFORMATION_REQUIRED / REJECTED respectively.

Direct invitation requests failed for DRAFT, PENDING, MORE_INFORMATION_REQUIRED and REJECTED companies. Approved companies could invite employees. Authorization remains enforced by the backend.

Reproduced and repaired stale owner status: after a successful admin approval, an already-open owner application stayed Under review. Pending application/workspace queries now check every three seconds, stopping when the status changes. The browser regression proved approval and the invitation form appear without a manual reload.

## Invitation Creation

PASS. Owner used the Team form to submit only employee email and FLEET_MANAGER or DISPATCHER. The company-scoped URL is checked against the authenticated owner's active membership and immutable owner association. An arbitrary company URL does not confer authority. CARRIER_OWNER invitations were rejected.

The existing multi-company design uses an authorized company path, rather than trusting a company identifier supplied in the request body. Invitation registration ignores submitted email/company/role overrides and derives them from the validated invitation.

## Brevo/Email

PASS for the application's SMTP generation and delivery to local capture. Both plain text and HTML are produced by the existing `SmtpEmailService`; captured messages included Freight-Link, the company, invited role, expiry and acceptance link. Recipient and link were checked against each invitation. Real SMTP messages were captured, not mocked API invitation responses.

Saved SMTP host: `smtp-relay.brevo.com`. Credentials were neither printed nor placed in this report. Live Brevo delivery was not tested because the audit used controlled non-deliverable test addresses. The normal backend is again running with its original saved mail configuration.

## Invitation Link

PASS. Captured links resolved to `http://localhost:5174/invitations/accept#token=…`, matching the actual React route and configured FRONTEND_BASE_URL. Tokens travel to validation/acceptance in POST bodies. React removes the token from the address bar after capturing it. No tested raw invitation token appeared in the captured application log.

Web: `http://localhost:5174/`. API: `http://127.0.0.1:8080/api`. PostgreSQL: `ethioloadai_spring` on port 5432. React uses its existing API configuration and development proxy. Direct CORS preflight for origin `http://localhost:5174` returned 200 and that allowed origin.

## New User

PASS, through the browser for **both roles**:

1. Opened the actual SMTP-captured link.
2. Confirmed company, read-only invited email, assigned role and expiry.
3. Refreshed the invitation page before submission to verify token retention.
4. Entered name, required phone, password and confirmation.
5. Submitted account creation on the invitation page.
6. Existing authentication service created the account; the invitation registration transaction created the company membership and consumed the invitation.
7. React stored the returned session, loaded memberships, and opened `/company/22` with the assigned role.
8. Browser refresh and subsequent logout/login retained access.

The account and invitation acceptance use a single backend transaction. Existing registration, password hashing, JWT generation and invitation acceptance services are reused. The user cannot choose the company or company role.

## Existing User

PASS. Registered the separate Dispatcher account first, then invited its email through the owner UI. The invitation page displayed the existing-account password form, authenticated that email, accepted the invitation automatically and opened the DISPATCHER workspace. Actual logout, normal login and refresh succeeded.

Wrong-password retry and switching from a different signed-in account are covered by the isolated browser regression. The real backend also rejected a wrong authenticated email with 403 and created no membership for that account.

## Token Preservation

The existing `invitation-session.ts` captures fragment/query tokens in sessionStorage with a one-hour local deadline and strips them from the URL. The authoritative expiry remains in PostgreSQL. Token state survives page reload and login/registration navigation and is cleared after successful acceptance. It is not stored permanently in localStorage. Direct inline registration retains it until the combined backend transaction succeeds.

## Membership

Post-restart PostgreSQL evidence:

| User ID | Company ID | Company role | Membership | Invitation | Rows per company/user |
|---|---|---|---|---|---|
| 75 | 22 | DISPATCHER | ACTIVE | ACCEPTED | 1 |
| 76 | 22 | FLEET_MANAGER | ACTIVE | ACCEPTED | 1 |
| 83 | 22 | DISPATCHER | ACTIVE | ACCEPTED | 1 |
| 78 | 23 | FLEET_MANAGER | ACTIVE | ACCEPTED | 1 |
| 79 | 23 | DISPATCHER | ACTIVE | ACCEPTED | 1 |

Every accepted invitation has accepted_at set and a 64-character token hash. Company/user membership uniqueness remains protected by the database constraint. Platform `users.role` remains separate from CompanyMembership.role.

## Role Dashboards

- **Owner:** company workspace, application management, team and invitations. Normal-account owners can now discover My companies and their company workspace in navigation.
- **Fleet Manager:** company drivers, vehicles, availability and assignment forms. Actual API tests added a driver and vehicle, approved the vehicle through the admin endpoint, and created the driver/vehicle assignment. Browser showed these resources and management forms without owner invitation controls.
- **Dispatcher:** company resources and dispatch workspace, without fleet-write or owner-team controls. Actual API tests negotiated a carrier offer, awarded the freight and created a dispatch. Browser displayed the awarded shipment in Carrier Dispatch. Dispatcher fleet writes were denied.

The existing company workspace renders permissions from current memberships. Fresh normal login fetches those memberships; it does not depend on invitation state. Direct access to another company's workspace returns an error without its private resources.

One verified driver was prepared as an isolated KYC fixture for the fleet/dispatch test. That fixture preparation is not claimed as a browser test of driver KYC; company registration, document submission and company approval were exercised through their actual workflow.

## Security

PASS using the real backend/PostgreSQL:

- Wrong authenticated email: 403, no new membership.
- Expired/revoked/accepted tokens: rejected.
- Resend: old token rejected; replacement initially valid, then rejected after revocation.
- Already ACTIVE employee: controlled 409 rather than another membership.
- Unsupported CARRIER_OWNER invitation: rejected.
- Tampered registration email, company ID and owner role: invitation values prevail.
- ABC Fleet Manager/Dispatcher could not read or write XYZ resources, and XYZ employees could not access ABC.
- Employee owner-team requests and admin requests: denied.
- Unapproved companies cannot invite employees.
- Token generator uses 32 cryptographically random bytes and SHA-256 storage; existing integration/transaction tests cover acceptance races and uniqueness.

The auth service has access JWTs and persisted token revocation. No implemented refresh-token endpoint was found; refresh-expiration configuration alone does not provide one. This audit preserved that architecture.

## React

PASS:

- TypeScript: `node frontend/node_modules/typescript/bin/tsc -p frontend/artifacts/freight-link/tsconfig.json --noEmit`.
- Production Vite build, exit 0, after the final application changes.
- Real Chrome browser workflows: owner registration/login, company creation/uploads/submission, admin document download/approval, email invitation, new Fleet Manager, existing Dispatcher, new Dispatcher, logout/login, refresh, role resource UI and cross-company denial.
- Delayed real membership request: no premature platform dashboard or missing-membership message.
- Injected membership 503: explicit error and retry; retry restored correct workspace.
- Window focus refresh: retained the in-progress form.
- Separate isolated-response test: password mismatch, invalid-password retry, account switch, expired token, ignored late 401 from an older session.

The isolated-response test is supplementary. The business workflow result comes from the real stack, not mocked endpoints.

Vite emits a non-blocking existing large-chunk warning (main bundle above 500 kB). No build failure remains.

## Backend

Final full suite: **1,113 tests, zero failures, zero errors, zero skipped**, Maven exit 0. Includes company lifecycle, invitation registration, team transaction, tenant isolation, fleet, offers, dispatch, trip, delivery and settlement tests.

Examples from the final run: Phase 3 tenant isolation 59; Phase 4 fleet 67; Phase 5 offers 39; Phase 6 dispatch 42; Phase 7 delivery 48; company team integration 46; invitation registration 5. All passed.

Spring Boot was restarted and healthy afterward. Both primary employees logged in again through React after restart; SQL/API verification also checked the newly registered Dispatcher. Authorization rules were not relaxed.

## PostgreSQL

PASS. Verified users, company ownership, ACTIVE memberships, ACCEPTED invitations, accepted_at, token hashes, document rows and persistent roles. Application migrations remain through **Flyway V72**. No migration was needed for these repairs. H2-backed automated tests are reported separately from the live PostgreSQL checks.

## Bugs Found and Repairs

| Problem | Root cause | Repair | Regression evidence |
|---|---|---|---|
| Normal account cannot register a carrier company | React routes required global fleet_owner although backend establishes ownership through memberships | Authenticated company routes; discoverable company navigation | Historical baseline reproduction; final browser registration using normal account |
| Newly created owner membership not immediately available | Company creation did not refresh auth memberships | Reload memberships after successful creation | Company dashboard/team navigation in complete browser workflow |
| Initial login could expose platform dashboard while memberships were pending; fetch failure resembled no memberships | Auth marked loading finished before membership fetch; failed refresh cleared memberships | Await membership readiness in route rendering; explicit retryable membership error | Held real membership request and injected failure/retry browser tests |
| Late responses could contaminate or clear a newer account session | Membership requests and 401 handling did not check the session/request that initiated them | Session identity/request generation checks and current-token-only 401 handling | Account-switch and stale-session 401 browser regressions |
| Background refresh reset active forms/review state during the initial loading repair | Background membership reload temporarily unmounted protected pages | Block only initial membership loading; refresh existing membership state in place | Admin review rerun; focus refresh preserves typed vehicle field |
| Waiting owner still saw Under review after admin approved | Pending application query did not poll and disabled focus refresh | Poll only pending application/workspace status; stop after transition | Reproduced timeout, then passing real approval-refresh browser test |
| Company application could render owner team UI for a non-owner viewer | UI visibility checked approval but not ownership | Render application Team block only for actual owner; backend remains authoritative | Employee dashboard controls and owner-only API denials |

## Files Modified

Application repairs in this audit:

- `frontend/artifacts/freight-link/src/App.tsx`
- `frontend/artifacts/freight-link/src/components/layout.tsx`
- `frontend/artifacts/freight-link/src/components/protected-route.tsx`
- `frontend/artifacts/freight-link/src/contexts/auth-context.tsx`
- `frontend/artifacts/freight-link/src/lib/api.ts`
- `frontend/artifacts/freight-link/src/lib/carrier-companies.ts`
- `frontend/artifacts/freight-link/src/pages/carrier-companies.tsx`
- `frontend/artifacts/freight-link/src/pages/company-workspace.tsx`

Regression/audit artifacts:

- `frontend/artifacts/freight-link/tests/onboarding-browser.mjs` — added actual-stack Chrome workflow and loading/refresh regressions.
- `frontend/artifacts/freight-link/tests/invitation-password.mjs` — extended with stale-session regression.
- `backend-spring-boot/scripts/onboarding-audit.py` — added controlled fixture setup, security/role API tests and PostgreSQL verification.
- `FLEET_COMPANY_ONBOARDING_INVITATION_FINAL_REPORT.md` — this report.
- `local-run-checks/onboarding-start.ps1` — ignored local launcher for temporary SMTP capture; reads saved configuration without modifying it.

The earlier invitation account-creation implementation remains in the existing invitation page, InvitationRegistrationService/DTO/controller, preview DTO, security route configuration and InvitationRegistrationTest. Those were audited rather than replaced. Other pre-existing working-tree changes were preserved.

## Evidence and Reproduction

Sanitized result files under ignored `local-run-checks/`:

- `onboarding-browser-baseline.json`
- `onboarding-browser-full.json`
- `onboarding-browser-new-dispatcher.json`
- `onboarding-browser-roles.json`
- `onboarding-browser-approval-refresh.json`
- `onboarding-browser-restart.json`
- `onboarding-real-api-results.json`
- `onboarding-post-restart-postgres.json`
- `onboarding-final-test-counts.json`
- `onboarding-baseline-tests.log`, `onboarding-final-tests.log`
- `onboarding-web-build.log`

Fixtures with test passwords/sessions and captured links are kept only in ignored local files and are not reproduced here. Test accounts/companies are clearly named Onboarding and retained as local verification fixtures; existing business records were not reset or deleted.

The actual-stack browser script uses isolated Chrome CDP on port 9232. `init` initializes a fresh fixture file and archives a previous one; the Python script's `setup` prepares only the isolated admin. Run browser `full`, `new-dispatcher`, then Python `verify`, then browser `roles` while local SMTP capture is configured. After restoring the saved backend configuration and restarting, run browser `restart` and Python `snapshot`. `approval-refresh` independently tests an owner page awaiting admin approval. The historical `baseline` mode intentionally asserts the original route failure and is not a post-repair success test.

## Migrations

None. Existing V72 database and constraints were retained.

## Remaining Issues

No blocking issue remains in the tested onboarding/invitation workflow. Live Brevo inbox delivery, production deployment and native mobile onboarding were not part of the claimed verification. The production build's large-bundle warning remains non-blocking.
