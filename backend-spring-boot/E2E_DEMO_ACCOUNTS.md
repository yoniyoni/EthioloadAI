# Disposable E2E database and demo accounts

## E2E startup safeguards

The dedicated `e2e` profile requires `E2E_DATASOURCE_MODE` and accepts only
these exact mode/URL pairs:

```text
host-loopback  jdbc:postgresql://127.0.0.1:15433/ethioloadai_e2e
docker-network jdbc:postgresql://ethioloadai-e2e-db:5432/ethioloadai_e2e
```

For `host-loopback`, set `E2E_DATASOURCE_MODE=host-loopback`. For
`docker-network`, set `E2E_DATASOURCE_MODE=docker-network` and
`E2E_DATASOURCE_URL` to the exact Docker-network URL above. The guard rejects
missing or unknown modes, mode/URL mismatches, URL parameters, and conflicting
property sources or aliases; it does not permit arbitrary datasource hosts.

`E2eDatasourceGuardInitializer` checks the effective Spring environment after configuration and profile resolution, but before the application context is refreshed. Spring creates the datasource and initializes Flyway during context refresh, so an invalid E2E configuration fails before those database initialization paths run.

The guard requires `e2e` to be the only active profile and rejects conflicting profile property sources, datasource URL values, Hikari alternate JDBC/data-source configuration, JNDI or alternate datasource types, separate Flyway URLs or credentials, enabled custom Flyway, and SQL script initialization. Database username and password must be supplied in the process environment. The guard does not log credential values.

E2E document uploads use local storage rooted at `uploads-e2e`, separate from the existing `dev`/`test` `uploads` root and production B2 storage. Treat E2E files as disposable test data.

E2E disables outbound AI and routing/geocoding integrations. SMTP username,
password, and invitation sender are explicitly blank in the E2E profile, and
the startup guard rejects any effective nonblank override. Invitation email
delivery also refuses to invoke the mail sender when required SMTP settings
are blank. These application-level controls do not constitute process-wide
network egress isolation.

The E2E CORS allowlist permits only the configured React and Flutter local
origins, including `http://127.0.0.1:5174` for Flutter's loopback web server.
Do not replace the exact origin list with a wildcard or origin pattern.

This guard applies when `e2e` is active. Demo-account bootstrap is available only under the `e2e` profile and is never eligible for production. Never use the development datasource for E2E work.

## Demo-account bootstrap

The E2E-only bootstrap remains disabled unless `APP_DEMO_SEED_ENABLED=true` is explicitly set and a synthetic administrator password is supplied through:

- `E2E_DEMO_ADMIN_PASSWORD`

The bootstrap creates only the synthetic administrator by default. Optional `E2E_DEMO_SHIPPER_PASSWORD` and `E2E_DEMO_DRIVER_PASSWORD` values enable only the exact shipper and driver identities listed below. It runs only under the `e2e` profile and only against the exact host-loopback E2E datasource. New accounts are created only when both their email and phone are unused. Any existing email, phone, or role conflict aborts rather than overwriting the account. For an exact matching identity, only the password hash is changed when necessary; verification and active state are preserved. A new driver remains unverified and inactive pending normal KYC review. This mechanism does not create vehicles, documents, or workflow state. Create fleet-owner test accounts through the application interface.

Password rotation for the existing bootstrap administrator requires the separate, normally disabled `APP_DEMO_SEED_ROTATE_ADMIN_PASSWORD=true` flag in addition to `APP_DEMO_SEED_ENABLED=true`. It is restricted to the exact existing administrator identity and approved host-loopback E2E datasource, and updates only its password hash. Use it only for an explicitly authorized local E2E recovery; never enable it in production.

## Authorization gates before any E2E execution

No database, Spring service, account, migration, or workflow is started by these instructions. Each action below requires separate explicit approval:

1. **Code implementation:** review the guard, profile, tests, and this documentation.
2. **Code-only verification:** run only approved plain unit tests that do not start Spring Boot, create a datasource, access a database, run Flyway, start Testcontainers, or call external services.
3. **Disposable database creation:** recheck Docker context and port 15433, then create only the uniquely named E2E container, network, and dedicated volume. Bind only `127.0.0.1:15433:5432`, use no restart policy, and apply E2E-specific labels. Use fresh disposable credentials that are never reused.
4. **Spring startup and migrations:** start with only the `e2e` profile and the exact datasource URL. Verify the disposable database identity and application health. Do not connect to port 5433.
5. **Synthetic workflows:** use synthetic accounts/files and normal registration and authorized KYC/admin review. Do not send real payments, emails, or OTPs or use real identity documents.
6. **Teardown:** removing any container, volume, network, or seeded data requires separate explicit approval.

Do not persist secrets in `.env` files, source control, shell profiles, or logs. Use process-scoped values, restore prior process environment values after a launch, and never print passwords or tokens. Docker resource creation, Spring startup, migrations, account creation, E2E workflows, and teardown are not authorized by this documentation alone.

## Synthetic account identities

| Role | Email | Initial state |
|---|---|---|
| ADMIN | `admin@freightlink.et` | Verified and active |
| SHIPPER | `tigist@shipper.et` | Verified and active |
| DRIVER | `bekele@driver.et` | Unverified and inactive pending KYC |
