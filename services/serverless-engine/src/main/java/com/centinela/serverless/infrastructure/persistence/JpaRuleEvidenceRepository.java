package com.centinela.serverless.infrastructure.persistence;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaRuleEvidenceRepository implements RuleEvidenceRepository {

    private final JdbcTemplate jdbc;

    public JpaRuleEvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long countRecentForAccount(TransactionMessage tx) {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                  FROM oltp.transactions
                 WHERE account_id = ?
                   AND timestamp >= ?
                """,
                Long.class,
                tx.accountId(),
                tx.timestamp().minusSeconds(600L));
        return count == null ? 0L : count;
    }

    @Override
    public boolean isHighRiskMerchant(String merchantId) {
        Integer flagged = jdbc.queryForObject(
                "SELECT 1 FROM oltp.flagged_merchants WHERE merchant_id = ?",
                Integer.class,
                merchantId);
        return flagged != null;
    }

    @Override
    public double getAverageAmount(String accountId) {
        Double avg = jdbc.queryForObject(
                "SELECT AVG(amount) FROM oltp.transactions WHERE account_id = ?",
                Double.class,
                accountId);
        return avg == null ? Double.NaN : avg;
    }

    @Override
    public Optional<TransactionLocation> previousLocationFor(TransactionMessage tx) {
        List<TransactionLocation> rows = jdbc.query(
                """
                SELECT latitude, longitude, timestamp
                  FROM oltp.transactions
                 WHERE account_id = ?
                   AND timestamp < ?
                   AND timestamp >= ?
                 ORDER BY timestamp DESC
                 LIMIT 1
                """,
                (rs, i) -> new TransactionLocation(
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getTimestamp("timestamp").toInstant()),
                tx.accountId(),
                tx.timestamp(),
                tx.timestamp().minusSeconds(3600L));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
