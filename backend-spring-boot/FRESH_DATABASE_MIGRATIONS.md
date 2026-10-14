# Fresh database migration compatibility

These repairs target migration startup on a newly created, empty PostgreSQL database. PostGIS must be installed on the PostgreSQL server and enabled in the target database.

- V16 uses a companion `.sql.conf` file with `executeInTransaction=false`. Its SQL remains unchanged, preserving the SQL checksum. `spring.flyway.postgresql.transactional-lock=false` in `application.yml` uses session-level advisory locks so `CREATE INDEX CONCURRENTLY` does not wait on Flyway's own transaction.
- V17 fixes invalid SQL and preserves route sampling order; V18 uses the canonical join columns; V19 replaces an index predicate that requires an immutable expression.
- V41, V42, and V50–V52 conditionally apply operations that depend on legacy schema columns.

The historical SQL changes alter their Flyway checksums. Existing populated installations must back up their database and review migration history and the actual schema before deciding how to reconcile those changes. Do not automatically run Flyway repair or baseline, or disable checksum validation.

Forward migration V55 creates monthly location-history partitions from January 2026 through the end of `max(current year, 2026) + 2`, supporting current and future timestamps in that range. Legacy installations with a nonpartitioned location-history table skip partition DDL.

Forward migration V56 adds the required trip driver/vehicle relationships and document label/file URL columns. It preserves existing values and fills missing values from the linked booking, the document type's application label, and the existing file path. Required values and foreign keys are validated; inconsistent source records cause migration failure instead of receiving fabricated defaults.

Verified on 2026-09-19 against the newly created local `ethioloadai_spring` database on PostgreSQL 18.6 with PostGIS 3.6: all 38 migrations applied successfully through V56. A separate backend startup with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` also passed. Web registration and mobile login/profile requests succeeded against the shared database; the temporary verification account was then removed.
