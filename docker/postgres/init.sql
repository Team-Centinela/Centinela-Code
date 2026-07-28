-- Local PostgreSQL bootstrap installs database-level extensions only.
-- Service-owned schemas and tables are created by each service's Flyway V1
-- migration so local startup follows the same version-0 contract as production.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Tracked by #184: signal that the postgis entrypoint has finished its
-- initial start (after initdb + docker-entrypoint-initdb.d scripts + final
-- pg_ctl restart) so the Compose healthcheck does not pass against the
-- temporary init postmaster that is then stopped and replaced.
DO $$
BEGIN
    EXECUTE format('COPY (SELECT 1) TO %L', '/var/lib/postgresql/.centinela-init-complete');
EXCEPTION WHEN OTHERS THEN
    -- Best-effort marker only; failing here must not abort init.
    NULL;
END
$$;
