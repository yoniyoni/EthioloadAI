# Run the backend locally on Windows

The backend needs Java 21, PostgreSQL, and PostGIS installed for your PostgreSQL server version. Maven uses the included wrapper and may download dependencies on its first run.

The canonical normal-development database is `ethioloadai_spring` on **127.0.0.1:5433**, matching the Spring `dev` profile. On this workstation it is the existing PostgreSQL 15 Windows service (`postgresql-x64-15`). PostgreSQL 17 on 5432 and the Docker database on 15433 are not this development database; the latter is tagged disposable-test. The legacy Laravel Compose file's 5432 mapping is a separate mode and must not be used for Spring development.

Create the database `ethioloadai_spring` in the PostgreSQL 15 instance if it does not already exist. Install PostGIS if unavailable, and enable its extension in this database using pgAdmin's Query Tool with an administrator account:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Save the following settings in `backend-spring-boot/.env`, preserving other existing settings and entering the database password locally:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_actual_postgres_password
```

Keep passwords and private keys out of chat and version control. Values may be unquoted, single quoted, or double quoted. Put comments on separate lines. The launcher reads values literally; it does not expand variables, execute commands, or interpret escape sequences.

For the complete owned-service workflow, use these commands from the repository root:

```powershell
.\scripts\dev-up.ps1
.\scripts\dev-down.ps1
```

The workflow requires Docker Engine to be available for environment identity checks, but does not manage Docker resources. It starts the existing PostgreSQL 15 Windows service only if needed, refreshes and verifies the service PID, executable, listener ownership, and `pg_isready` result, then starts current Spring source using the Maven wrapper (not a stale JAR) and React/Vite. Flutter Web preview is optional and serves the existing build. The launcher records the actual Maven Java process identity and a private Windows shutdown event outside the repository. The backend launcher owns its Maven/Java process tree with a Windows Job Object, so its children are terminated if the owner exits unexpectedly. Shutdown signals only that recorded backend, terminates its owned process job, and verifies the JVM exits; this is process-level termination, not an application-level graceful drain. It then stops only launcher-owned web processes. PostgreSQL is shared infrastructure and is deliberately left running after shutdown and startup failures. Existing unrelated services and Docker resources are left untouched.

The launcher refuses to start Spring while the frozen V78 migration file is present because Flyway could apply it automatically. Do not remove this guard or start Spring until V78 migration status and use have separate authorization.

The backend launcher passes `.env` values only to its child process and leaves the caller's shell environment unchanged. Normal development requires `SPRING_PROFILES_ACTIVE=dev`; Flyway applies pending migrations at backend startup.

If no `JWT_SECRET` is configured and only the `dev` profile is active, the launcher generates a cryptographically random temporary secret for that run. Existing login tokens stop working after restarting with a new secret; sign in again. To preserve tokens across local restarts, save a strong random `JWT_SECRET` in `.env`. Other profiles, including production, require `JWT_SECRET` to be supplied externally and fail closed when it is missing.

After the startup log confirms the server is ready, the default health endpoint is `http://localhost:8080/api/actuator/health`. If `.env` sets `SERVER_PORT`, use that port instead.

Login requests use token buckets in each backend process: five attempts per account identity, refilling at one attempt per minute, and 30 failed attempts per observed source address, refilling at one attempt every five seconds. State uses fixed-size, process-keyed hash tables (about 30 MiB per process) rather than retaining submitted identifiers or growing with attacker-controlled keys. The limits are not shared between replicas and reset on process restart. Production deployments must enforce a shared gateway/WAF limit as well, and the trusted ingress must ensure the servlet remote address represents the actual client; do not trust client-supplied forwarded-IP headers.

`start-local.ps1 -UseJar` remains available for a deliberate manual run of `target/ethioloadai-backend-1.0.0.jar`, but the canonical workflow does not use it because the artifact may be stale.
