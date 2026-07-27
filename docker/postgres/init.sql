-- Pre-phase only — matches the ADR-002 schema list so Flyway migrations are
-- testable end-to-end against this local instance. See ADR-002 §Schemas and
-- the Phase-0 plan in .context-snapshots/w1-consolidation-PRs-120-130.md §A.1.

CREATE SCHEMA IF NOT EXISTS oltp;
CREATE SCHEMA IF NOT EXISTS outbox;
CREATE SCHEMA IF NOT EXISTS cases;
CREATE SCHEMA IF NOT EXISTS alerts;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS rules_config;
CREATE SCHEMA IF NOT EXISTS triggered_rules;
CREATE SCHEMA IF NOT EXISTS received_messages;

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
