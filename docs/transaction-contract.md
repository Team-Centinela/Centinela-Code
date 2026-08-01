# Contrato de Transaccion - Centinela

## Estructura del Contrato

```json
{
  "cuentaId": "string (requerido)",
  "monto": "BigDecimal (requerido, min: 0.01)",
  "moneda": "string (default: USD)",
  "marcaTiempo": "Long (epoch millis, opcional)",
  "ubicacionLat": "Double (rango: -90 a 90, opcional)",
  "ubicacionLon": "Double (rango: -180 a 180, opcional)",
  "comercioId": "string (opcional)",
  "comercioCategoria": "string (opcional)"
}
```

## Campos Detallados

| Campo | Tipo | Obligatorio | Restricciones | Proposito |
|---|---|---|---|---|
| cuentaId | String | Si | No vacio, max 64 chars | Identificador unico de cuenta |
| monto | BigDecimal | Si | > 0.01, precision 18,2 | Valor monetario de la transaccion |
| moneda | String | No | Default: USD, ISO 4217 | Moneda de la transaccion |
| marcaTiempo | Long | No | Epoch millis, no futuro | Momento exacto de la transaccion |
| ubicacionLat | Double | No | -90 a 90 | Latitud de la ubicacion |
| ubicacionLon | Double | No | -180 a 180 | Longitud de la ubicacion |
| comercioId | String | No | Max 128 chars | Identificador del comercio |
| comercioCategoria | String | No | Max 128 chars | Categoria del comercio |

## Preguntas que Responde el Contrato

| Pregunta | Campo(s) Utilizado(s) | Regla que lo Requiere |
|---|---|---|
| ¿De que cuenta proviene? | cuentaId | Velocidad, Monto Atipico |
| ¿Cual es el monto? | monto, moneda | Monto Atipico |
| ¿En que instante exacto ocurrio? | marcaTiempo | Velocidad, Geo-imposible |
| ¿Desde que ubicacion se origino? | ubicacionLat, ubicacionLon | Geo-imposible |
| ¿Hacia que comercio o categoria se dirige? | comercioId, comercioCategoria | Comercio de Riesgo |
| ¿Como se identifica de forma unica? | id (generado por el sistema) | Trazabilidad, Idempotencia |

## Decisiones Tomadas

### 1. Marca de Tiempo
- **Decision**: El servidor genera la marca de tiempo si el cliente no la envia
- **Justificacion**: Un actor malicioso podria manipular la regla de velocidad enviando marcas de tiempo futuras o del pasado
- **Implementacion**: `Instant.now()` cuando `marcaTiempo` es null

### 2. Monto
- **Decision**: BigDecimal con precision 18,2
- **Justificacion**: El tipo float/double genera errores de precision con valores monetarios. BigDecimal es el estandar para dinero en Java
- **Moneda**: ISO 4217, default USD

### 3. Ubicacion
- **Decision**: Coordenadas separadas (lat, lon) en Double
- **Justificacion**: Permite calculo de distancias con formula de Haversine. Las coordenadas se validan en rango (-90/90, -180/180)

### 4. Identificador
- **Decision**: UUID v4 generado por el servidor
- **Justificacion**: UUID4 garantiza unicidad sin coordinacion. El cliente no envia ID - el sistema lo genera

## Validaciones

### Rechazo con Codigo 400
- Campos obligatorios ausentes
- Monto <= 0.01
- Marca de tiempo futura
- Coordenadas fuera de rango
- Campos no contemplados (politica: rechazar)

### Ejemplo de Payload Valido
```json
{
  "cuentaId": "cuenta-001",
  "monto": 150.00,
  "moneda": "USD",
  "marcaTiempo": 1690000000000,
  "ubicacionLat": 6.2442,
  "ubicacionLon": -75.5812,
  "comercioId": "supermercado-xyz",
  "comercioCategoria": "retail"
}
```

### Ejemplo de Payload Invalido
```json
{
  "cuentaId": "",
  "monto": -50.00,
  "moneda": "USD"
}
```
