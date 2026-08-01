# Garantias de Entrega - Mensajeria Centinela

## Escenario 1: Transaccion recibida exitosamente

**Flujo:** API → PostgreSQL → Service Bus → Scoring

1. API recibe transaccion y persiste en PostgreSQL
2. API publica evento `TRANSACTION_RECEIVED` en Service Bus (cola `transacciones-ingestion`)
3. Consumer recibe el mensaje, deserializa y ejecuta scoring
4. Si score > umbral, se crea caso de fraude y se publica en topic `casos-fraude`

**Garantia:** Al menos una vez (at-least-once). Service Bus Standard tiene duplicate detection configurable.

## Escenario 2: Service Bus no disponible

**Flujo alternativo:** API → PostgreSQL → Scoring directo

Si Service Bus falla, el scoring se ejecuta directamente en `IngestionService` via llamada sincrona a `ScoringService.evaluarTransaccion()`. La transaccion se persiste y analiza de todas formas.

**Garantia:** La transaccion se procesa aunque la mensajeria async falle. Degradacion gracefully.

## Escenario 3: Consumer falla al procesar

**Flujo:** Service Bus → Consumer → Error → Dead-Letter Queue

- Service Bus reintent automaticamente hasta `max-delivery-count: 10`
- Despues de 10 fallos, el mensaje se mueve a la Dead-Letter Queue (`transacciones-ingestion$/DeadLetterQueue`)
- El caso de fraude no se crea, pero la transaccion queda registrada en PostgreSQL
- Un administrador puede revisar los mensajes DLQ manualmente via Azure Portal o CLI

**Garantia:** Ningun mensaje se pierde silenciosamente. Los fallos se investigan via DLQ.

## Escenario 4: Scoring produce resultado

**Flujo:** ScoringService → CaseRepository → Topic `casos-fraude`

1. Scoring evalua la transaccion con las 4 reglas
2. Si score >= umbral (60), crea `FraudCase` en PostgreSQL
3. Publica evento `CASE_OPENED` en topic `casos-fraude`
4. Subscription `gestion-casos` recibe el evento para gestion

**Garantia:** El caso se persiste ANTES de publicar el evento. Si la publicacion falla, el caso existe pero no se notifica (mejor que perder el caso).

## Configuracion de Service Bus

| Propiedad | Valor | Justificacion |
|---|---|---|
| `maxDeliveryCount` | 10 | Balance entre reintentos y evitar poison messages |
| `lockDuration` | PT1M (1 min) | Tiempo suficiente para procesar un evento |
| `defaultMessageTimeToLive` | P14D (14 dias) | Mensajes en DLQ se conservan 2 semanas |
| `deadLetteringOnMessageExpiration` | true | Captura mensajes no procesados |
| `requiresSession` | false | No se requiere ordenamiento por sesion |

## Monitoreo

- **Application Insights** recibe metricas de latencia y errores
- **DLQ monitoring**: revisar `az servicebus queue show` periodicamente para detectar mensajes acumulados
- **Alertas**: configurar `az monitor metrics alert` para DLQ message count > 0
