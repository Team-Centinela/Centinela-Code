package com.centinela.scoring.infrastructure.persistence;

import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.ScoringRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("azure")
public class PostgresScoringRepository implements ScoringRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void save(TransactionHistory transaction) {
        // Uses the transacciones table from the ingestion module
        entityManager.createNativeQuery(
            "INSERT INTO transacciones (id, cuenta_id, monto, moneda, marca_tiempo, ubicacion_lat, ubicacion_lon, comercio_id, comercio_categoria, created_at) " +
            "VALUES (:id, :cuentaId, :monto, :moneda, :marcaTiempo, :lat, :lon, :comercioId, :comercioCategoria, NOW()) " +
            "ON CONFLICT (id) DO UPDATE SET score = EXCLUDED.score, marcada = EXCLUDED.marcada, fecha_evaluacion = EXCLUDED.fecha_evaluacion, reglas_activadas = EXCLUDED.reglas_activadas")
            .setParameter("id", transaction.getTransactionId())
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
            "SELECT id, cuenta_id, monto, marca_tiempo, ubicacion_lat, ubicacion_lon, comercio_id " +
            "FROM transacciones WHERE cuenta_id = :cuentaId AND marca_tiempo >= :since " +
            "ORDER BY marca_tiempo DESC")
            .setParameter("cuentaId", cuentaId)
            .setParameter("since", since)
            .getResultList();

        return results.stream().map(row -> {
            TransactionHistory th = new TransactionHistory();
            th.setTransactionId((String) row[0]);
            th.setCuentaId((String) row[1]);
            th.setMonto((java.math.BigDecimal) row[2]);
            th.setMarcaTiempo(((java.sql.Timestamp) row[3]).toInstant());
            th.setUbicacionLat(row[4] != null ? ((Number) row[4]).doubleValue() : null);
            th.setUbicacionLon(row[5] != null ? ((Number) row[5]).doubleValue() : null);
            th.setComercioId((String) row[6]);
            return th;
        }).toList();
    }
}
