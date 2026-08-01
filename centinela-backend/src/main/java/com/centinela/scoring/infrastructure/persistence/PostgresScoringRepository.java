package com.centinela.scoring.infrastructure.persistence;

import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.ScoringRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!local")
public class PostgresScoringRepository implements ScoringRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void save(TransactionHistory transaction) {
        entityManager.createNativeQuery(
            "INSERT INTO transacciones (id, transaction_id, cuenta_id, monto, moneda, marca_tiempo, ubicacion_lat, ubicacion_lon, comercio_id, comercio_categoria, created_at) " +
            "VALUES (:id, :transactionId, :cuentaId, :monto, :moneda, :marcaTiempo, :lat, :lon, :comercioId, :comercioCategoria, NOW()) " +
            "ON CONFLICT (transaction_id) DO UPDATE SET score = EXCLUDED.score, marcada = EXCLUDED.marcada, fecha_evaluacion = EXCLUDED.fecha_evaluacion, reglas_activadas = EXCLUDED.reglas_activadas")
            .setParameter("id", UUID.randomUUID().toString())
            .setParameter("transactionId", transaction.getTransactionId())
            .setParameter("cuentaId", transaction.getCuentaId())
            .setParameter("monto", transaction.getMonto())
            .setParameter("moneda", "USD")
            .setParameter("marcaTiempo", transaction.getMarcaTiempo())
            .setParameter("lat", transaction.getUbicacionLat())
            .setParameter("lon", transaction.getUbicacionLon())
            .setParameter("comercioId", transaction.getComercioId())
            .setParameter("comercioCategoria", transaction.getComercioCategoria())
            .executeUpdate();
    }

    @Override
    public List<TransactionHistory> findRecentByCuentaId(String cuentaId, Instant since) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(
            "SELECT transaction_id, cuenta_id, monto, marca_tiempo, ubicacion_lat, ubicacion_lon, comercio_id, comercio_categoria " +
            "FROM transacciones WHERE cuenta_id = :cuentaId AND marca_tiempo >= :since " +
            "ORDER BY marca_tiempo DESC")
            .setParameter("cuentaId", cuentaId)
            .setParameter("since", since)
            .getResultList();

        return results.stream().map(row -> {
            TransactionHistory th = new TransactionHistory();
            th.setTransactionId((String) row[0]);
            th.setCuentaId((String) row[1]);
            th.setMonto((BigDecimal) row[2]);
            th.setMarcaTiempo(toInstant(row[3]));
            th.setUbicacionLat(row[4] != null ? ((Number) row[4]).doubleValue() : null);
            th.setUbicacionLon(row[5] != null ? ((Number) row[5]).doubleValue() : null);
            th.setComercioId((String) row[6]);
            th.setComercioCategoria((String) row[7]);
            return th;
        }).toList();
    }

    @Override
    public Optional<TransactionHistory> findLastByCuentaId(String cuentaId) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(
            "SELECT transaction_id, cuenta_id, monto, marca_tiempo, ubicacion_lat, ubicacion_lon, comercio_id, comercio_categoria " +
            "FROM transacciones WHERE cuenta_id = :cuentaId " +
            "ORDER BY marca_tiempo DESC LIMIT 1")
            .setParameter("cuentaId", cuentaId)
            .getResultList();

        return results.stream().map(row -> {
            TransactionHistory th = new TransactionHistory();
            th.setTransactionId((String) row[0]);
            th.setCuentaId((String) row[1]);
            th.setMonto((BigDecimal) row[2]);
            th.setMarcaTiempo(toInstant(row[3]));
            th.setUbicacionLat(row[4] != null ? ((Number) row[4]).doubleValue() : null);
            th.setUbicacionLon(row[5] != null ? ((Number) row[5]).doubleValue() : null);
            th.setComercioId((String) row[6]);
            th.setComercioCategoria((String) row[7]);
            return th;
        }).findFirst();
    }

    @Override
    public BigDecimal calculateAverageMonto(String cuentaId) {
        Object result = entityManager.createNativeQuery(
            "SELECT COALESCE(AVG(monto), 0) FROM transacciones WHERE cuenta_id = :cuentaId")
            .setParameter("cuentaId", cuentaId)
            .getSingleResult();
        return new BigDecimal(result.toString());
    }

    @Override
    public long countRecentByCuentaId(String cuentaId, Instant since) {
        Object result = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = :cuentaId AND marca_tiempo >= :since")
            .setParameter("cuentaId", cuentaId)
            .setParameter("since", since)
            .getSingleResult();
        return ((Number) result).longValue();
    }

    private Instant toInstant(Object dbValue) {
        if (dbValue == null) return null;
        if (dbValue instanceof Instant) return (Instant) dbValue;
        if (dbValue instanceof java.sql.Timestamp) return ((java.sql.Timestamp) dbValue).toInstant();
        if (dbValue instanceof java.time.OffsetDateTime) return ((java.time.OffsetDateTime) dbValue).toInstant();
        return Instant.parse(dbValue.toString());
    }
}
