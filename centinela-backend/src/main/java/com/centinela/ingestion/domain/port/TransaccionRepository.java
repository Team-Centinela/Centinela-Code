package com.centinela.ingestion.domain.port;

import com.centinela.ingestion.domain.model.Transaccion;
import java.util.List;
import java.util.Optional;

public interface TransaccionRepository {
    Transaccion save(Transaccion transaccion);
    Optional<Transaccion> findById(String id);
    List<Transaccion> findByCuentaId(String cuentaId);
    List<Transaccion> findRecentByCuentaId(String cuentaId, int limit);
    List<Transaccion> findAll();
}
