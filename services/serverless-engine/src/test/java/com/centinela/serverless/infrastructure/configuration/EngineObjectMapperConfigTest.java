package com.centinela.serverless.infrastructure.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test for the engine JSONB ObjectMapper after #246.
 *
 * <p>Asserts the contract pinned by ADR-004 §4.2 + #246: a financial value
 * (BigDecimal with arbitrary scale/precision) survives a serialize +
 * deserialize cycle without double-rounding through {@code Double}. The
 * regression we are guarding against is the historical Spring Boot default
 * where JSON numbers deserialize as {@code Double} and a value like
 * {@code 0.123456789012345} becomes {@code 0.123456789012345} cast to
 * double, losing the trailing digits. With
 * {@code USE_BIG_DECIMAL_FOR_FLOATS=true} + {@code WRITE_BIGDECIMAL_AS_PLAIN=true}
 * the value round-trips exactly.</p>
 */
class EngineObjectMapperConfigTest {

    private final ObjectMapper mapper = new Jackson2ObjectMapperBuilder()
            .build();

    @Test
    void engineCustomizerAppliedKeepsBigDecimalFloats() {
        // The Spring Boot autoconfiguration applies Jackson2ObjectMapperBuilderCustomizer
        // beans to the application's ObjectMapper. We simulate that by building
        // a builder and applying our customizer the same way Spring would.
        ObjectMapper engineMapper = new Jackson2ObjectMapperBuilder()
                .customizers(new EngineObjectMapperConfig().engineBigDecimalFloatsCustomizer())
                .build();

        Map<String, Object> payload = Map.of(
                "z_score", new BigDecimal("5.01234567890123456789"),
                "amount_usd", new BigDecimal("100.00"),
                "historical_avg_usd", new BigDecimal("99.999999999")
        );

        String json = engineMapper.writeValueAsString(payload);

        // Sanity: BigDecimal serializes in plain (no scientific notation) so the
        // outbox JSONB and the broker message body keep the same shape.
        assertThat(json)
                .contains("5.01234567890123456789")
                .contains("99.999999999")
                .doesNotContain("E");

        JsonNode roundTripped = engineMapper.readTree(json);
        assertThat(roundTripped.get("z_score").decimalValue())
                .isEqualByComparingTo(new BigDecimal("5.01234567890123456789"));
        assertThat(roundTripped.get("amount_usd").decimalValue())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(roundTripped.get("historical_avg_usd").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99.999999999"));
    }

    @Test
    void bareMapperWithoutCustomizerDoublesByDefault() {
        // Demonstrates the bug the customizer fixes: a vanilla Spring Boot
        // mapper drops BigDecimal precision on the round-trip because it
        // deserializes JSON numbers as Double.
        Map<String, Object> payload = Map.of("amount", new BigDecimal("100.123456789"));

        String json = mapper.writeValueAsString(payload);
        // BigDecimal is serialized in plain form by default but the
        // double-roundtrip path triggers on READ, not WRITE.
        JsonNode roundTripped = mapper.readTree(json);

        // With the default mapper the value is read as Double, not BigDecimal.
        // The contract enforced by #246 + ADR-004 §4.2 is that the engine
        // mapper behaves differently — see engineCustomizerAppliedKeepsBigDecimalFloats.
        assertThat(roundTripped.get("amount").isDouble()).isTrue();
    }
}
