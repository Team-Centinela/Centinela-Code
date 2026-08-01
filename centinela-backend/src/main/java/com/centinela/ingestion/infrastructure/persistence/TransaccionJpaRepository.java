package com.centinela.ingestion.infrastructure.persistence;

import com.centinela.ingestion.domain.model.Coordenada;
import com.centinela.ingestion.domain.model.Transaccion;
import com.centinela.ingestion.domain.port.TransaccionRepository;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class TransaccionJpaRepository implements TransaccionRepository {

    private final TransaccionJpaEntityRepository jpaRepo;

    public TransaccionJpaRepository(TransaccionJpaEntityRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public Transaccion save(Transaccion transaccion) {
        TransaccionJpaEntity entity = toEntity(transaccion);
        TransaccionJpaEntity saved = jpaRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Transaccion> findById(String id) {
        return jpaRepo.findByTransactionId(id).map(this::toDomain);
    }

    @Override
    public List<Transaccion> findByCuentaId(String cuentaId) {
        return jpaRepo.findByCuentaIdOrderByMarcaTiempoDesc(cuentaId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transaccion> findRecentByCuentaId(String cuentaId, int limit) {
        return jpaRepo.findTopNByCuentaIdOrderByMarcaTiempoDesc(cuentaId, limit)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transaccion> findAll() {
        return jpaRepo.findAll()
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    private TransaccionJpaEntity toEntity(Transaccion t) {
        TransaccionJpaEntity e = new TransaccionJpaEntity();
        e.setId(t.getId() != null ? t.getId() : java.util.UUID.randomUUID().toString());
        e.setTransactionId(t.getId());
        e.setCuentaId(t.getCuentaId());
        e.setMonto(t.getMonto());
        e.setMoneda(t.getMoneda());
        e.setMarcaTiempo(t.getMarcaTiempo());
        if (t.getUbicacion() != null) {
            e.setUbicacionLat(t.getUbicacion().getLatitud());
            e.setUbicacionLon(t.getUbicacion().getLongitud());
        }
        e.setComercioId(t.getComercioId());
        e.setComercioCategoria(t.getComercioCategoria());
        e.setCreatedAt(t.getFechaCreacion() != null ? t.getFechaCreacion() : Instant.now());
        return e;
    }

    private Transaccion toDomain(TransaccionJpaEntity e) {
        Coordenada ubicacion = (e.getUbicacionLat() != null && e.getUbicacionLon() != null)
                ? new Coordenada(e.getUbicacionLat(), e.getUbicacionLon())
                : null;

        return Transaccion.builder()
                .id(e.getTransactionId())
                .cuentaId(e.getCuentaId())
                .monto(e.getMonto())
                .moneda(e.getMoneda())
                .marcaTiempo(e.getMarcaTiempo())
                .ubicacion(ubicacion)
                .comercioId(e.getComercioId())
                .comercioCategoria(e.getComercioCategoria())
                .fechaCreacion(e.getCreatedAt())
                .build();
    }
}
