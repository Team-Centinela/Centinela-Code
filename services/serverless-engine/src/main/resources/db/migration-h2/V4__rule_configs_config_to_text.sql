-- #270 — H2 mirror of `pg/V4__rule_configs_config_to_text.sql`. Same rationale:
-- H2 MODE=PostgreSQL stores JSON as VARCHAR but the JDBC driver reports the
-- column type as `Types#OTHER` to Hibernate's schema validator, which then
-- rejects the entity field declared as String (VARCHAR). Widening to TEXT
-- gives both PG and H2 the same effective type and lets
-- `RuleConfigConverter` handle JSON serialization explicitly.
--
-- H2 syntax (no USING clause):
ALTER TABLE rules_config.rule_configs ALTER COLUMN config TEXT NOT NULL;