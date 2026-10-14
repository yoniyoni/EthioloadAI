# Team local setup (not a shared preview)

This repository snapshot contains source only. Each teammate uses a separate local database and local-only credentials. Do not reuse another developer's E2E database, dump, passwords, or tokens. The React/Flutter URLs below are loopback-only and are not internet/team URLs.

## Requirements

- Java 21 and the included Maven wrapper for Spring Boot.
- PostgreSQL with PostGIS for a local database.
- Node.js and Corepack/pnpm for the React app.
- Flutter stable, Dart, and Chrome for visible Flutter Web.
- PHP/Composer for Laravel; Python 3.10+ for the AI engine.

## Spring Boot development backend

1. Create an isolated local PostgreSQL/PostGIS database for Spring. The `dev` profile defaults to `ethioloadai_spring` on port 5433; configure the actual local URL, username, password, and a local JWT secret in ignored `backend-spring-boot/.env`. Never commit `.env`.
2. From the repository root, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend-spring-boot\start-local.ps1`. Flyway migrations run at startup.
3. The local Spring API defaults to `http://127.0.0.1:8080/api`; health is `http://127.0.0.1:8080/api/actuator/health`.

## React website

From `frontend`, install the lockfile-pinned dependencies with `pnpm install --frozen-lockfile`. In PowerShell, set a process-only proxy target and run Vite:

```powershell
$env:VITE_API_TARGET = 'http://127.0.0.1:8080'
pnpm --filter @workspace/freight-link dev -- --host 127.0.0.1 --port 5173
```

Open `http://localhost:5173`. Remove the process variable after stopping Vite if desired. Do not create or commit `.env.local`.

## Flutter app

From `frontend/artifacts/mobile-app`, run `flutter pub get`. For a visible local Chrome session, run:

```powershell
flutter run -d chrome --web-hostname 127.0.0.1 --web-port 8765 --dart-define=API_BASE_URL=http://127.0.0.1:8080/api
```

If map tiles are required, create the ignored `secrets.local.json` locally with your own restricted `MAPTILER_API_KEY`, then add `--dart-define-from-file=secrets.local.json` to the command. Do not commit or share that key. Open `http://127.0.0.1:8765`.

## Laravel, AI, and legacy Compose

The Laravel app, AI engine, and existing `docker/docker-compose.yml` remain in the project. The Compose stack runs Laravel + AI + PostgreSQL, not Spring Boot; its ports are loopback-bound in this snapshot. Create a local root `.env` from `.env.example`, set fresh local values, and use `docker compose --env-file .env -f docker/docker-compose.yml up --build` only for that stack. Install PHP dependencies with `composer install` in the Laravel project and AI dependencies from `ai_engine/requirements.txt` in a virtual environment. Set a locally generated Laravel `APP_KEY` in the ignored root `.env`. Migrations/seeding must target only a teammate-owned disposable database.

## E2E readiness and account approval

The guarded Spring `e2e` profile accepts only its documented host-loopback or Docker-network E2E datasource and imports secrets from `/run/secrets`. The repository does not yet provide a complete reviewed one-command teammate E2E launcher. Do not point it at the restored database from another machine.

No pre-provisioned test-account credentials are included. Some live integration tests create synthetic accounts or depend on ignored local fixtures; run them only against an isolated disposable database after explicit approval. The prior C36 test-account provisioning approval is still pending. Do not enable the demo bootstrap or create accounts until separately approved. Use synthetic data only; do not invoke payment, SMTP/email, SMS, or OTP providers.

This local setup does not prove E2E readiness. Login/workflow results must be observed against each teammate's approved isolated database. There is no team-reachable preview URL in this snapshot.
