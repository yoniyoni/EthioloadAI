# Run locally on this Windows computer

| Component | Address |
| --- | --- |
| Web app | http://localhost:5174/ (or http://localhost:5173/) |
| Mobile app browser preview | http://127.0.0.1:8765/ |
| Backend health | http://127.0.0.1:8080/api/actuator/health |
| AI service documentation | http://127.0.0.1:8000/docs |

Both interfaces use the Spring Boot backend and the same PostgreSQL database, `ethioloadai_spring`, on port 5432. PostGIS is installed and enabled. Connection credentials are in `backend-spring-boot/.env`.

Carrier Company Phase 2 uses migrations through V60. Its backend APIs and web invitation acceptance page are available; sending invitations also requires SMTP settings in the backend .env. See [Phase 2 configuration and verification](backend-spring-boot/CARRIER_COMPANY_PHASE2.md). Full team-management screens and Flutter employee screens are not part of Phase 2.

The database starts without demo accounts. Register a new account in either interface; it can be used in both.

## Quick Start (Start All Services)

To start all services with a single command, run in PowerShell from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-all.ps1
```

To stop all running services:

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-all.ps1
```

## Restart Individually

If the services are already running, open the links above. Otherwise, open separate PowerShell terminals at the project root and run each command below. Keep those terminals open while using the apps.

Backend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend-spring-boot\start-local.ps1
```

Web app:

```powershell
Set-Location .\frontend
pnpm.cmd --filter @workspace/freight-link dev
```

Mobile browser preview:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\frontend\artifacts\mobile-app\start-preview.ps1
```

AI service (uses the Python interpreter already bundled with PostgreSQL's pgAdmin on this computer):

```powershell
& 'C:\Program Files\PostgreSQL\18\pgAdmin 4\python\python.exe' .\ai_engine\run_local.py
```

The AI packages are installed only in `ai_engine/.runtime`. To restore them if necessary:

```powershell
& 'C:\Program Files\PostgreSQL\18\pgAdmin 4\python\python.exe' -m pip install --target .\ai_engine\.runtime -r .\ai_engine\requirements-runtime.txt
```

## Mobile preview and validation

The mobile preview serves the Flutter web build supplied in `frontend/artifacts/mobile-app/build/web`. It is not a new Flutter compilation and does not update automatically when Dart source changes. Flutter is not currently installed; install it and rebuild the web target to include later Dart changes. No native phone or emulator build was performed.

Verified: both interfaces render, database migrations through V56 complete, Hibernate schema validation passes, web registration and mobile login/profile calls work, mobile cross-origin requests are allowed, and authenticated price prediction works through the web proxy, backend, and AI service. Temporary verification accounts were removed.

See `backend-spring-boot/FRESH_DATABASE_MIGRATIONS.md` before using the modified migration files against an older populated database.
