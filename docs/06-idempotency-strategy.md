# Estrategia de Idempotencia - Centinela

## Definición

La idempotencia garantiza que una operación produzca el mismo resultado aunque se ejecute múltiples veces. En Centinela, esto es crítico para evitar transacciones duplicadas.

## Punto de Confirmación

```
Cliente ──▶ API ──▶ Validar ──▶ Persistir ──▶ Confirmar ──▶ Responder
                                      │
                                      ▼
                               Publicar Evento
```

**Punto seguro de confirmación**: Después de persistir la transacción y antes de publicar el evento.

**Justificación**:
- La transacción ya está guardada de forma segura
- Si falla la publicación del evento, se puede reintentar
- El cliente recibe confirmación antes de que se ejecute el análisis

## Comportamiento ante Duplicados

### Escenario 1: Reenvío de Transacción

```json
// Primera recepción
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "accepted"
}

// Segunda recepción (duplicada)
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "duplicate",
  "message": "Transaction already processed"
}
```

### Escenario 2: Confirmación Perdida

Si el cliente no recibe la confirmación:
1. El cliente reenvía la misma transacción
2. La API detecta el `transaction_id` duplicado
3. Retorna `409 Conflict` con el estado actual

## Implementación

### En la API

```python
@app.post("/api/v1/transactions")
async def receive_transaction(transaction: Transaction):
    # Verificar si ya existe
    existing = await cosmos_db.query(
        "SELECT * FROM c WHERE c.transactionId = @id",
        parameters=[{"name": "@id", "value": str(transaction.transaction_id)}]
    )
    
    if existing:
        raise HTTPException(
            status_code=409,
            detail={
                "status": "duplicate",
                "message": "Transaction already processed"
            }
        )
    
    # Persistir nueva transacción
    await cosmos_db.create(transaction)
    
    # Publicar evento
    await service_bus.publish("transactions", transaction)
    
    return {"status": "accepted", "transaction_id": str(transaction.transaction_id)}
```

### En Cosmos DB

Usar `transactionId` como parte de la clave de partición o como identificador único:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "accountId": "ACC-123456",
  "amount": 150000,
  ...
}
```

## Ventanas Temporales

| Operación | Ventana de Deduplicación |
|-----------|--------------------------|
| Transacción | 24 horas |
| Evento | 1 hora |
| Caso | 7 días |

## Validación

### Prueba de Idempotencia

```bash
# Enviar la misma transacción dos veces
curl -X POST http://localhost:8000/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "test-123", ...}'

# Respuesta esperada: 201 Created

curl -X POST http://localhost:8000/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "test-123", ...}'

# Respuesta esperada: 409 Conflict
```

## Conclusión

La estrategia de idempotencia garantiza que:
1. No se procesen transacciones duplicadas
2. El cliente reciba confirmación antes del análisis
3. Se pueda recuperar de fallos de red
4. El sistema sea tolerante a fallos
