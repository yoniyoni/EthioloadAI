# Phase 1 final verification report

Implemented and verified on September 23, 2026. Scope: carrier-company foundation, private company documents, owner application workflow, and admin verification only.

## Repository inspection and design decisions

- The backend is `backend-spring-boot`, under `com.ethioloadai`, with feature/controller/service/repository/dto/entity packages.
- User is the existing authenticated JWT principal. Global roles are SHIPPER, DRIVER, ADMIN, and FLEET_OWNER.
- Fleet associates drivers and vehicles through existing user IDs. Carrier ownership is a separate scalar user reference with a database FK; it does not rewrite fleet ownership or change roles.
- Existing driver documents are KYC-specific. The new module has its own entities/tables and reuses DocumentStorage, LocalDocumentStorage, and B2DocumentStorage.
- Controllers use method authorization, validated DTOs, ResponseEntity/direct DTO responses, and the global BaseException error handler.
- MapStruct handles response mapping and an explicit allowlist of editable fields. Hibernate timestamps follow User/Vehicle conventions.
- Controller paths exclude the shared /api context prefix.
- V56 was the latest migration. Only a new V57 was added.
- Existing tests use JUnit 5, Mockito, Spring Boot, and H2. New tests add actual JWT-filter/MockMvc coverage and independently execute V57 constraints.
- No AGENTS.md was found in the inspected project/ancestor locations. This workspace has no Git repository metadata, so no commit or Git diff was produced.

Potential conflicts were resolved by retaining global auth/roles and fleet/KYC semantics, allowing multiple companies per owner for future membership, and protecting files through authenticated downloads. Draft fields may be incomplete; submission enforces required information/documents.

## Files Created

Paths below are relative to `backend-spring-boot`.

Production Java under `src/main/java/com/ethioloadai/carrier/company/`:

1. `controller/CarrierCompanyController.java`
2. `controller/AdminCarrierCompanyController.java`
3. `dto/CarrierCompanyRequest.java`
4. `dto/CarrierCompanyReviewRequest.java`
5. `dto/CarrierCompanyResponse.java`
6. `dto/CarrierCompanyDocumentResponse.java`
7. `entity/CarrierCompany.java`
8. `entity/CarrierCompanyDocument.java`
9. `entity/VerificationStatus.java`
10. `entity/CompanyDocumentType.java`
11. `exception/CarrierCompanyException.java`
12. `mapper/CarrierCompanyMapper.java`
13. `repository/CarrierCompanyRepository.java`
14. `repository/CarrierCompanyDocumentRepository.java`
15. `service/CarrierCompanyService.java`
16. `service/CarrierCompanyServiceImpl.java`

Migration, tests, tooling, and documentation:

17. `src/main/resources/db/migration/V57__create_carrier_companies.sql`
18. `src/test/java/com/ethioloadai/carrier/company/CarrierCompanyIntegrationTest.java`
19. `src/test/java/com/ethioloadai/carrier/company/CarrierCompanyTransactionTest.java`
20. `src/test/java/com/ethioloadai/carrier/company/CarrierCompanyMigrationTest.java`
21. `scripts/verify-carrier-postgres.ps1`
22. `CARRIER_COMPANY_PHASE1.md`
23. `CARRIER_COMPANY_PHASE1_REPORT.md`

Generated verification artifacts include `phase1-baseline-tests.log`, `phase1-feature-tests.log`, `phase1-full-tests.log`, `phase1-postgres-tests.log`, `phase1-postgres-verification.log`, `phase1-startup.log`, `phase1-startup-error.log`, Surefire reports, and the executable JAR in target. Logs/build artifacts are not source files; the earlier feature-attempt log retains resolved failures, while the full-suite and PostgreSQL logs contain the final successful runs.

## Files Modified

- Root `README.md`: links the current Spring backend, local run instructions, and carrier documentation; marks the older Laravel overview as historical.
- Root `.gitignore`: ignores private uploaded files and generated Phase 1 logs.

No existing Java/auth/security/driver/fleet/payment module, dependency manifest, or applied migration was changed.

## Database Changes

Added forward-only `V57__create_carrier_companies.sql`.

Tables:

- `carrier_companies`: company details, initial owner, verification status, last review metadata, submission/creation/update times.
- `carrier_company_documents`: company association, type, private storage key, safe download metadata, verification/review metadata, upload time.

Foreign keys:

- carrier_companies.owner_id → users.id
- carrier_companies.reviewed_by → users.id
- carrier_company_documents.carrier_company_id → carrier_companies.id
- carrier_company_documents.reviewed_by → users.id

Unique constraints:

- `uq_carrier_tin`
- `uq_carrier_registration`
- `uq_carrier_document_type` on company/type
- unique document storage key

Indexes:

- `idx_carrier_owner_created` on owner_id, created_at DESC, id DESC
- `idx_carrier_status_created` on verification_status, created_at DESC, id DESC
- `idx_carrier_created` on created_at DESC, id DESC
- `idx_carrier_reviewer`
- `idx_carrier_document_reviewer`
- Primary/unique constraints supply their own indexes; the company/type index supports company document queries.

Checks enforce nonblank company names, canonical uppercase legal identifiers, allowed company/document states and types, no company self-review, required submission/review timestamps, required rejection/information messages, accepted document media types, and positive file size up to 5 MiB.

All migrations through V57 and entity/schema validation succeeded in an isolated PostgreSQL/PostGIS database. That database was deleted. The configured application database was not migrated during verification; V57 applies on its next normal Flyway-enabled startup/deployment.

## API Endpoints

All paths include /api. ADMIN can inspect company/document details; only the actual owner can mutate the owner application.

| Method | Path | Authorization |
| --- | --- | --- |
| POST | /api/carrier-companies | Authenticated creator |
| GET | /api/carrier-companies/me | Authenticated owner; own paginated companies |
| GET | /api/carrier-companies/{id} | Owner or ADMIN |
| PUT | /api/carrier-companies/{id} | Owner; DRAFT or MORE_INFORMATION_REQUIRED |
| POST | /api/carrier-companies/{id}/submit | Owner; complete editable application |
| POST | /api/carrier-companies/{id}/documents | Owner; editable application; multipart |
| GET | /api/carrier-companies/{id}/documents | Owner or ADMIN |
| GET | /api/carrier-companies/{id}/documents/{documentId}/file | Owner or ADMIN; matching company/document |
| GET | /api/admin/carrier-companies | ADMIN; optional status filter; paginated |
| GET | /api/admin/carrier-companies/{id} | ADMIN |
| POST | /api/admin/carrier-companies/{id}/approve | ADMIN other than owner; PENDING |
| POST | /api/admin/carrier-companies/{id}/reject | ADMIN other than owner; PENDING; message required |
| POST | /api/admin/carrier-companies/{id}/request-information | ADMIN other than owner; PENDING; message required |

Swagger tags and operation descriptions are provided on both controllers. See [feature documentation](CARRIER_COMPANY_PHASE1.md) for payloads, requirements, errors, and lifecycle details.

## Security

Existing JWT authentication and method security remain in use. Ownership and review identity derive from the existing authenticated User. Write DTOs contain no ownership, role, verification, review, timestamp, or file-location fields. Service checks enforce owner access and admin decisions, including the prohibition against admin owners reviewing themselves.

Cross-owner resource access returns 404. Admin actions by ordinary users return 403. Missing authentication returns 401. Existing DTO validation remains 422; business-state errors use 400 and duplicate registration uses 409.

Company data and private files have no public endpoints. File IDs are looked up together with company IDs. Storage keys are generated server-side and omitted from responses. Uploads validate size/type/signature. Downloads use no-store, attachment disposition, and nosniff.

Company row locks serialize mutations. Unique database constraints protect concurrent registrations. Decisions update company and documents atomically. File replacement deletes old objects only after successful commit and deletes new objects on rollback.

## Tests

Final executed results:

| Run | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Existing baseline suite | 720 | 720 | 0 | 0 |
| Full suite with new carrier tests | 754 | 754 | 0 | 0 |
| Carrier API/transaction tests repeated against PostgreSQL/PostGIS | 30 | 30 | 0 | 0 |

The 34 new tests consist of 25 JWT/API integration cases, 5 transaction/concurrency cases, and 4 tests of the actual migration SQL.

Coverage includes authenticated creation, unauthenticated denial, server-owned sensitive fields, owner reads/updates, cross-owner access denial for all operations and files, required fields/documents, submission and resubmission, owner-visible review messages, admin approval/rejection/information requests, mandatory reasons, self-review prohibition, frozen states, duplicate TIN/registration, private company/file association, storage validation, rollback cleanup, concurrent admin decisions, and concurrent duplicate registration. Migration tests exercise unique/check/foreign-key constraints.

Commands executed:

```powershell
.\mvnw.cmd test
.\mvnw.cmd '-Dtest=CarrierCompanyIntegrationTest' test
.\mvnw.cmd '-Dtest=CarrierCompany*Test' test
.\mvnw.cmd test package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-carrier-postgres.ps1
```

Intermediate issues were resolved before the final runs: the sandbox initially denied Maven cache writes; the approved rerun succeeded. A managed-entity auto-flush exposed a duplicate-update error, fixed by checking identifiers before mutating the entity. H2 migration-test teardown was corrected. The isolated PostgreSQL bootstrap was updated to install required PostGIS before running existing migrations.

Executable application verification also passed:

- Packaged Spring Boot JAR started on port 18087.
- Flyway migration history reached V57; Hibernate ddl-auto=validate passed.
- Health reported UP.
- Real HTTP workflow: register owner/admin test accounts, create company, upload three files through the existing local storage provider, submit, request information, read the message as owner, resubmit, approve as admin, and download the original document bytes.
- Verification process stopped; temporary PostgreSQL database and uploaded smoke-test files were removed.

## Remaining Phase 1 Issues

No known incomplete Phase 1 functionality or failing tests remain.

Verification limitations: production B2 was reused but not contacted; the real-file smoke test used the local provider. Storage object cleanup remains best effort if the provider cannot delete an object, with a warning for reconciliation. Review fields retain the latest decision rather than a historical review ledger. Rejected/approved applications are intentionally immutable under the documented Phase 1 lifecycle.

Deployment still requires applying V57 to the target database through normal Flyway startup. No credentials or environment variables were added.

## Phase 2 Readiness

Yes, this is a suitable foundation for CompanyMembership, scoped CARRIER_OWNER/FLEET_MANAGER/DISPATCHER permissions, and Brevo employee invitations. Each company has an explicit immutable initial owner; multiple companies can reference one user, so memberships can be introduced and owner memberships backfilled without replacing authentication.

Phase 2 still needs its own authorization/membership/invitation design and migrations. No memberships, new roles, invitations, Brevo, employee management, driver/vehicle refactoring, dispatch, bidding, payment, AI, React, or Flutter changes were implemented.

