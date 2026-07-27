package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.port.FlaggedMerchant;
import com.centinela.serverless.domain.port.FlaggedMerchantRepository;

import java.util.Optional;

/**
 * Placeholder {@link FlaggedMerchantRepository} that always returns empty.
 * {@code FR-2 Atypical Amount} and {@code FR-4 High-Risk Merchant}
 * therefore never fire in this configuration.
 *
 * <p>TODO(@3105jero): replace with the JPA query against
 * {@code rules_config.flagged_merchants}.</p>
 */
public class InMemoryFlaggedMerchantRepository implements FlaggedMerchantRepository {

    @Override
    public Optional<FlaggedMerchant> findByMerchantId(String merchantId) {
        return Optional.empty();
    }
}
