# Clasificacion de Componentes - Centinela

## Matriz de Componentes

| Componente | Servicio Azure | Modelo | Responsabilidad Celula | Responsabilidad Proveedor |
|---|---|---|---|---|
| **API de Ingesta** | App Service (B1) | PaaS | Codigo, configuracion, despliegue | Infraestructura, escalado automatico, mantenimiento |
| **Motor de Scoring** | App Service (B1) | PaaS | Codigo, reglas, configuracion | Infraestructura, ejecucion, escalado |
| **Base de Datos Transacciones** | PostgreSQL Flexible Server | PaaS | Esquema, queries, indices | Infraestructura, backups, disponibilidad |
| **Almacenamiento Documentos** | Blob Storage | PaaS | Configuracion, politicas | Infraestructura, redundancia, durabilidad |
| **Mensajeria** | Service Bus | PaaS | Colas, topics, suscripciones | Infraestructura, entrega de mensajes |
| **Cache** | Azure Cache for Redis | PaaS | Configuracion,Politicas de expiracion | Infraestructura, disponibilidad |
| **Secretos** | Key Vault | PaaS | Politicas de acceso, secrets | Infraestructura, HSM, disponibilidad |
| **Monitoreo** | Application Insights | SaaS | Alertas, dashboards | Recoleccion, analisis, retencion |
| **Contenedores** | Container Registry | PaaS | Imagenes, versiones | Almacenamiento, distribucion |
| **CI/CD** | Azure Pipelines | SaaS | Pipeline, stages, triggers | Ejecucion, agentes |
| **Frontend** | App Service (B1) | PaaS | Codigo, configuracion | Infraestructura, hosting |
| **Red** | Virtual Network | IaaS-PaaS | NSGs, subnets, reglas | Infraestructura de red |

## Modelo de Responsabilidad

### PaaS (Platform as a Service)
- **Celula**: Codigo, configuracion, despliegue, escalado manual
- **Proveedor**: Infraestructura, parches, disponibilidad, backups

### SaaS (Software as a Service)
- **Celula**: Configuracion, integracion, alertas
- **Proveedor**: Todo el stack de infraestructura y plataforma

### IaaS-PaaS (Hibrido)
- **Celula**: Configuracion de red, reglas de seguridad
- **Proveedor**: Infraestructura fisica, virtualizacion

## Flujo de Datos

```
Cliente -> App Service (API) -> Service Bus -> App Service (Scoring)
                         |                           |
                         v                           v
                    PostgreSQL              PostgreSQL (cases)
                         |
                         v
                    Blob Storage
```

## Justificacion de Modelos

1. **App Service sobre Container Instances**: App Service ofrece CI/CD integrado, auto-scaling, y nivel gratuito B1 suficiente para el proyecto

2. **PostgreSQL sobre Cosmos DB**: PostgreSQL cumple con el requisito de ADR-002, soporta transacciones ACID, y es mas economico para el volumen esperado

3. **Service Bus sobre Storage Queue**: Service Bus ofrece ordered delivery, dead-lettering, y topic/subscription que Storage Queue no proporciona

4. **Key Vault sobre variables de entorno**: Key Vault cumple con el requisito de ADR-003 de no tener secretos en codigo o repositorio
