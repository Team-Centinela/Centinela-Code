package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Placeholder {@link RuleConfigRepository} returning an empty optional on every
 * lookup so each rule falls back to its compile-time defaults
 * (see {@code domain/service/*Rule#loadConfig()}).
 *
 * <p>TODO(@3105jero, epic #54 evidence-persistence half): replace with the
 * JPA-backed implementation that reads from
 * {@code rules_config.rule_configs} once that table is the authoritative
 * source for tunable rule parameters in production. Until then, the engine
 * is fully functional but operators cannot tune the four FR parameters
 * from the UI — they would have to redeploy.</p>
 */
@Repository
public class InMemoryRuleConfigRepository implements RuleConfigRepository {

    @Override
    public Optional<RuleConfig> findByRuleCode(String ruleCode) {
        return Optional.empty();
    }
}
