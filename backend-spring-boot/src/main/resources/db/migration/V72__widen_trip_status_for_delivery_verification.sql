-- DELIVERY_PENDING_VERIFICATION exceeds the original 20-character column.
-- PostgreSQL requires dependent views to be recreated even for varchar widening.
-- Preserve their CURRENT definitions (later migrations renamed joined columns),
-- indexes, comments, owners and grants. No CASCADE: unknown dependencies abort
-- the transaction rather than silently removing application objects.
CREATE TEMP TABLE trip_status_views ON COMMIT DROP AS
SELECT c.oid, n.nspname AS schema_name, c.relname AS view_name, c.relkind,
       pg_get_viewdef(c.oid, true) AS definition,
       pg_get_userbyid(c.relowner) AS owner_name,
       obj_description(c.oid, 'pg_class') AS description,
       c.reloptions
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = current_schema()
  AND c.relname IN ('v_trip_details', 'v_trip_stop_details',
                   'mv_trip_performance_daily', 'mv_driver_performance',
                   'v_system_health', 'v_recent_activity')
  AND c.relkind IN ('v', 'm');

CREATE TEMP TABLE trip_status_indexes ON COMMIT DROP AS
SELECT pg_get_indexdef(i.indexrelid) AS definition
FROM pg_index i JOIN trip_status_views v ON v.oid = i.indrelid;

CREATE TEMP TABLE trip_status_grants ON COMMIT DROP AS
SELECT v.schema_name, v.view_name,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(a.grantee)) END AS grantee,
       a.privilege_type, a.is_grantable
FROM trip_status_views v JOIN pg_class c ON c.oid = v.oid,
LATERAL aclexplode(COALESCE(c.relacl, acldefault('r', c.relowner))) a;

DO $$
DECLARE v RECORD;
BEGIN
    FOR v IN SELECT * FROM trip_status_views LOOP
        EXECUTE format('DROP %sVIEW %I.%I',
            CASE WHEN v.relkind = 'm' THEN 'MATERIALIZED ' ELSE '' END,
            v.schema_name, v.view_name);
    END LOOP;

    ALTER TABLE trips ALTER COLUMN trip_status TYPE VARCHAR(40);

    FOR v IN SELECT * FROM trip_status_views LOOP
        EXECUTE format('CREATE %sVIEW %I.%I %s AS %s',
            CASE WHEN v.relkind = 'm' THEN 'MATERIALIZED ' ELSE '' END,
            v.schema_name, v.view_name,
            CASE WHEN v.reloptions IS NULL THEN '' ELSE 'WITH (' || array_to_string(v.reloptions, ',') || ')' END,
            v.definition);
        EXECUTE format('COMMENT ON %sVIEW %I.%I IS %L',
            CASE WHEN v.relkind = 'm' THEN 'MATERIALIZED ' ELSE '' END,
            v.schema_name, v.view_name, v.description);
        EXECUTE format('ALTER %sVIEW %I.%I OWNER TO %I',
            CASE WHEN v.relkind = 'm' THEN 'MATERIALIZED ' ELSE '' END,
            v.schema_name, v.view_name, v.owner_name);
    END LOOP;
    FOR v IN SELECT * FROM trip_status_indexes LOOP
        EXECUTE v.definition;
    END LOOP;
    FOR v IN SELECT * FROM trip_status_grants LOOP
        EXECUTE format('GRANT %s ON %I.%I TO %s%s', v.privilege_type,
            v.schema_name, v.view_name, v.grantee,
            CASE WHEN v.is_grantable THEN ' WITH GRANT OPTION' ELSE '' END);
    END LOOP;
END $$;
