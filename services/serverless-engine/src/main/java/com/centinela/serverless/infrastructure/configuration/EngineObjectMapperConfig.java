package com.centinela.serverless.infrastructure.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Engine-specific Jackson customizations. The default Spring Boot
 * {@link ObjectMapper} reads JSON numbers as {@code Double}, which loses
 * precision for financial fields ({@code amount}, {@code z_score},
 * {@code historical_avg_usd}, etc.) when the value carries more than ~15
 * significant decimal digits. ADR-004 §4.2 + #246 mandate that the engine's
 * rawEvidence payload preserve BigDecimal precision end-to-end so the
 * deterministic explainer renders the value as it was computed, not as a
 * double-rounded approximation.
 *
 * <p>The customizer sets {@code USE_BIG_DECIMAL_FOR_FLOATS} globally on the
 * Spring-managed {@link ObjectMapper} bean. There is only one
 * {@code ObjectMapper} in the engine (ScoreTransactionService and
 * TransactionsRawConsumer both inject it) so a single global setting is
 * sufficient — the tradeoff is that any code path expecting doubles from
 * JSON deserialization will now see BigDecimal. The only such path in the
 * engine is {@link com.centinela.serverless.domain.event.TransactionReceivedEvent#amount()}
 * which is already BigDecimal, so no breakage.</p>
 *
 * <p>The companion test
 * {@code EngineObjectMapperBigDecimalRoundTripTest} asserts that
 * serializing a BigDecimal and reading it back returns the same
 * BigDecimal, not a Double approximation.</p>
 */
@Configuration
public class EngineObjectMapperConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer engineBigDecimalFloatsCustomizer() {
        return builder -> builder
                .featuresToEnable(
                        DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                        SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
