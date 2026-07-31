package com.centinela.serverless.infrastructure.configuration;

import com.centinela.serverless.adapter.out.persistence.InMemoryFlaggedMerchantRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryRuleConfigRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryTransactionStatisticsRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryTransactionStatsRepository;
import com.centinela.serverless.domain.port.FlaggedMerchantRepository;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStatisticsRepository;
import com.centinela.serverless.domain.port.TransactionStatsRepository;
import com.centinela.serverless.domain.service.AggregatorStage;
import com.centinela.serverless.domain.service.AtypicalAmountRule;
import com.centinela.serverless.domain.service.FraudPipeline;
import com.centinela.serverless.domain.service.HighRiskMerchantRule;
import com.centinela.serverless.domain.service.ImpossibleGeoRule;
import com.centinela.serverless.domain.service.PipelineStage;
import com.centinela.serverless.domain.service.VelocityRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring wiring for the {@code domain/service/} layer of the Serverless Engine.
 *
 * <p>The domain classes ({@code VelocityRule}, {@code AtypicalAmountRule},
 * {@code ImpossibleGeoRule}, {@code HighRiskMerchantRule},
 * {@code AggregatorStage}, {@code FraudPipeline}) are framework-agnostic
 * by ADR-001 §Hexagonal: no {@code @Component} annotations. Wiring them
 * centrally keeps the domain layer pure Java and makes the dependency
 * graph explicit.</p>
 *
 * <p>Threshold loading per ADR-004 §4.4 + SrLampi1001 review on PR #268
 * (finding 3): {@code scoreThreshold} (PIPELINE config) is loaded ONCE
 * here and passed to both the {@code FraudPipeline} (short-circuit) and
 * the {@code AggregatorStage} (BLOCK recommendation) so the two components
 * see the same canonical value. {@code flagThreshold} (AGGREGATOR config)
 * is loaded separately and passed to {@code AggregatorStage} only.</p>
 */
@Configuration
public class RulePipelineWiring {

    @Bean
    public RuleConfigRepository ruleConfigRepository() {
        return new InMemoryRuleConfigRepository();
    }

    @Bean
    public TransactionStatisticsRepository transactionStatisticsRepository() {
        return new InMemoryTransactionStatisticsRepository();
    }

    @Bean
    public TransactionStatsRepository transactionStatsRepository() {
        return new InMemoryTransactionStatsRepository();
    }

    @Bean
    public FlaggedMerchantRepository flaggedMerchantRepository() {
        return new InMemoryFlaggedMerchantRepository();
    }

    @Bean
    public VelocityRule velocityRule() {
        return new VelocityRule(transactionStatisticsRepository(), ruleConfigRepository());
    }

    @Bean
    public AtypicalAmountRule atypicalAmountRule() {
        return new AtypicalAmountRule(transactionStatsRepository(), ruleConfigRepository());
    }

    @Bean
    public ImpossibleGeoRule impossibleGeoRule() {
        return new ImpossibleGeoRule(ruleConfigRepository());
    }

    @Bean
    public HighRiskMerchantRule highRiskMerchantRule() {
        return new HighRiskMerchantRule(flaggedMerchantRepository(), ruleConfigRepository());
    }

    /**
     * Single source of truth for {@code scoreThreshold} (ADR-004 §4.4
     * canonical threshold). Loaded once from the PIPELINE {@code rules_config}
     * row and passed to both {@link FraudPipeline} and {@link AggregatorStage}.
     */
    @Bean
    public int scoreThreshold(RuleConfigRepository repo) {
        var opt = repo.findByRuleCode(FraudPipeline.PIPELINE_CONFIG_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return FraudPipeline.DEFAULT_SCORE_THRESHOLD;
        }
        return opt.get().getInt("scoreThreshold", FraudPipeline.DEFAULT_SCORE_THRESHOLD);
    }

    /**
     * {@code flagThreshold} loaded once from the AGGREGATOR {@code rules_config}
     * row and passed only to {@link AggregatorStage}. Strict validation
     * {@code flagThreshold < scoreThreshold} is enforced in
     * {@link AggregatorStage}'s constructor.
     */
    @Bean
    public int flagThreshold(RuleConfigRepository repo) {
        var opt = repo.findByRuleCode(AggregatorStage.RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return AggregatorStage.DEFAULT_FLAG_THRESHOLD;
        }
        return opt.get().getInt("flagThreshold", AggregatorStage.DEFAULT_FLAG_THRESHOLD);
    }

    @Bean
    public AggregatorStage aggregatorStage(int scoreThreshold, int flagThreshold) {
        return new AggregatorStage(scoreThreshold, flagThreshold);
    }

    @Bean
    public FraudPipeline fraudPipeline(List<PipelineStage> stages,
                                       AggregatorStage aggregator,
                                       int scoreThreshold) {
        return new FraudPipeline(stages, aggregator, scoreThreshold);
    }
}
