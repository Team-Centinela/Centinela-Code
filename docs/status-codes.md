# Codigos de Estado HTTP - API Centinela

## Endpoints de Ingestion

### POST /api/v1/transacciones

| Codigo | Significado | Cuando Ocurre | Ejemplo de Response |
|---|---|---|---|
| `201` | Created | Transaccion aceptada y encolada para scoring | `{ "transactionId": "...", "status": "ACCEPTED", ... }` |
| `400` | Bad Request | Campo obligatorio faltante o formato invalido (monto no numerico, cuentaId vacio) | `{ "error": "Validation failed", "details": "..." }` |
| `429` | Too Many Requests | Rate limit excedido (100 req/min por IP, Bucket4j) | `{ "error": "Rate limit exceeded" }` |
| `500` | Internal Server Error | Error inesperado en el servidor | `{ "error": "Internal server error" }` |

### GET /api/v1/transacciones

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Lista de transacciones retornada exitosamente |
| `500` | Internal Server Error | Error al consultar la base de datos |

### GET /api/v1/transacciones/{id}

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Transaccion encontrada |
| `404` | Not Found | No existe transaccion con ese ID |

## Endpoints de Casos

### GET /api/v1/cases

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Lista de casos de fraude retornada |
| `500` | Internal Server Error | Error al consultar la base de datos |

### GET /api/v1/cases?cuentaId={cuentaId}

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Lista de casos filtrados por cuenta |
| `200` | OK | Lista vacia si no hay casos para esa cuenta |

### GET /api/v1/cases/{id}

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Caso de fraude encontrado con explicacion |
| `404` | Not Found | No existe caso con ese ID |

## Endpoints de Monitoreo

### GET /api/v1/transacciones/health

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Servicio operativo, DB conectada |

### GET /actuator/health

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Todos los componentes UP |
| `503` | Service Unavailable | Algún componente DOWN (DB, disco, etc.) |

### GET /actuator/prometheus

| Codigo | Significado | Cuando Ocurre |
|---|---|---|
| `200` | OK | Metricas Prometheus expuestas |

## Manejo de Errores

Todos los errores retornan JSON con la estructura:
```json
{
  "error": "Tipo de error",
  "message": "Descripcion en espanol",
  "timestamp": "ISO-8601"
}
```

El `GlobalExceptionHandler` captura:
- `MethodArgumentNotValidException` → 400
- `EntityNotFoundException` → 404
- `RuntimeException` → 500
- Cualquier otra excepcion no controlada → 500
