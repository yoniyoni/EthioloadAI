# Carrier companies: Phase 1

Phase 2 now adds company memberships and invitations; see [Phase 2 documentation](CARRIER_COMPANY_PHASE2.md). Phase 1 owner_id remains the compatibility reference, and new company creation also creates an active owner membership in the same transaction. The Phase 1 API and verification lifecycle below are unchanged.

Phase 1 adds organization registration and platform-admin verification to the existing Spring Boot backend. It uses the existing User, JWT principal, ADMIN role, exception handler, MapStruct, JPA, and private DocumentStorage provider. It introduces no environment variables or account types.

## Domain and compatibility

`CarrierCompany` stores the company name, optional legal name, TIN, business registration number, contact/address/city, owner, verification status, submission time, last review message/admin/time, and Hibernate creation/update timestamps. `owner_id` references `users.id`; the authenticated creator becomes the immutable initial owner. A user can own multiple companies, so `GET /me` is paginated. Owner and reviewer references follow the existing scalar user-ID convention, backed by database foreign keys.

Company registration does not change a user's global role or grant fleet permissions. The existing user/vehicle `fleet_owner_id`, driver KYC, bidding, bookings, trips, tracking, and payments remain independent.

`CarrierCompanyDocument` stores a company reference, document type, private storage key, safe download name, media type, size, upload time, and last review status/message/admin/time. Company business documents never enter `driver_documents`.

## Lifecycle and review

| Current state | Allowed action | Result |
| --- | --- | --- |
| DRAFT | Owner updates fields/uploads documents | DRAFT |
| DRAFT | Owner submits complete application | PENDING |
| PENDING | Another user with ADMIN role approves | APPROVED |
| PENDING | Another user with ADMIN role rejects with message | REJECTED |
| PENDING | Another user with ADMIN role requests information with message | MORE_INFORMATION_REQUIRED |
| MORE_INFORMATION_REQUIRED | Owner updates fields/replaces documents | MORE_INFORMATION_REQUIRED |
| MORE_INFORMATION_REQUIRED | Owner resubmits complete application | PENDING |

PENDING, APPROVED, and REJECTED applications cannot be edited or resubmitted. Rejection is final in Phase 1; use request-information for correctable issues. Approved-company amendments and ownership transfer require future workflows.

The admin reviews the company information and downloads its documents, then makes one decision for the entire application. All submitted document statuses and review metadata receive that decision in the same transaction. Phase 1 does not offer independent document approval decisions.

On resubmission, current review metadata clears, documents return to PENDING, and submittedAt updates. Edits before resubmission preserve the owner's company review message; a replaced document returns to DRAFT. The tables retain the latest decision, not an append-only review history.

Every company mutation locks the parent row with PESSIMISTIC_WRITE before accessing documents. This serializes competing edits, uploads, submissions, and decisions. Concurrent reviews have exactly one successful decision.

## Registration and validation

Only companyName is required to save a draft. Use null or omit unknown optional fields. PUT replaces editable fields, including clearing omitted optional values.

Submission requires companyName, TIN, business registration number, phone, email, business address, city, and these three document types:

- BUSINESS_LICENSE
- BUSINESS_REGISTRATION
- TIN_CERTIFICATE

OWNER_IDENTIFICATION and OTHER are optional. These are application requirements, not a claim that the software validates statutory registration formats.

TIN accepts 2–50 letters/digits/slashes/hyphens; registration numbers accept 2–100. Identifiers are uppercased before duplicate checks and persistence. Each is unique across companies, including rejected companies; null values are allowed for drafts. Company names are not unique. Phone accepts an optional leading + and 7–15 digits. Email and field lengths use Jakarta DTO validation.

Example creation body:

```json
{
  "companyName": "Abay Transport",
  "legalName": "Abay Transport PLC",
  "tinNumber": "TIN-12345",
  "businessRegistrationNumber": "REG-12345",
  "phone": "+251911223344",
  "email": "office@example.com",
  "businessAddress": "Bole Road",
  "city": "Addis Ababa"
}
```

Review rejection/information-request body:

```json
{"message": "Please upload a clearer business license."}
```

There are no owner, status, reviewer, timestamp, storage-key, or user-role fields in editable DTOs. Unknown JSON fields follow the existing Jackson behavior and cannot change those server-owned values.

## Endpoints

All paths below include the existing servlet context path `/api`. Send the existing bearer JWT. Responses are DTOs; collections of companies use Spring Page with `content` and pagination metadata.

| Method | Path | Authorization |
| --- | --- | --- |
| POST | /api/carrier-companies | Any authenticated user; creates own DRAFT (201) |
| GET | /api/carrier-companies/me | Authenticated user; only their companies |
| GET | /api/carrier-companies/{id} | Owner or ADMIN |
| PUT | /api/carrier-companies/{id} | Owner; DRAFT or MORE_INFORMATION_REQUIRED |
| POST | /api/carrier-companies/{id}/submit | Owner; eligible complete application |
| POST | /api/carrier-companies/{id}/documents | Owner; editable application; multipart upload (201) |
| GET | /api/carrier-companies/{id}/documents | Owner or ADMIN |
| GET | /api/carrier-companies/{id}/documents/{documentId}/file | Owner or ADMIN; document must belong to that company |
| GET | /api/admin/carrier-companies | ADMIN; optional status filter |
| GET | /api/admin/carrier-companies/{id} | ADMIN |
| POST | /api/admin/carrier-companies/{id}/approve | ADMIN who is not the owner; PENDING |
| POST | /api/admin/carrier-companies/{id}/reject | ADMIN who is not the owner; PENDING; required message |
| POST | /api/admin/carrier-companies/{id}/request-information | ADMIN who is not the owner; PENDING; required message |

The two collection endpoints accept `page` (default 0) and `size` (default 20, max 100). Ordering is creation time descending, then ID descending. Admin `status=PENDING` selects the review queue. Admins use the shared document list/download endpoints to inspect files.

Upload with multipart field `file` and query/form parameter `documentType=BUSINESS_LICENSE`, for example. There is one current document per company/type; uploading the same type replaces it. Types use their enum names in query parameters.

## Security and storage

The existing SecurityConfig authenticates these routes. Controller method security restricts admin routes to ADMIN; service checks enforce admin decisions and ownership again. Owners cannot review their own applications even if they are admins. Another ordinary user's private application, document list, download, update, submission, or upload returns 404 to avoid disclosing its existence.

Responses use the existing error envelope and conventions: 401 for missing authentication, 403 for admin/self-review violations, 404 for missing/inaccessible resources, 400 for invalid lifecycle/completeness/file rules, 422 for invalid DTO fields, 409 for duplicate legal registration, and 503 for storage failures.

Uploads use the existing local provider for dev/test and B2 S3-compatible provider in prod. Existing B2 configuration remains unchanged. Objects use server-generated `carrier-companies/{companyId}/{uuid}.{extension}` keys. Clients cannot provide a storage key or external file URL.

Allowed files are PDF, JPEG, and PNG, with matching magic bytes and a 5 MiB limit. Download names are generated safely rather than trusting submitted filenames. Metadata responses omit storage keys, bucket details, and credentials. Downloads require authorization, use attachment disposition, disable caching, and send nosniff.

Replacement retains the previous object until the database commit succeeds. A rolled-back upload removes the new object. Storage cleanup is best effort: provider deletion failure is logged and requires reconciliation. The production storage bucket must remain private, as required by the existing document infrastructure.

## Migration

`V57__create_carrier_companies.sql` is forward-only and creates:

- `carrier_companies`: owner/reviewer foreign keys to users; unique TIN and registration constraints; canonical-identifier, lifecycle, review-metadata, nonblank-message, nonblank-name, and no-self-review checks.
- `carrier_company_documents`: company foreign key, reviewer foreign key to users, unique storage key, unique company/type pair, allowed type/status/media checks, positive size capped at 5 MiB, and review-metadata/message checks.
- Indexes for owner/creation ordering, status/creation ordering, global creation ordering, and company/document reviewers. The unique company/type index supports document lookup by company.

No old migration, user table, role, fleet relationship, or existing production table was modified. Foreign keys use restrictive deletion; Phase 1 exposes no company deletion API. Hibernate schema validation against PostgreSQL was verified.

## Run and test

From `backend-spring-boot`:

```powershell
.\mvnw.cmd '-Dtest=CarrierCompany*Test' test
.\mvnw.cmd package
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1
```

The test profile uses H2. Integration tests exercise actual JWT/security/controllers/services/repositories and mock only storage; transaction tests verify concurrency and storage cleanup. Migration tests execute V57 SQL itself against H2, including checks omitted by Hibernate-generated schemas.

To verify with PostgreSQL/PostGIS and real local files:

```powershell
# Build the current JAR first; run this script in a child process.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-carrier-postgres.ps1
```

The script reads local .env database credentials without printing them. It requires permission to create a database and install PostGIS, uses a random isolated database, executes the 30 carrier API/transaction tests with Flyway enabled and Hibernate validate, starts the built JAR on port 18087, and runs the real HTTP upload/review/download workflow. It removes its database and successful-smoke uploaded files afterward. Override `-PsqlPath` and `-Port` if necessary. Existing application data is not used or modified.

No new application environment variables are required. The verifier temporarily overrides existing Spring settings only in its child process.

## Phase 2 boundary

The immutable primary-owner reference and unrestricted number of companies per user provide a straightforward starting point for future CompanyMembership records. Later work can backfill an owner membership per company, introduce scoped CARRIER_OWNER/FLEET_MANAGER/DISPATCHER roles, and add invitation delivery.

Phase 1 does not implement membership, invitations, Brevo, employee permissions, driver/vehicle migration, assignment, dispatch, bidding, payments, AI changes, or UI.
