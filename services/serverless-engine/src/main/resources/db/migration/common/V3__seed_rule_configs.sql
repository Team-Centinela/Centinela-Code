-- Seed `rules_config.rule_configs` with the PIPELINE + AGGREGATOR + FR-1..FR-4
-- rows that the engine reads at startup (ADR-004 §4.4). Replaces the prior
-- compile-time defaults that the placeholder in-memory repository silently
-- fell back to (#270 — configurable threshold DB-backed).
--
-- Defaults match ADR-004 §4.4 + the per-rule constants in
-- domain/service/{Velocity,AtypicalAmount,ImpossibleGeo,HighRiskMerchant}Rule.java.
-- Operators tune via Admin UI; the engine reloads on the @Scheduled
-- refresh tick (60s default) without a restart.
--
-- Idempotent via WHERE NOT EXISTS so the same DDL runs unmodified against
-- H2 (MODE=PostgreSQL test profile) and PostgreSQL. Note: the `config`
-- column is TEXT post-V4 — JSON literal casts ('::json') were dropped
-- because Postgres TEXT doesn't need them and H2 MODE=PostgreSQL rejects
-- them on a TEXT column.

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'PIPELINE',   TRUE,
       '{"scoreThreshold": 70}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'PIPELINE');

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'AGGREGATOR', TRUE,
       '{"flagThreshold": 30}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'AGGREGATOR');

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'FR-1',       TRUE,
       '{"scoreValue": 30, "windowSeconds": 300, "txnCountThreshold": 5}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'FR-1');

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'FR-2',       TRUE,
       '{"scoreValue": 40, "zScoreThreshold": 2.5, "minSampleSize": 30}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'FR-2');

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'FR-3',       TRUE,
       '{"scoreValue": 60, "maxAllowedSpeedKmh": 900}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'FR-3');

INSERT INTO rules_config.rule_configs (id, rule_code, enabled, config, created_at, updated_at)
SELECT ${uuid-func}(), 'FR-4',       TRUE,
       '{"scoreValue": 50}',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM rules_config.rule_configs WHERE rule_code = 'FR-4');