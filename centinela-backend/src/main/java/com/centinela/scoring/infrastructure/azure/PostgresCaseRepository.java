package com.centinela.scoring.infrastructure.azure;

import com.centinela.scoring.domain.model.FraudCase;
import com.centinela.scoring.domain.port.CaseRepository;
import com.centinela.scoring.infrastructure.persistence.CaseAuditoriaJpaEntity;
import com.centinela.scoring.infrastructure.persistence.CaseAuditoriaJpaRepository;
import com.centinela.scoring.infrastructure.persistence.FraudCaseJpaEntity;
import com.centinela.scoring.infrastructure.persistence.FraudCaseJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Profile("azure")
public class PostgresCaseRepository implements CaseRepository {

    private final FraudCaseJpaRepository jpaRepository;
    private final CaseAuditoriaJpaRepository auditoriaRepository;

    public PostgresCaseRepository(FraudCaseJpaRepository jpaRepository,
                                   CaseAuditoriaJpaRepository auditoriaRepository) {
        this.jpaRepository = jpaRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Override
    public FraudCase save(FraudCase fraudCase) {
        FraudCaseJpaEntity entity = new FraudCaseJpaEntity();
        entity.setId(fraudCase.getCaseId() != null ? fraudCase.getCaseId() : UUID.randomUUID().toString());
        entity.setTransactionId(fraudCase.getTransactionId());
        entity.setCuentaId(fraudCase.getCuentaId());
        entity.setScore(fraudCase.getScore());
        entity.setUmbral(fraudCase.getUmbral());
        entity.setEstado(fraudCase.getEstado());
        entity.setExplicacion(fraudCase.getExplicacion());
        entity.setFechaApertura(fraudCase.getFechaApertura());
        entity.setFechaResolucion(fraudCase.getFechaResolucion());
        entity.setAnalistaId(fraudCase.getAnalistaId());

        FraudCaseJpaEntity saved = jpaRepository.save(entity);

        CaseAuditoriaJpaEntity audit = new CaseAuditoriaJpaEntity();
        audit.setId(UUID.randomUUID().toString());
        audit.setFraudCase(saved);
        audit.setAccion("CREACION_CASO");
        audit.setValorNuevo("Score: " + saved.getScore() + ", Umbral: " + saved.getUmbral());
        audit.setUsuario("sistema");
        auditoriaRepository.save(audit);

        fraudCase.setCaseId(saved.getId());
        return fraudCase;
    }

    @Override
    public Optional<FraudCase> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<FraudCase> findByCuentaId(String cuentaId) {
        return jpaRepository.findByCuentaIdOrderByFechaAperturaDesc(cuentaId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<FraudCase> findByEstado(String estado) {
        return jpaRepository.findByEstadoOrderByFechaAperturaDesc(estado)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    private FraudCase toDomain(FraudCaseJpaEntity entity) {
        FraudCase fc = new FraudCase();
        fc.setCaseId(entity.getId());
        fc.setTransactionId(entity.getTransactionId());
        fc.setCuentaId(entity.getCuentaId());
        fc.setScore(entity.getScore());
        fc.setUmbral(entity.getUmbral());
        fc.setEstado(entity.getEstado());
        fc.setExplicacion(entity.getExplicacion());
        fc.setFechaApertura(entity.getFechaApertura());
        fc.setFechaResolucion(entity.getFechaResolucion());
        fc.setAnalistaId(entity.getAnalistaId());
        return fc;
    }
}
