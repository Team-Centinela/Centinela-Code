-- outbox schema only; `outbox.outbox_events` is owned by the ingestion service
-- per ADR-003 §3.2 (the Ingestion API writes to `oltp` + `outbox.outbox_events`
-- in the same ACID transaction per ADR-002 line 97). core-backend only
-- consumes; its own V3 creates `outbox.processed_events` for inbound dedup.
-- Keeping the schema creation here so V3 can rely on it without ordering
-- dependencies between migrations.
CREATE SCHEMA IF NOT EXISTS outbox;
