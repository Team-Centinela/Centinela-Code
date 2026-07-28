-- Local PostgreSQL bootstrap installs database-level extensions only.
-- Service-owned schemas and tables are created by each service's Flyway V1
-- migration so local startup follows the same version-0 contract as production.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
