package com.centinela.serverless.infrastructure.configuration;

import com.centinela.serverless.adapter.out.persistence.InMemoryFlaggedMerchantRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryRuleConfigRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryTransactionStatisticsRepository;
import com.centinela.serverless.adapter.out.persistence.InMemoryTransactionStatsRepository;
import com.centinela.serverless.domain.port.FlaggedMerchantRepository;
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
 * <pre>
 *   VelocityRule            ─┐
 *   AtypicalAmountRule      ─┤   List&lt;PipelineStage&gt;
 *   ImpossibleGeoRule       ─┤   ───▶ FraudPipeline(stages, aggregator, configRepo)
 *   HighRiskMerchantRule    ─┘
 *                            ┌──▶ AggregatorStage(configRepo)
 *
 *   InMemoryRuleConfigRepository           ──┐
 *   InMemoryTransactionStatisticsRepository ─┤
 *   InMemoryTransactionStatsRepository      ─┼──▶ rule constructors
 *   InMemoryFlaggedMerchantRepository       ─┘
 * </pre>
 *
 * <p>The in-memory repository stubs return empty {@code Optional} / 0 so each
 * rule falls back to compile-time defaults. JPA-backed implementations are
 * tracked as @3105jero follow-ups under epic #54.</p>
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

    @Bean
    public AggregatorStage aggregatorStage() {
        return new AggregatorStage(ruleConfigRepository());
    }

    @Bean
    public FraudPipeline fraudPipeline(List<PipelineStage> stages, AggregatorStage aggregator) {
        return new FraudPipeline(stages, aggregator, ruleConfigRepository());
    }
}
