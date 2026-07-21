package com.centinela.corebackend.transaction.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TransactionOutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
