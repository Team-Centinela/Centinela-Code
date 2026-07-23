package com.centinela.ingestion.domain.port;

public interface OutboxEventPort {
    void save(OutboxEvent event);
}
