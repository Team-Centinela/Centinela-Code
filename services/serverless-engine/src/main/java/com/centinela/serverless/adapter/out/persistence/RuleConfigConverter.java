package com.centinela.serverless.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.Map;

/**
 * JPA {@link AttributeConverter} that translates the
 * {@code rules_config.rule_configs.config} JSON column (declared as
 * {@code JSON} in {@code V1__init_engine_schemas.sql}) to and from
 * {@code Map<String, Object>} for the entity.
 *
 * <p>Bypasses Hibernate 6's built-in {@code @JdbcTypeCode(SqlTypes.JSON)}
 * support because the latter, paired with the engine's custom
 * {@code USE_BIG_DECIMAL_FOR_FLOATS} Jackson configuration, mis-handles
 * the JSON-as-VARCHAR return type from H2 (MODE=PostgreSQL) and surfaces
 * {@code MismatchedInputException: no String-argument constructor to
 * deserialize from String value}. Using this converter keeps the round-trip
 * explicit and identical between H2 and PostgreSQL.</p>
 *
 * <p>An empty / {@code null} config body is stored as the empty map
 * ({@code {}}); the converter never throws on a missing value.</p>
 */
@Converter
public class RuleConfigConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public RuleConfigConverter() {
        this(new ObjectMapper());
    }

    public RuleConfigConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to serialize rules_config.rule_configs.config to JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        // H2 (MODE=PostgreSQL) wraps the value in extra quotes when reading
        // from a column that was previously JSON. Strip one leading +
        // trailing quote pair (and the embedded escaped quotes) before
        // handing to Jackson. PostgreSQL never adds the wrapping, so this
        // branch is a no-op there.
        String normalized = dbData;
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        try {
            return objectMapper.readValue(normalized, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to parse rules_config.rule_configs.config JSON: " + ex.getMessage(), ex);
        }
    }
}