package com.centinela.serverless.application;

import com.centinela.serverless.domain.model.FraudScore;
import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import com.centinela.serverless.application.rules.Fr1VelocityRule;
import com.centinela.serverless.application.rules.Fr2AtypicalAmountRule;
import com.centinela.serverless.application.rules.Fr3ImpossibleGeoRule;
import com.centinela.serverless.application.rules.Fr4HighRiskMerchantRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FraudEvaluationPipelineTest {

    @Test
    void highRiskMerchantShortCircuits() {
        StubEvidence evidence = StubEvidence.builder()
                .highRiskMerchant(true)
                .build();
        FraudEvaluationPipeline pipeline = pipeline(evidence, 60.0d, 100.0d);

        FraudScore score = pipeline.evaluate(sample());

        assertThat(score.flagged()).isTrue();
        assertThat(score.score()).isGreaterThanOrEqualTo(60.0d);
        assertThat(score.triggeredRules()).anyMatch(r -> r.ruleCode().equals("FR-4") && r.isFired());
    }

    @Test
    void noRuleFiresBelowThreshold() {
        StubEvidence evidence = StubEvidence.builder().build();
        FraudEvaluationPipeline pipeline = pipeline(evidence, 60.0d, 100.0d);

        FraudScore score = pipeline.evaluate(sample());

        assertThat(score.flagged()).isFalse();
        assertThat(score.triggeredRules()).isNotEmpty();
        assertThat(score.triggeredRules()).allSatisfy(r ->
                assertThat(r.isFired()).isFalse());
    }

    @Test
    void scoreIsClampedToMax() {
        StubEvidence evidence = StubEvidence.builder()
                .highRiskMerchant(true)
                .build();
        FraudEvaluationPipeline pipeline = pipeline(evidence, 60.0d, 50.0d);

        FraudScore score = pipeline.evaluate(sample());

        assertThat(score.score()).isLessThanOrEqualTo(50.0d);
    }

    private FraudEvaluationPipeline pipeline(StubEvidence evidence, double threshold, double max) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Fr1VelocityRule fr1 = new Fr1VelocityRule(evidence, 600L, 5);
        Fr2AtypicalAmountRule fr2 = new Fr2AtypicalAmountRule(evidence, 2.5d);
        Fr3ImpossibleGeoRule fr3 = new Fr3ImpossibleGeoRule(evidence, 900.0d);
        Fr4HighRiskMerchantRule fr4 = new Fr4HighRiskMerchantRule(evidence, 80.0d);
        return new FraudEvaluationPipeline(
                List.of(fr1, fr2, fr3, fr4), threshold, max, registry);
    }

    private TransactionMessage sample() {
        return new TransactionMessage(
                UUID.randomUUID(),
                "acct-1",
                new BigDecimal("100.00"),
                "USD",
                Instant.parse("2026-07-24T10:00:00Z"),
                40.7128d,
                -74.0060d,
                "PURCHASE",
                "merchant-flagged",
                "Test tx");
    }

    static final class StubEvidence implements RuleEvidenceRepository {
        private final boolean highRiskMerchant;
        private final long recentCount;

        private StubEvidence(Builder b) {
            this.highRiskMerchant = b.highRiskMerchant;
            this.recentCount = b.recentCount;
        }

        static Builder builder() {
            return new Builder();
        }

        @Override
        public long countRecentForAccount(TransactionMessage tx) {
            return recentCount;
        }

        @Override
        public boolean isHighRiskMerchant(String merchantId) {
            return highRiskMerchant;
        }

        @Override
        public double getAverageAmount(String accountId) {
            return 99.0d;
        }

        @Override
        public Optional<TransactionLocation> previousLocationFor(TransactionMessage tx) {
            return Optional.empty();
        }

        static final class Builder {
            private boolean highRiskMerchant = false;
            private long recentCount = 0L;

            Builder highRiskMerchant(boolean v) {
                this.highRiskMerchant = v;
                return this;
            }

            Builder recentCount(long v) {
                this.recentCount = v;
                return this;
            }

            StubEvidence build() {
                return new StubEvidence(this);
            }
        }
    }
}
