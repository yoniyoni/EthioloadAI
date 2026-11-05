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

The guard requires `e2e` to be the only active profile and rejects conflicting profile property sources, datasource URL values, Hikari alternate JDBC/data-source configuration, JNDI or alternate datasource types, separate Flyway URLs, Flyway driver or JNDI overrides, enabled custom Flyway, and SQL script initialization. It requires separate runtime and migration credentials with distinct usernames and passwords; credential values have no configuration defaults. The guard does not log credential values.

The E2E profile does not import `/run/secrets` automatically. Supply the runtime
credentials through `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`, and the separate migration credentials through
`SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD`. Supply `JWT_SECRET` through
the launching process as well. Do not store these values in `.env`, source
control, shell profiles, or logs. For host-loopback startup, use only
`E2E_DATASOURCE_MODE=host-loopback`; the datasource URL must resolve to the
exact approved loopback URL above. Do not set `SPRING_FLYWAY_URL`,
`SPRING_FLYWAY_DRIVER_CLASS_NAME`, or `SPRING_FLYWAY_JNDI_NAME`: Flyway inherits
the guarded application datasource URL while using its own credentials.

Use the documented Maven wrapper from `backend-spring-boot` only after verifying
the approved local database identity and obtaining separate startup/migration
approval. Set `SPRING_PROFILES_ACTIVE=e2e`, the datasource mode, both credential
pairs, and the JWT secret in the launch process; explicitly keep
`APP_DEMO_SEED_ENABLED` and `APP_DEMO_SEED_ROTATE_ADMIN_PASSWORD` false. Then run
`.\mvnw.cmd spring-boot:run`. Obtain secrets privately from an approved local
secret source or prompt; never put secret values in the command text or echo
them. Spring Boot 3.2 uses the primary datasource URL for Flyway when only
Flyway credentials are overridden.

This configuration does not provision database roles or grants. Do not use a
database administrator or superuser as the application runtime identity. Create
and verify the least-privilege runtime and migration roles through a separately
approved database-administration step before attempting Spring startup.

E2E document uploads use local storage rooted at `uploads-e2e`, separate from the existing `dev`/`test` `uploads` root and production B2 storage. Treat E2E files as disposable test data.

E2E disables outbound AI and routing/geocoding integrations. SMTP is fixed to
the internal capture service at `ethioloadai-e2e-mail-capture:12525`, with
authentication and TLS disabled. The `e2e.mail.capture.enabled` property
defaults to `false`; in the default mode, the startup guard requires SMTP
credentials and the invitation sender to remain blank. Explicit capture mode
requires the exact internal sink, blank SMTP credentials, and the fixed sender
`ethioloadai-test@example.test`. The sink accepts only `@example.test`
recipients and never forwards mail. These application controls supplement,
but do not replace, the internal Docker network boundary.

See [E2E_MAIL_CAPTURE.md](./E2E_MAIL_CAPTURE.md) for the isolated container
configuration, network requirements, and cursor-based message retrieval.
Enabling capture requires a separately approved E2E launch. Do not configure
Brevo or another external SMTP provider in the `e2e` profile.

The E2E CORS allowlist permits only the configured React and Flutter local
origins, including `http://127.0.0.1:5174` for Flutter's loopback web server.
Do not replace the exact origin list with a wildcard or origin pattern.

This guard applies when `e2e` is active. Demo-account bootstrap is available only under the `e2e` profile and is never eligible for production. Never use the development datasource for E2E work.

## Demo-account bootstrap

The E2E-only bootstrap remains disabled unless `APP_DEMO_SEED_ENABLED=true` is explicitly set and a synthetic administrator password is supplied through:

- `E2E_DEMO_ADMIN_PASSWORD`

The bootstrap creates only the synthetic administrator by default. Optional `E2E_DEMO_SHIPPER_PASSWORD` and `E2E_DEMO_DRIVER_PASSWORD` values enable only the exact shipper and driver identities listed below. It runs only with `e2e` as the sole active profile and only against one of the two exact approved E2E datasource URLs listed above. New accounts are created only when both their email and phone are unused. Any existing email, phone, or role conflict aborts rather than overwriting the account. For an exact matching identity, only the password hash is changed when necessary; verification and active state are preserved. A new driver remains unverified and inactive pending normal KYC review. This mechanism does not create vehicles, documents, or workflow state. Create fleet-owner test accounts through the application interface.

For the Docker-network E2E environment, provisioning requires a newly started
backend process configured with:

```text
SPRING_PROFILES_ACTIVE=e2e
E2E_DATASOURCE_MODE=docker-network
E2E_DATASOURCE_URL=jdbc:postgresql://ethioloadai-e2e-db:5432/ethioloadai_e2e
APP_DEMO_SEED_ENABLED=true
```

`E2E_DEMO_ADMIN_PASSWORD` must be supplied from an approved protected source;
`E2E_DEMO_SHIPPER_PASSWORD` and `E2E_DEMO_DRIVER_PASSWORD` are optional and
create only their documented identities. Keep the admin password-rotation
flag false. Never put password values in command text, source control, or logs.
Starting with the enable flag performs database writes after normal E2E
datasource validation and startup migrations; it requires separate explicit
approval. The currently running backend remains configured with seeding
disabled and must not be changed as part of this source-only stage.

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
