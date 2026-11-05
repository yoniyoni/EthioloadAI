# Local development and deployment modes

| Component | Address |
| --- | --- |
| React web app | http://localhost:5173/ |
| Optional Flutter Web preview | http://127.0.0.1:8765/ |
| Backend health | http://127.0.0.1:8080/api/actuator/health |

## Normal development (Windows)

Normal development uses the existing PostgreSQL 15 Windows service on **127.0.0.1:5433**, database `ethioloadai_spring`, and Spring's `dev` profile. The workspace's Docker database on 15433 is explicitly disposable-test data; the E2E database and production/demo stack are separate modes and are never started or stopped by these scripts.

Configure `backend-spring-boot/.env` as described in [LOCAL_SETUP.md](backend-spring-boot/LOCAL_SETUP.md). Do not put credentials in scripts or source control. From PowerShell at the repository root:

```powershell
.\scripts\dev-up.ps1
# Include the existing Flutter Web build when needed:
.\scripts\dev-up.ps1 -FlutterWeb
```

Stop only processes and the PostgreSQL service that this launcher started:

```powershell
.\scripts\dev-down.ps1
```

The launcher checks Docker Engine, verifies the PostgreSQL service configured for 5433, waits for `pg_isready`, starts the current Spring source (not a potentially stale JAR), waits for Spring health, starts Vite, and checks `/api/actuator/health` through Vite's proxy. PostgreSQL is shared infrastructure and remains running after `dev-down`. The launcher records only owned process identities under `%LOCALAPPDATA%\EthioloadAI\dev-workflow`; it stores no credentials. Existing healthy local services are reused. An occupied but unrecognized port causes a safe failure; the launcher never kills a process based on port number.

Startup is serialized with a machine-wide Windows named mutex. The backend launcher owns its Maven/Java descendants in a Windows Job Object and records the actual Java process identity. `dev-down` signals only that launcher's private shutdown event, terminates its owned process job, and verifies the JVM exits; this is process-level termination, not an application-level graceful drain. If the launcher process exits unexpectedly, Windows closes its Job Object and terminates its owned descendants. Spring startup is blocked while the frozen V78 migration file is present because Flyway could apply it automatically.

The Docker Engine is checked but no Docker container, image, network, volume, or secret is started, stopped, or removed by the normal-development launcher. `start-all.ps1` and `stop-all.ps1` remain compatibility wrappers for the canonical scripts.
The AI engine is not part of this default workflow; start it separately only when working on AI-backed features.

## E2E testing with SMTP capture

E2E is an independently guarded Docker workflow using the approved isolated E2E database/network and synthetic SMTP capture. Follow [TEAM_E2E_SETUP.md](TEAM_E2E_SETUP.md) and [E2E_MAIL_CAPTURE.md](backend-spring-boot/E2E_MAIL_CAPTURE.md); do not use `dev-up.ps1` to launch it. The capture sink does not deliver email externally.

## Production/demo deployment

Production/demo uses its separate Compose file, protected runtime secrets, production profile, B2 storage, and real SMTP provider configuration. See [docker-compose.spring-demo.yml](docker/docker-compose.spring-demo.yml) and the approved deployment procedure. Do not use the normal local `.env` or E2E secrets for that mode.

## Optional Flutter Web preview

```powershell
.\scripts\dev-up.ps1 -FlutterWeb
```

This serves the existing `frontend/artifacts/mobile-app/build/web`; it does not compile Dart or update that build. Rebuild separately when Flutter source changes.

See `backend-spring-boot/FRESH_DATABASE_MIGRATIONS.md` before using the modified migration files against an older populated database.
