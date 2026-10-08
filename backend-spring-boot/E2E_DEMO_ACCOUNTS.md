# DEV/E2E demo accounts

The demo-account bootstrap is disabled unless all of the following are true:

- Spring's `dev` profile is active.
- `APP_DEMO_SEED_ENABLED=true` is explicitly supplied.
- The effective datasource URL is exactly `jdbc:postgresql://127.0.0.1:5433/ethioloadai_e2e`.
- All three passwords are supplied through `E2E_DEMO_ADMIN_PASSWORD`, `E2E_DEMO_SHIPPER_PASSWORD`, and `E2E_DEMO_DRIVER_PASSWORD`.

The bootstrap is not part of a migration and is not loaded by the production profile. It uses the application's configured `PasswordEncoder`. It creates an account only when both its email and phone are unused. A conflicting email/phone identity causes startup to fail rather than changing an existing user. Matching accounts are left untouched, including their password and KYC state.

In PowerShell, start the backend from `backend-spring-boot` with the isolated E2E datasource and seed flag set. Supply the demo passwords in the process environment; do not put them in an `.env` file or source control:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/ethioloadai_e2e"
$env:APP_DEMO_SEED_ENABLED = "true"
$env:E2E_DEMO_ADMIN_PASSWORD = "<the agreed demo password>"
$env:E2E_DEMO_SHIPPER_PASSWORD = "<the agreed demo password>"
$env:E2E_DEMO_DRIVER_PASSWORD = "<the agreed demo password>"
mvn spring-boot:run
```

Demo identities:

| Role | Email | Seeded KYC/active state |
|---|---|---|
| ADMIN | `admin@freightlink.et` | Verified and active |
| SHIPPER | `tigist@shipper.et` | Verified and active |
| DRIVER | `bekele@driver.et` | Pending KYC and inactive, matching public driver registration |

The DRIVER can authenticate to proceed through the normal KYC flow, but cannot bid until the existing KYC/document approval workflow activates the account. The bootstrap does not manufacture KYC approval or document records.
