# Carrier Company Phase 2 verification report

Date: September 23, 2026.

Implementation, automated regression, PostgreSQL, local SMTP, HTTP, and browser verification are complete. **Real Brevo verification remains pending configuration.** No live email was sent, and inbox delivery is not claimed.

## Repository Analysis

Reused the existing User JWT principal, global roles, SecurityConfig/method security, BaseException/global API envelope, Jakarta validation, MapStruct, Hibernate/JPA, company row locking, DocumentStorage, Maven/JUnit/Mockito/H2 testing, and the Phase 1 PostgreSQL verifier approach.

No existing mail sender was present. The existing RateLimitFilter is a pass-through placeholder, so company invitation cooldown/hourly limits were implemented against persisted invitations under the company lock.

Phase 1 supports multiple companies per owner. Phase 2 preserves that capability and uses explicit company routes with server-side owner/membership checks. Existing email registration behavior was preserved; invitation emails and acceptance comparisons normalize case and whitespace without imposing new global account restrictions.

V57 and earlier migrations were not changed. The existing owner_id remains authoritative for Phase 1 compatibility; active owner membership is also required for team authorization.

## Files Created

Exact paths relative to the project root:

- `backend-spring-boot/src/main/resources/db/migration/V58__create_company_memberships.sql`
- `backend-spring-boot/src/main/resources/db/migration/V59__backfill_company_owner_memberships.sql`
- `backend-spring-boot/src/main/resources/db/migration/V60__create_company_invitations.sql`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/entity/CompanyRole.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/entity/MembershipStatus.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/entity/InvitationStatus.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/entity/CompanyMembership.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/entity/CompanyInvitation.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/repository/CompanyMembershipRepository.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/repository/CompanyInvitationRepository.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/exception/CompanyTeamException.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/InvitationRequest.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/MemberRoleRequest.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/InvitationTokenRequest.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/InvitationResponse.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/MembershipResponse.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/dto/InvitationPreview.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/mapper/CompanyTeamMapper.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/config/InvitationProperties.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/email/EmailService.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/email/SmtpEmailService.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/service/InvitationTokens.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/service/CompanyTeamAccess.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/service/CompanyMembershipService.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/service/CompanyInvitationService.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/controller/CompanyTeamController.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/controller/CompanyInvitationController.java`
- `backend-spring-boot/src/main/java/com/ethioloadai/carrier/team/controller/InvitationRequestAdvice.java`
- `frontend/artifacts/freight-link/src/pages/invitation-accept.tsx`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/TeamTestSupport.java`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/CompanyTeamIntegrationTest.java`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/CompanyTeamTransactionTest.java`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/CompanyOwnerMigrationTest.java`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/SmtpEmailServiceTest.java`
- `backend-spring-boot/src/test/java/com/ethioloadai/carrier/team/Phase2PostgresVerification.java`
- `backend-spring-boot/scripts/capture-invitation-smtp.py`
- `backend-spring-boot/scripts/verify-carrier-phase2.ps1`
- `frontend/artifacts/freight-link/scripts/verify-invitation-page.mjs`
- `backend-spring-boot/CARRIER_COMPANY_PHASE2.md`
- `backend-spring-boot/CARRIER_COMPANY_PHASE2_REPORT.md`

Generated logs, build outputs, browser profiles, and temporary inspection helpers are verification artifacts, not application source. Private files, Phase 2 logs, and local-run-checks artifacts are ignored.

## Files Modified

| File | Purpose |
| --- | --- |
| backend-spring-boot/pom.xml | Add Spring Boot mail starter |
| backend-spring-boot/src/main/resources/application.yml | SMTP SSL/timeouts/debug settings; centralized invitation configuration; disable optional mail health probe |
| backend-spring-boot/src/main/resources/application-dev.yml | Development frontend URL default |
| backend-spring-boot/src/test/resources/application-test.yml | Test frontend URL |
| backend-spring-boot/src/main/java/com/ethioloadai/carrier/company/service/CarrierCompanyServiceImpl.java | Create owner membership atomically with new Phase 1 company |
| backend-spring-boot/src/main/java/com/ethioloadai/config/SecurityConfig.java | Permit only POST public invitation validation; existing protections remain |
| backend-spring-boot/src/main/java/com/ethioloadai/auth/dto/AuthenticationResponse.java | Exclude JWT from generated debug string |
| backend-spring-boot/src/main/java/com/ethioloadai/auth/dto/LoginRequest.java | Exclude password from generated debug string |
| backend-spring-boot/src/main/java/com/ethioloadai/auth/dto/RegisterRequest.java | Exclude password from generated debug string |
| backend-spring-boot/src/main/java/com/ethioloadai/exception/GlobalExceptionHandler.java | Avoid logging rejected values/JSON body fragments |
| backend-spring-boot/.env.example | Add empty SMTP/test-recipient placeholders and invitation defaults; remove credential-like values from existing examples |
| frontend/artifacts/freight-link/src/App.tsx | Add public invitation acceptance route |
| frontend/artifacts/freight-link/src/pages/login.tsx | Return to pending invitation after existing login |
| frontend/artifacts/freight-link/src/pages/register.tsx | Return to pending invitation after existing non-driver registration |
| .gitignore | Ignore generated Phase 2 logs |
| README.md | Link Phase 2 documentation |
| LOCAL_RUN.md | Describe V60 and SMTP prerequisite |
| backend-spring-boot/CARRIER_COMPANY_PHASE1.md | Explain additive owner membership integration |

The actual backend .env was not rewritten, and no real credentials were copied into source, tests, or this report.

## Flyway

- V58__create_company_memberships.sql: membership table, uniqueness, foreign keys, enum/owner-status checks, lookup/audit indexes.
- V59__backfill_company_owner_memberships.sql: idempotent owner membership backfill.
- V60__create_company_invitations.sql: invitation table, hash and active-invitation uniqueness, security/lifecycle checks, lookup/audit indexes.

All migrations ran successfully against isolated PostgreSQL/PostGIS with Hibernate schema validation. V58–V60 also applied successfully to the local application database when its backend was restarted.

## Database

company_memberships:

- Unique (carrier_company_id, user_id).
- FKs: company → carrier_companies (cascade on company deletion); user, invited_by, role_changed_by, status_changed_by → users.
- Checks: supported company roles and membership states; owner must remain ACTIVE.
- Indexes: user, company/role/status, inviter, role actor, status actor.
- Stores joinedAt, inviter, latest role/status actor and time, creation/update timestamps.

company_invitations:

- Unique token_hash.
- Partial unique (carrier_company_id, invited_email) WHERE status=PENDING.
- FKs: company → carrier_companies (cascade on company deletion); inviter, acceptor, revoker → users.
- Checks: normalized email, 64-character lowercase hexadecimal hash, employee-only roles, allowed invitation states, expiry after creation, and acceptance/revocation metadata matching status.
- Indexes: company/created/id, email, status/expiry, inviter, acceptor, revoker.

No duplicate auth/user table or global company role was added.

## Membership Model

CompanyRole is CARRIER_OWNER, FLEET_MANAGER, or DISPATCHER; User.Role stays SHIPPER, DRIVER, ADMIN, or FLEET_OWNER. MembershipStatus is ACTIVE, INACTIVE, or REMOVED. Multiple company memberships per user are allowed.

Owners can change employee roles, deactivate/reactivate, and soft-remove employees. Owner promotion/demotion/removal and ownership transfer are prohibited. Removal retains the User and membership row. Rejoining a removed employee requires a new invitation.

## Owner Migration

V59 creates ACTIVE CARRIER_OWNER memberships for all preexisting company owners with NOT EXISTS to prevent duplicate backfill. PostgreSQL and H2 migration tests execute this backfill twice and verify exactly one owner membership.

Phase 1 owner_id stays immutable and is checked alongside active owner membership. New Phase 1 registration inserts the owner membership in the company transaction. All Phase 1 tests remain passing.

## Invitation Security

Tokens use 256 SecureRandom bits and base64url encoding; only SHA-256 hashes are persisted. Tokens expire, are single-use, and stop working after acceptance/revocation/replacement. Raw tokens appear only in email links and the recipient's transient acceptance flow, never response DTOs or database rows.

Links use fragments; the web page removes the token from the address bar and retains it only in sessionStorage across login/registration. Public validation/acceptance send token POST bodies, not query strings. Token DTO debug strings and parse-error handling avoid echoing token values.

Acceptance rechecks the authenticated User email, company approval, pending/unexpired state, and membership, then creates/reactivates membership and consumes the invitation atomically. Company and role come from the invitation. Parent row locking serializes acceptance, replacement, and revocation, with a post-lock entity refresh to avoid stale state.

Duplicate sends preserve historical invitation rows and leave one pending token. Company-wide cooldown and hourly counts prevent repeated mail sends across recipients and application instances.

## Brevo

Added provider-independent EmailService and a Spring JavaMailSender implementation. SMTP configuration comes from environment/.env through Spring configuration, using port 465 implicit SSL by default, authentication, hostname verification, bounded timeouts, and disabled SMTP debug.

Emails have plain-text and HTML alternatives, escaped company content, role, UTC expiry, acceptance link, and an unexpected-invitation notice. SMTP internals are converted to a generic API error.

A send failure rolls back invitation creation/replacement. Successful SMTP acceptance is distinguished from inbox delivery. A post-send database failure or ambiguous network timeout can leave a delivered but unusable link; this synchronous consistency limitation is documented.

The optional auto-configured mail health probe is disabled, so missing SMTP configuration does not break existing application health. Actual invitation sending still requires valid configuration.

## APIs

All paths include /api. T means /api/carrier-companies/{companyId}/team.

| Method | Path | Authentication / authorization | Purpose |
| --- | --- | --- | --- |
| POST | T/invitations | JWT; ACTIVE primary owner; approved company | Send employee invitation |
| GET | T/invitations | JWT; ACTIVE primary owner | List private invitation history |
| POST | T/invitations/{id}/resend | JWT; ACTIVE primary owner; approved company | Replace token and send |
| POST | T/invitations/{id}/revoke | JWT; ACTIVE primary owner; approved company | Revoke invitation |
| GET | T/members | JWT; ACTIVE primary owner | List team details |
| PATCH | T/members/{id}/role | JWT; ACTIVE primary owner; approved company | Change employee role |
| POST | T/members/{id}/deactivate | JWT; ACTIVE primary owner; approved company | Make membership INACTIVE |
| POST | T/members/{id}/activate | JWT; ACTIVE primary owner; approved company | Reactivate membership |
| DELETE | T/members/{id} | JWT; ACTIVE primary owner; approved company | Soft-remove membership |
| POST | /api/company-invitations/validate | Public; valid invitation token | Limited invitation metadata |
| POST | /api/company-invitations/accept | JWT; matching invited email; approved company | Atomic acceptance |
| GET | /api/company-memberships/me | JWT; own active memberships only | Discover company responsibilities |

Wrong-company/non-owner management returns 403. Admin status alone does not authorize team management. Full details and request/response examples are in CARRIER_COMPANY_PHASE2.md.

## Tests

Final executed results:

| Suite/check | Run | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Entire backend regression suite | 814 | 814 | 0 | 0 |
| PostgreSQL Phase 1/2 API, concurrency, migration constraints | 88 | 88 | 0 | 0 |
| Browser acceptance checks | 3 | 3 | 0 | 0 |

The 60 new default-suite tests comprise 46 API/integration cases, 7 transactional/concurrency cases, 4 owner-migration tests, and 3 mail tests. The dedicated PostgreSQL migration class contributes five additional checks in the explicit PostgreSQL run; it is not a skipped default-suite class.

Coverage includes ownership backfill/idempotence, unique/FK/check constraints, allowed roles, company isolation, active-member rejection, normalization, secure hashes, invalid/expired/revoked/replayed/replaced tokens, safe public metadata, matching-email acceptance, normal registration, client role/company override rejection, team role/status changes, protected owner membership, global user independence, resend/revoke authorization, SMTP content/configuration/errors, rollback, concurrency, cooldown/hourly limits, and optional SMTP health isolation.

Commands executed include mvnw.cmd package, the targeted carrier/team test selectors, verify-carrier-phase2.ps1, frontend typecheck/build, and verify-invitation-page.mjs.

The full suite initially exposed a zero-cooldown timestamp-precision edge case; it was fixed. Packaging required stopping the running Windows JAR to release its file lock. Real startup exposed the optional mail health compatibility issue; it was fixed and regression-tested. Final runs above passed after those fixes.

Frontend typecheck and Vite build passed. Vite reported existing dependency sourcemap and bundle-size warnings; no build failure occurred.

## PostgreSQL Verification

Passed against a fresh isolated PostgreSQL/PostGIS database with real Flyway migrations through V60 and Hibernate ddl-auto=validate.

The 88-test run includes the 30 existing Phase 1 PostgreSQL API/transaction cases, 53 Phase 2 API/transaction cases, and five PostgreSQL schema checks. Verified actual partial uniqueness, token uniqueness/hash shape, normalized email, membership uniqueness, user/company FKs, role/status checks, owner backfill/idempotence, and concurrent acceptance/resend/revoke.

Temporary schemas and verification database were removed. No test employee/account data was added to the normal application database.

## HTTP Verification

Passed using the packaged executable JAR, actual HTTP/JWT/registration endpoints, PostgreSQL, and Spring Mail talking to a loopback SMTP capture server.

Verified owner membership; invitation creation and SMTP message capture; public validation; new employee registration; acceptance; server-controlled company/role despite injected request fields; team listing; role change; deactivate/reactivate; removal; and employee management denial with 403.

The company was created through the Phase 1 API, then approval was seeded in the isolated database for the team-specific HTTP check. Phase 1's full review/upload workflow remains covered by its existing tests and earlier real HTTP verification.

The browser used synthetic responses to check missing-token messaging, fragment stripping, login return, explicit acceptance, session-token cleanup, and expired-link handling. This browser check is separate from the real backend HTTP check.

## Real Email Verification

**Not performed. No real invitation was sent.**

The configured SMTP variables and INVITATION_TEST_RECIPIENT were not present with values in the backend .env when checked. The user was given the exact test-recipient variable to add.

Required to complete this verification: SMTP_HOST, SMTP_PORT, SMTP_SECURE, SMTP_USER, SMTP_PASSWORD, EMAIL_FROM, and INVITATION_TEST_RECIPIENT in backend-spring-boot/.env. FRONTEND_BASE_URL should point to the intended recipient-accessible web app.

The supplied verifier supports -LiveEmail only after its automated/HTTP checks pass. It records an attempt marker and refuses a second live send. Report SMTP acceptance separately from recipient-confirmed inbox delivery; neither is claimed here.

## Regression

All prior 754 tests, including all 34 Phase 1 tests, remain passing. Existing user/global roles, JWT behavior, fleet, drivers, vehicles, bookings, trips, tracking, and payments were preserved. Auth DTO changes affect debug strings only, not JSON or authentication behavior.

## Remaining Issues

- Real Brevo relay acceptance/inbox delivery remains unverified until SMTP and controlled-recipient configuration is provided. The user's complete production-email definition of done is therefore not yet verified.
- SMTP/database atomicity is bounded by the documented synchronous send strategy; no distributed transaction/outbox is claimed.
- Employee responsibilities are modeled but do not yet authorize operational company data; that is Phase 3.
- The only new web screen is invitation acceptance. Owner team-management UI and Flutter employee screens were intentionally excluded.

There are no known failing automated/backend/browser checks.

## Phase 3 Readiness

The data and authorization foundation is ready for company-wide tenant isolation across drivers, vehicles, fleet, bookings, and future company-owned operational records: company identity, active memberships, distinct company roles, secure joining, and owner management exist.

Phase 3 still needs operational ownership migrations, scoped queries, permission checks, and tenant-isolation tests. Those changes were not implemented. Production invitation rollout should wait for valid SMTP configuration and the controlled real Brevo test.

