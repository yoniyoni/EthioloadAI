# Carrier Company Phase 2

Phase 2 adds company memberships, owner-managed employees, secure email invitations, and a small web acceptance page. Existing platform roles, authentication, JWTs, driver/fleet operations, and Phase 1 APIs retain their existing behavior. Broad operational tenant isolation remains Phase 3.

## Architecture and ownership

The existing User is related to CarrierCompany through CompanyMembership. CompanyRole is separate from User.Role:

- CARRIER_OWNER: the company's immutable Phase 1 primary owner.
- FLEET_MANAGER and DISPATCHER: invited employee responsibilities; they confer no new global fleet/driver permissions in Phase 2.

Users may belong to multiple companies. Membership is unique by company/user. Team routes therefore include a company ID as a selector; the server verifies that the caller is both the matching Phase 1 owner and an ACTIVE CARRIER_OWNER member. Request bodies cannot assign company ownership. Platform admins receive no override for team management.

V58 creates memberships; V59 backfills every existing company owner as ACTIVE CARRIER_OWNER. The backfill uses NOT EXISTS and is idempotent. Phase 1 owner_id stays as the immutable compatibility reference. New company creation now inserts its owner membership in the same transaction. No existing migration, including V57, was changed.

CompanyMembership stores role, status, joinedAt, invitedBy, creation/update timestamps, and the actor/time of the latest role and status change. This is lightweight auditing, not a full event ledger. Historical accepted/revoked invitations retain earlier invitation context.

## Membership lifecycle

| Current | Owner action | Result |
| --- | --- | --- |
| ACTIVE employee | Deactivate | INACTIVE |
| INACTIVE employee | Activate | ACTIVE |
| ACTIVE or INACTIVE employee | Remove | REMOVED |
| INACTIVE or REMOVED employee | Send fresh invitation; employee accepts | ACTIVE, reusing the membership row |
| ACTIVE or INACTIVE employee | Change role | FLEET_MANAGER or DISPATCHER |

A removed employee cannot be directly reactivated or have their role changed. Rejoining requires a new invitation. Repeated status transitions return 400. Owner memberships cannot be demoted, promoted, removed, or deactivated through these APIs.

Deactivation/removal never disables or deletes the User. Membership status is the foundation for later company-specific authorization, not a change to existing global operational permissions.

Owner read/list APIs work for their own company in any verification state. Team writes, invitation sending/resending/revocation, and employee acceptance require APPROVED. Phase 1 owner/company verification rules otherwise remain unchanged.

## Invitation security and delivery semantics

CompanyInvitation stores company, normalized invited email, invited role, token hash, invitation status, inviter, expiry, acceptance/revocation actors and times, and creation/update timestamps.

Tokens contain 32 SecureRandom bytes (256 bits), encoded as unpadded base64url. Only SHA-256(token) is persisted. Neither raw tokens nor token hashes appear in API response DTOs. The token request DTO deliberately has no generated toString or rejected-value validation logging. Malformed invitation JSON uses a dedicated safe error handler.

The acceptance URL is:

```text
FRONTEND_BASE_URL/invitations/accept#token=<token-from-email>
```

A fragment is used so the token is not sent to web servers as a URL/query parameter or Referer. The web page immediately removes it from the address bar, keeps it in sessionStorage for the login/registration return flow, and clears it after acceptance or invalid validation. Do not send invitation links to analytics, logs, or other users.

Public validation uses POST with a token body, deliberately replacing the conceptual GET/query example. It returns only company name, invited role/email, expiry, and validity, with Cache-Control: no-store. Acceptance also uses POST and requires a matching authenticated email, normalized by trim/lowercase. The employee never submits company or role selections.

Statuses are PENDING, ACCEPTED, EXPIRED, and REVOKED. Expiry is enforced at validation and acceptance; list responses show expired pending invitations as EXPIRED without requiring a scheduler. Accepted/revoked tokens are never reusable.

Duplicate sends and resends create a new invitation row and retire the prior pending token. An expired pending row becomes EXPIRED; an unexpired one becomes REVOKED. Resend is available for pending/expired invitations, not accepted/revoked ones. The PostgreSQL partial unique index allows only one stored PENDING invitation for a company/email. Successful replacement invalidates the old link.

A company-wide cooldown defaults to 60 seconds, including different recipients and resends. A persisted count limits sends to 20 per rolling hour by default. All invitation/member writes lock the company row before changing related state. Acceptance refreshes the invitation after obtaining that lock, ensuring concurrent acceptance, resend, and revocation see the latest state. Uniqueness constraints protect membership and token identity.

Mail sends synchronously after invitation persistence/flush but before transaction commit. SMTP exceptions produce a sanitized 503 and roll back the new row plus any replacement revocation. The previous valid link survives a failed resend. This avoids claiming success when SMTP rejects the message.

SMTP and the database do not share a distributed transaction: a connection timeout after relay acceptance or a database failure after sending can result in an email with a nonusable link. There are no automatic email retries or outbox messages containing raw tokens. An owner can resend after checking the result. A successful API response means the relay accepted the email, not that it reached the inbox.

## Brevo configuration

Spring's JavaMailSender is behind the EmailService abstraction; invitation logic is provider-independent. SmtpEmailService creates UTF-8 plain-text and HTML alternatives containing company, role, Freight-Link context, acceptance URL, UTC expiry, and an ignore-if-unexpected notice. Company text is HTML-escaped and subject newlines are removed.

Configure only in ignored backend-spring-boot/.env or deployment environment/secrets:

| Variable | Purpose/default |
| --- | --- |
| SMTP_HOST | smtp-relay.brevo.com |
| SMTP_PORT | 465 |
| SMTP_SECURE | true; implicit SSL |
| SMTP_USER | Configured SMTP login; no source default |
| SMTP_PASSWORD | Configured SMTP secret; no source default |
| EMAIL_FROM | Verified sender address; no source default |
| FRONTEND_BASE_URL | Public HTTPS web origin/path; dev defaults to http://localhost:5173 |
| INVITATION_EXPIRY_HOURS | 24, allowed 1–168 |
| INVITATION_COOLDOWN_SECONDS | 60; 0 only for controlled verification |
| INVITATION_MAX_PER_HOUR | 20, allowed 1–100 |
| INVITATION_TEST_RECIPIENT | Optional controlled inbox, used only by verification tooling |

Port 465 uses implicit SSL, authentication, server-identity verification, and no STARTTLS upgrade. Connection/read/write timeouts are five seconds; JavaMail debug is disabled. No credentials or provider error messages are returned or logged. The existing Actuator exposure remains health/info/metrics/prometheus, with no env/config endpoint. The optional SMTP health indicator is disabled so missing mail configuration does not mark unrelated platform functionality unhealthy; actual sends still fail clearly until configured.

FRONTEND_BASE_URL must be HTTPS except explicit localhost/127.0.0.1 development URLs, and cannot contain userinfo, query, or fragment components. Production has no localhost default.

Configuration follows [Spring Mail support](https://docs.spring.io/spring-boot/reference/io/email.html) and [Brevo SMTP relay guidance](https://developers.brevo.com/docs/smtp-integration). No Brevo API or custom SMTP socket client is added to the application.

## API reference

All paths include the existing /api servlet context. Existing bearer JWTs authenticate requests.

Let T = /api/carrier-companies/{companyId}/team.

| Method | Path | Authorization/purpose |
| --- | --- | --- |
| POST | T/invitations | Active primary owner; approved company; email an employee invitation (201) |
| GET | T/invitations | Active primary owner; list private invitations |
| POST | T/invitations/{id}/resend | Active primary owner; approved company; regenerate and email |
| POST | T/invitations/{id}/revoke | Active primary owner; approved company; revoke pending token |
| GET | T/members | Active primary owner; list team details |
| PATCH | T/members/{id}/role | Active primary owner; approved company; change employee role |
| POST | T/members/{id}/deactivate | Active primary owner; approved company |
| POST | T/members/{id}/activate | Active primary owner; approved company |
| DELETE | T/members/{id} | Active primary owner; approved company; soft removal |
| POST | /api/company-invitations/validate | Public; token required; limited metadata |
| POST | /api/company-invitations/accept | Authenticated matching active User; token required |
| GET | /api/company-memberships/me | Authenticated User; their own active memberships |

Collection APIs use page=0 and size=20 defaults, maximum size 100, and descending membership/invitation IDs. Invitation lists include historical statuses; expired pending rows are represented as EXPIRED.

Invitation request:

```json
{"email":"employee@example.com","role":"FLEET_MANAGER"}
```

Role change:

```json
{"role":"DISPATCHER"}
```

Validation/acceptance request (the same shape):

```json
{"token":"<token-from-email>"}
```

Successful validation shape:

```json
{
  "valid": true,
  "companyName": "ABC Logistics",
  "role": "FLEET_MANAGER",
  "email": "employee@example.com",
  "expiresAt": "2026-10-01T12:00:00"
}
```

Invitation expiry timestamps are UTC. Membership responses include id, carrierCompanyId, userId, fullName, email, role, status, joinedAt, invitation/audit actors, and timestamps. Invitation responses contain reviewable invitation metadata but never the raw token, link, or hash.

Errors reuse the global message/code envelope: 401 unauthenticated; 403 wrong company/non-owner/mismatched invited email; 400 unsupported transitions/roles/invalid or expired token; 409 already-active member; 422 DTO validation; 429 cooldown/hourly limit; 503 unavailable/misconfigured SMTP. Invalid tokens reveal no company information. Employee endpoints cannot promote anyone to CARRIER_OWNER.

## Existing and new user flows

Existing user: open the email link, inspect company/role, sign in with the invited email, then explicitly accept. Login returns to the invitation page if a pending invitation is present.

New user: open the link, choose Create account, register through the existing endpoint using the invited email (the existing shipper platform account works for non-driver employees), then explicitly accept. A company role is added through membership; the registration platform role remains independent. Registration passwords are handled only by the existing authentication service.

The acceptance page does not implement a team-management UI. Owners use the documented APIs in Phase 2. Flutter employee screens and operational tenant permissions remain deferred.

## Database

- V58: company_memberships; unique company/user; company and user FKs; invitation and last role/status actor FKs; valid role/status checks; owner must stay ACTIVE; indexes by user, company/role/status, and audit actors.
- V59: idempotent backfill from carrier_companies.owner_id without changing that field.
- V60: company_invitations; unique hash and partial unique pending company/email; normalized email/hash-format/status/role/expiry/decision checks; company and inviter/acceptor/revoker FKs; company-created, email, status-expiry, and actor indexes.

Company FKs cascade deletion for these dependent records; Phase 1 still exposes no company deletion API. User references are restrictive. Normal employee removal preserves membership rows and user accounts.

## Verification

From backend-spring-boot:

```powershell
.\mvnw.cmd test
.\mvnw.cmd '-Dtest=CompanyTeam*Test,CompanyOwnerMigrationTest,SmtpEmailServiceTest' test
# Stop the local JAR process before packaging on Windows.
.\mvnw.cmd package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-carrier-phase2.ps1
```

The PostgreSQL verifier creates a random isolated database, installs PostGIS, runs the Phase 1/2 API/concurrency tests and actual migration/constraint checks with Flyway and Hibernate validate, starts the packaged JAR, and executes HTTP actions through a loopback-only SMTP capture server. Capture keeps tokens in memory and protects its inspection endpoint with an ephemeral secret. It sends no real mail. The test processes and database are cleaned up afterward.

For one controlled live invitation, configure the variables above including INVITATION_TEST_RECIPIENT, then run the verifier with -LiveEmail. It sends only after automated/database/HTTP checks succeed. A marker under local-run-checks prevents another live attempt. The controlled invitation is temporary and becomes invalid when its test database is removed. SMTP acceptance can be verified by the tool; actual inbox delivery requires recipient confirmation.

Frontend verification:

```powershell
# From frontend
pnpm.cmd --filter @workspace/freight-link typecheck
pnpm.cmd --filter @workspace/freight-link build
# Start isolated Chrome with --headless=new --remote-debugging-port=9224, plus a temporary user-data-dir.
node artifacts/freight-link/scripts/verify-invitation-page.mjs
```

Browser checks use synthetic tokens/API responses, independent of real accounts/mail. For local servers, see ../LOCAL_RUN.md. For exact executed results and live-email limitations, see CARRIER_COMPANY_PHASE2_REPORT.md.

## Phase 3 boundary

Company memberships and their status are now available for future scoped authorization. Phase 3 must extend tenant ownership and query filtering across drivers, vehicles, fleet, bookings, and operational data. Phase 2 does not change those modules, assign drivers/vehicles, dispatch shipments, change payments/commissions, or alter AI behavior.

