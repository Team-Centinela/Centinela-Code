-- V1: Create complete Centinela schema
-- Transactions, Fraud Cases, Audit Trail, Rule Config, Outbox

-- Transactions table (high-volume, write-heavy)
CREATE TABLE IF NOT EXISTS transacciones (
    id VARCHAR(36) PRIMARY KEY,
    cuenta_id VARCHAR(64) NOT NULL,
    monto DECIMAL(18,2) NOT NULL,
    moneda VARCHAR(3) NOT NULL DEFAULT 'USD',
    marca_tiempo TIMESTAMPTZ NOT NULL,
    ubicacion_lat DOUBLE PRECISION,
    ubicacion_lon DOUBLE PRECISION,
    comercio_id VARCHAR(128),
    comercio_categoria VARCHAR(128),
    score DECIMAL(5,2),
    marcada BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_evaluacion TIMESTAMPTZ,
    reglas_activadas JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta_id ON transacciones (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta_tiempo ON transacciones (cuenta_id, marca_tiempo DESC);
CREATE INDEX IF NOT EXISTS idx_transacciones_marcada ON transacciones (marcada) WHERE marcada = TRUE;

-- Fraud Cases
CREATE TABLE IF NOT EXISTS fraud_cases (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    cuenta_id VARCHAR(64) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    umbral DECIMAL(5,2) NOT NULL,
    estado VARCHAR(32) NOT NULL DEFAULT 'ABIERTO',
    explicacion TEXT,
    fecha_apertura TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_resolucion TIMESTAMPTZ,
    analista_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fraud_cases_estado ON fraud_cases (estado);
CREATE INDEX IF NOT EXISTS idx_fraud_cases_cuenta ON fraud_cases (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_fraud_cases_analista ON fraud_cases (analista_id);

-- Case Status Catalog
CREATE TABLE IF NOT EXISTS case_estados (
    codigo VARCHAR(32) PRIMARY KEY,
    nombre VARCHAR(128) NOT NULL,
    descripcion TEXT,
    orden INTEGER NOT NULL
);

INSERT INTO case_estados (codigo, nombre, descripcion, orden) VALUES
('ABIERTO', 'Abierto', 'Caso recien creado, pendiente de asignacion', 1),
('EN_REVISION', 'En Revision', 'Asignado a un analista, en proceso de revision', 2),
('ESCALADO', 'Escalado', 'Escalado a nivel superior de verificacion', 3),
('CONFIRMADO_FRAUDE', 'Confirmado Fraude', 'Fraude verificado por el analista', 4),
('DESCARTADO', 'Descartado', 'Falso positivo, caso cerrado', 5),
('CERRADO', 'Cerrado', 'Caso resuelto y archivado', 6);

-- Case Assignments
CREATE TABLE IF NOT EXISTS case_asignaciones (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL REFERENCES fraud_cases(id),
    analista_id VARCHAR(64) NOT NULL,
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_desasignacion TIMESTAMPTZ,
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_asignaciones_case ON case_asignaciones (case_id);
CREATE INDEX IF NOT EXISTS idx_asignaciones_analista ON case_asignaciones (analista_id);

-- Case Resolutions
CREATE TABLE IF NOT EXISTS case_resoluciones (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL REFERENCES fraud_cases(id),
    decision VARCHAR(32) NOT NULL,
    analista_id VARCHAR(64) NOT NULL,
    observaciones TEXT,
    fecha_resolucion TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resoluciones_case ON case_resoluciones (case_id);

-- Audit Trail (immutable)
CREATE TABLE IF NOT EXISTS case_auditoria (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL REFERENCES fraud_cases(id),
    accion VARCHAR(64) NOT NULL,
    valor_anterior TEXT,
    valor_nuevo TEXT,
    usuario VARCHAR(64) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_auditoria_case ON case_auditoria (case_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON case_auditoria (fecha DESC);

-- Document Verification Records
CREATE TABLE IF NOT EXISTS documentos_verificacion (
    id VARCHAR(36) PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL REFERENCES fraud_cases(id),
    blob_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    nombre_extraido VARCHAR(256),
    identificacion_extraida VARCHAR(128),
    fecha_extraccion TIMESTAMPTZ,
    estado VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
    error_mensaje TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documentos_case ON documentos_verificacion (case_id);

-- Rule Configuration (runtime-configurable)
CREATE TABLE IF NOT EXISTS rules_config (
    rule_id VARCHAR(64) PRIMARY KEY,
    nombre VARCHAR(128) NOT NULL,
    descripcion TEXT,
    puntos INTEGER NOT NULL DEFAULT 0,
    habilitada BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO rules_config (rule_id, nombre, descripcion, puntos, habilitada, config) VALUES
('VELOCITY', 'Velocidad de Transaccion', 'Detecta multiples transacciones en ventana corta', 35, true, '{"windowMinutes": 5, "maxTransactions": 3}'),
('AMOUNT', 'Monto Atipico', 'Detecta montos significativamente superiores al historial', 30, true, '{"multiplierThreshold": 10}'),
('GEO_IMPOSSIBLE', 'Ubicacion Geograficamente Imposible', 'Detecta ubicaciones incompatibles en tiempo dado', 25, true, '{"maxDistanceKm": 1000, "minTimeMinutes": 60}'),
('MERCHANT_RISK', 'Comercio de Riesgo', 'Detecta transacciones hacia comercios marcados', 20, true, '{}');

-- Processed Events (idempotency)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events (event_type);
CREATE INDEX IF NOT EXISTS idx_processed_events_date ON processed_events (processed_at DESC);

-- Outbox Events (reliable publishing)
CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished ON outbox_events (created_at) WHERE published = FALSE;
