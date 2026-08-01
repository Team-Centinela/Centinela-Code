# Justificacion de Region - Centinela

## Region Seleccionada: Poland Central (polandcentral)

## Criterios de Evaluacion

### 1. Disponibilidad de Servicios Requeridos

| Servicio | Disponible en Poland Central | Nivel |
|---|---|---|
| Azure Database for PostgreSQL | Si | Flexible Server |
| Azure Service Bus | Si | Standard |
| Azure Key Vault | Si | Standard |
| Azure Blob Storage | Si | General Purpose v2 |
| Azure App Service | Si | Linux |
| Azure Container Registry | Si | Basic |
| Azure Cache for Redis | Si | Basic |
| Application Insights | Si | Standard |

Todos los servicios requeridos para las semanas 1, 2 y 3 estan disponibles.

### 2. Latencia

- Poland Central ofrece latencia baja para Europa del Este y Centro
- Distancia geografica optima para el equipo de desarrollo
- Tiempos de respuesta < 50ms para servicios internos de Azure

### 3. Costo

- Poland Central tiene costos competitivos dentro de la region europea
- Los precios de PostgreSQL Flexible Server y Service Bus son consistentes con otras regiones europeas
- El nivel gratuito de Cosmos DB (no utilizado) esta disponible

### 4. Cumplimiento y Residencia de Datos

- Poland Central cumple con GDPR (Reglamento General de Proteccion de Datos)
- Los datos se procesan y almacenan dentro de la Union Europea
- Cumple con requisitos de residencia de datos para servicios financieros

### 5. Servicios No Disponibles o Restringidos

- Azure AI Document Intelligence: Verificar disponibilidad en suscripcion gratuita
- Algunos servicios premium pueden no estar en nivel gratuito

## Decision Final

Poland Central fue seleccionada por:
1. Disponibilidad completa de servicios requeridos
2. Latencia aceptable para el mercado objetivo
3. Cumplimiento GDPR
4. Costos dentro del presupuesto

## Verificacion

Se verifico la disponibilidad de cada servicio consultando:
- Azure Products by Region: https://azure.microsoft.com/en-us/explore/featured-products
- Documentacion especifica de cada servicio
- Limites de suscripcion gratuita
