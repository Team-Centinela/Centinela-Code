# Contrato de Transacción - Centinela

## Estructura del Payload

```json
{
  "transactionId": "string (GUID)",
  "timestamp": "string (ISO 8601 UTC)",
  "accountId": "string",
  "amount": "decimal",
  "currency": "string (ISO 4217)",
  "type": "string (enum)",
  "location": {
    "latitude": "decimal",
    "longitude": "decimal"
  },
  "merchant": {
    "id": "string",
    "name": "string",
    "category": "string"
  },
  "metadata": {
    "channel": "string",
    "deviceId": "string",
    "ipAddress": "string (optional)"
  }
}
```

## Descripción de Campos

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `transactionId` | string (GUID) | Sí | Identificador único de la transacción. Generado por el cliente. |
| `timestamp` | string (ISO 8601 UTC) | Sí | Fecha y hora de la transacción en formato UTC. |
| `accountId` | string | Sí | Identificador de la cuenta de origen. |
| `amount` | decimal | Sí | Monto de la transacción. Debe ser positivo. |
| `currency` | string (ISO 4217) | Sí | Código de moneda (ej: COP, USD, EUR). |
| `type` | string (enum) | Sí | Tipo de transacción: `purchase`, `transfer`, `withdrawal`. |
| `location.latitude` | decimal | Sí | Latitud de la transacción (-90 a 90). |
| `location.longitude` | decimal | Sí | Longitud de la transacción (-180 a 180). |
| `merchant.id` | string | Sí | Identificador único del comercio. |
| `merchant.name` | string | Sí | Nombre del comercio. |
| `merchant.category` | string | Sí | Categoría del comercio (ej: `grocery`, `electronics`, `travel`). |
| `metadata.channel` | string | No | Canal de la transacción: `online`, `pos`, `atm`, `mobile`. |
| `metadata.deviceId` | string | No | Identificador del dispositivo utilizado. |
| `metadata.ipAddress` | string | No | Dirección IP de origen (si aplica). |

## Decisiones Explícitas

### 1. Marca de Tiempo (timestamp)

**Decisión**: Se acepta la marca de tiempo enviada por el cliente, pero se valida contra la hora del servidor.

**Justificación**: 
- El cliente envía la marca de tiempo original de la transacción
- El servidor registra su propia marca de tiempo al recibir la transacción
- Ambas se almacenan para auditoría
- La regla de velocidad usa la marca de tiempo del servidor (no manipulable)

**Implementación**:
```json
{
  "clientTimestamp": "2024-01-15T14:30:00Z",  // Enviada por el cliente
  "serverTimestamp": "2024-01-15T14:30:01Z"   // Registrada por el servidor
}
```

### 2. Monto (amount)

**Decisión**: Tipo `decimal` en JSON, almacenado como `DECIMAL(18,2)` en la base de datos.

**Justificación**:
- Los valores monetarios no deben usar punto flotante (float/double) por precisión
- `decimal` en JSON se serializa como número con punto decimal
- En la base de datos se almacena como `DECIMAL(18,2)` para soportar montos hasta 999,999,999,999,999.99
- La moneda se almacena por separado (ISO 4217)

**Tratamiento de moneda**:
- El sistema almacena el monto original en su moneda
- No se realiza conversión de moneda automáticamente
- Las reglas de detección comparan montos de la misma moneda

### 3. Ubicación (location)

**Decisión**: Coordenadas GPS en formato decimal (latitude, longitude).

**Justificación**:
- Permite cálculo de distancias usando fórmula de Haversine
- Formato estándar para geolocalización
- Precisión suficiente para el cálculo de distancias

**Cálculo de distancia**:
```python
from math import radians, cos, sin, asin, sqrt

def haversine(lat1, lon1, lat2, lon2):
    """Calcula la distancia en km entre dos puntos GPS"""
    R = 6371  # Radio de la Tierra en km
    
    lat1, lon1, lat2, lon2 = map(radians, [lat1, lon1, lat2, lon2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    
    return R * c
```

### 4. Identificador (transactionId)

**Decisión**: GUID generado por el cliente, validado por el servidor.

**Justificación**:
- GUID garantiza unicidad global sin coordinación
- El cliente genera el ID para evitar duplicados por reintentos
- El servidor valida el formato UUID v4
- Ante duplicados, se rechaza la transacción con código 409 Conflict

**Comportamiento ante duplicados**:
```json
// Transacción duplicada
{
  "error": "Duplicate transaction",
  "message": "Transaction with this ID already exists",
  "statusCode": 409
}
```

## Ejemplo de Payload Válido

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T14:30:00Z",
  "accountId": "ACC-123456",
  "amount": 150000.00,
  "currency": "COP",
  "type": "purchase",
  "location": {
    "latitude": 6.2442,
    "longitude": -75.5812
  },
  "merchant": {
    "id": "MERCH-001",
    "name": "Supermercado El Éxito",
    "category": "grocery"
  },
  "metadata": {
    "channel": "pos",
    "deviceId": "POS-001",
    "ipAddress": "190.25.100.50"
  }
}
```
