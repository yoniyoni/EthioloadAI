# Run the backend locally on Windows

The backend needs Java 21, PostgreSQL, and PostGIS installed for your PostgreSQL server version. Maven uses the included wrapper and may download dependencies on its first run.

Create the database `ethioloadai_spring` in PostgreSQL. This local setup uses port **5433** (matching the Spring `dev` profile default); use your actual server port if it differs. Install PostGIS if it is unavailable, and enable its extension in this database using pgAdmin's Query Tool with an administrator account:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

Save the following settings in `backend-spring-boot/.env`, preserving any other existing settings and replacing the password locally:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_actual_postgres_password
```

Keep passwords and private keys out of chat and version control. Values may be unquoted, single quoted, or double quoted. Put comments on separate lines. The launcher reads values literally; it does not expand variables, execute commands, or interpret escape sequences.

From the project root, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend-spring-boot\start-local.ps1
```

The launcher starts the current backend source using the Maven wrapper. It passes `.env` values only to the child process and leaves your shell environment unchanged. It selects the `dev` profile unless `.env` or the process environment specifies `SPRING_PROFILES_ACTIVE`. Flyway applies pending database migrations during startup.

If no `JWT_SECRET` is configured and only the `dev` profile is active, the launcher generates a cryptographically random temporary secret for that run. Existing login tokens stop working after restarting with a new secret; sign in again. To preserve tokens across local restarts, save a strong random `JWT_SECRET` in `.env`. Other profiles, including production, require `JWT_SECRET` to be supplied externally and fail closed when it is missing.

After the startup log confirms the server is ready, the default health endpoint is `http://localhost:8080/api/actuator/health`. If `.env` sets `SERVER_PORT`, use that port instead.

Login requests use token buckets in each backend process: five attempts per account identity, refilling at one attempt per minute, and 30 failed attempts per observed source address, refilling at one attempt every five seconds. State uses fixed-size, process-keyed hash tables (about 30 MiB per process) rather than retaining submitted identifiers or growing with attacker-controlled keys. The limits are not shared between replicas and reset on process restart. Production deployments must enforce a shared gateway/WAF limit as well, and the trusted ingress must ensure the servlet remote address represents the actual client; do not trust client-supplied forwarded-IP headers.

To run an existing packaged build, append `-UseJar` to the command. This uses `target/ethioloadai-backend-1.0.0.jar`, which can be older than the current source. It does not build or update that JAR.
