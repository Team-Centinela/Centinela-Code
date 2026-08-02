-- #270 — change `rules_config.rule_configs.config` from JSON to TEXT.
--
-- Rationale: Hibernate 6's `@JdbcTypeCode(SqlTypes.JSON)` paired with the
-- engine's custom Jackson `USE_BIG_DECIMAL_FOR_FLOATS` configuration mis-
-- handles the JSON-as-VARCHAR return type from H2 (MODE=PostgreSQL) and
-- surfaces `MismatchedInputException: no String-argument constructor to
-- deserialize from String value`. The engine reads + parses the config
-- JSON via Jackson in `RuleConfigConverter`, so the column type does not
-- need JSON(B)-specific operators — TEXT is sufficient.
--
-- PostgreSQL syntax:
ALTER TABLE rules_config.rule_configs
    ALTER COLUMN config TYPE TEXT USING config::TEXT;