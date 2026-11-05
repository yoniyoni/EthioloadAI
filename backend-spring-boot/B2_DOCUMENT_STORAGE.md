# Backblaze B2 document storage

The `prod` profile writes private driver KYC, vehicle, carrier-company, and
proof-of-delivery documents to B2 through its S3-compatible API. The AWS SDK
uses HTTPS, Signature Version 4, and path-style bucket addressing. Downloads
remain backend-controlled and pass through the existing service authorization
checks; the application does not issue public or signed bucket URLs.

Development and test profiles continue to use `uploads/`. The `e2e` profile
continues to use `uploads-e2e/` and does not require B2 credentials. Production
reads an object from B2 first; only a confirmed missing-object response falls
back to the legacy local directory. Provider errors do not fall back, so an
outage or permission problem is not disguised as a missing file. New
production uploads go only to B2. The legacy directory defaults to `uploads/`
relative to the backend working directory and can be set with
`B2_LEGACY_LOCAL_PATH`.

For S3 reads, only a confirmed `NoSuchKey` response from `GetObject` is
normalized as a missing object and eligible for legacy fallback. `HeadObject`
404 responses are ambiguous and propagate as provider errors; they are not
treated as proof that an object is absent.

No schema migration is required: existing rows already store internal file
paths/object keys in `driver_documents.file_path`,
`vehicle_documents.file_path`, `carrier_company_documents.storage_key`, and
`proof_of_delivery.storage_key`. Existing files are not moved or deleted by
this integration. Before a production cutover, point `B2_LEGACY_LOCAL_PATH`
at the directory containing the existing files and verify representative
legacy records can still be downloaded by their authorized owners.

## Required production configuration

Set these values through the protected deployment secret/configuration
mechanism, not in source control, frontend configuration, URLs, or command-line
arguments:

| Environment variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `B2_BUCKET_NAME` | Name of the private document bucket |
| `B2_REGION` | Exact B2 region identifier, for example `us-west-004` |
| `B2_ENDPOINT` | `https://s3.<B2_REGION>.backblazeb2.com` |
| `B2_KEY_ID` | ID of the bucket-scoped application key |
| `B2_APPLICATION_KEY` | Secret for that bucket-scoped application key |
| `B2_LEGACY_LOCAL_PATH` | Optional path to existing local uploads; defaults to `uploads` |

The approved Backblaze bucket has been provisioned as
`freight-link-documents` in region `us-east-005`. A dedicated runtime
application key restricted to that bucket was provisioned with exactly
`readFiles`, `writeFiles`, and `deleteFiles`; its key identifier and secret are
intentionally not included here. Do not use the account master key or grant
bucket listing, bucket administration, or account-level access. `readFiles`
supports private downloads; `writeFiles` supports uploads; `deleteFiles`
supports replacement/cleanup. Do not configure CORS for direct browser access.

Production startup fails when required B2 configuration is missing or when
the endpoint is not the HTTPS regional endpoint matching `B2_REGION`.
The protected runtime credentials were used for a focused real-B2 driver
document transaction test against isolated in-memory H2. Its four scenarios
passed: committed replacement, forced rollback, notification failure after
upload, and owner download versus unauthorized access. This verifies the
driver service path only; equivalent real-provider transaction tests for
vehicle, carrier-company, and proof-of-delivery documents have not yet been
executed. Cleanup-ledger/recovery behavior is also not implemented or verified.

Bucket and runtime-key provisioning are verified; production deployment
configuration is not. The deployed environment-variable values, the
production profile startup, and the actual `B2_LEGACY_LOCAL_PATH` and its
legacy files have not been independently verified. Do not treat this document
as evidence that a production deployment has been configured or cut over.

The B2 adapter currently buffers downloads in memory; uploads are limited by
the existing per-flow file-size validation (5 MiB for KYC, vehicle, and
company documents; 10 MiB for proof of delivery).
