# Documento de Decisiones de Arquitectura - Centinela

## Resumen Ejecutivo

Este documento registra las decisiones de arquitectura tomadas durante el desarrollo del sistema Centinela, motor de detección de fraude transaccional en tiempo real.

## Semana 1: Fundamentos

### Decisión 1: Región de Despliegue

**Decisión**: East US (Virginia)

**Justificación**:
- Disponibilidad completa de todos los servicios requeridos
- Costos competitivos para desarrollo
- Baja latencia para usuarios en América Latina
- Soporte completo para Python y Azure Functions

### Decisión 2: Convención de Nombres

**Decisión**: Formato `[proyecto]-[tipo]-[secuencia]`

**Justificación**:
- Permite identificación rápida de recursos
- Escalable para múltiples instancias
- Cumple con restricciones de Azure para nombres globalmente únicos

### Decisión 3: Topología de Red

**Decisión**: Tres subredes (app, data, functions)

**Justificación**:
- Separación de responsabilidades
- Aislamiento de la capa de datos
- Cumplimiento del requisito de no acceso desde Internet

### Decisión 4: Nivel de Servicio API

**Decisión**: App Service B1 (Basic)

**Justificación**:
- Nivel mínimo que soporta integración VNet
- Costo estimado: ~$13/mes
- Suficiente para volumen de desarrollo

### Decisión 5: Tipo de Almacenamiento

**Decisión**: StorageV2, Standard_LRS, Hot tier

**Justificación**:
- Standard_LRS: Más económico, suficiente para desarrollo
- Hot tier: Acceso frecuente durante desarrollo
- StorageV2: Últimas funcionalidades

## Semana 2: Motor de Scoring

### Decisión 6: Clave de Partición Cosmos DB

**Decisión**: `accountId` como clave de partición

**Justificación**:
- Optimiza la consulta dominante: "transacciones recientes de una cuenta"
- Permite escalado horizontal
- Sacrifica consultas por rangos de tiempo globales (no requeridas)

### Decisión 7: Nivel de Consistencia Cosmos DB

**Decisión**: Session

**Justificación**:
- Balance entre consistencia y latencia
- Suficiente para el caso de uso (no requiere consistencia fuerte)
- Mejor rendimiento que Strong/Bounded Staleness

### Decisión 8: Umbral de Score

**Decisión**: 60 puntos

**Justificación**:
- Balance entre falsos positivos y fraude detectado
- Permite detectar transacciones con 2-3 reglas activadas
- Ajustable sin redespliegue

### Decisión 9: Mecanismo de Mensajería

**Decisión**: Azure Service Bus (colas)

**Justificación**:
- Garantiza procesamiento al menos una vez
- Soporte para colas con persistencia
- Integración nativa con Azure Functions

## Semana 3: Producción

### Decisión 10: Plataforma CI/CD

**Decisión**: GitHub Actions

**Justificación**:
- Integración nativa con GitHub
- Sin costo para repositorios públicos
- Fácil configuración con templates de Azure

### Decisión 11: Métrica de Escalado

**Decisión**: Profundidad de cola (Service Bus)

**Justificación**:
- Directamente relacionada con la carga de trabajo
- Permite escalar proactivamente
- Más relevante que CPU o memoria

### Decisión 12: Explicador

**Decisión**: Plantilla determinista (sin IA)

**Justificación**:
- Requisito del proyecto
- Sin costo adicional
- Resultados consistentes y predecibles

## Costos Estimados

| Servicio | Costo Mensual Estimado |
|----------|------------------------|
| App Service B1 | $13 |
| Cosmos DB (400 RU) | $24 |
| Storage | $5 |
| Service Bus | $10 |
| Azure Functions | $0 (consumption) |
| **Total** | **~$52/mes** |

**Presupuesto disponible**: $200 (30 días)
**Costo estimado 21 días**: ~$36

## Modificaciones Sugeridas

Si se reiniciara el proyecto:

1. **Usar Private Endpoints** en lugar de Service Endpoints para mayor seguridad
2. **Implementar Application Insights** desde el inicio para observabilidad
3. **Usar Azure Container Apps** en lugar de App Service para mejor escalado
4. **Implementar pruebas de carga** más temprano para validar escalabilidad

## Conclusiones

Las decisiones tomadas priorizan:
1. **Costo**: Mantener gasto dentro del presupuesto
2. **Simplicidad**: Soluciones que un equipo junior pueda entender
3. **Cumplimiento**: Requisitos del proyecto documentados
4. **Escalabilidad**: Base para crecimiento futuro

El sistema está diseñado para funcionar dentro de la suscripción gratuita, con margen para pruebas y desarrollo.
