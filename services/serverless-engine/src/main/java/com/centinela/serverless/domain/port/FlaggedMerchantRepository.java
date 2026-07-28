package com.centinela.serverless.domain.port;

import java.util.Optional;

public interface FlaggedMerchantRepository {
    Optional<FlaggedMerchant> findByMerchantId(String merchantId);
}
