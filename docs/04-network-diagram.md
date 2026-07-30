# Diagrama de Red - Centinela

## Topología de Red

```
                    Internet
                        │
                        ▼
            ┌───────────────────────┐
            │   Azure Firewall /    │
            │   NSG (Internet)      │
            └───────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────┐
│                    VNet: 10.0.0.0/16                   │
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │            snet-app: 10.0.1.0/24                │  │
│  │  - API de Ingesta (Web App)                     │  │
│  │  - Function App (Scoring)                       │  │
│  └─────────────────────────────────────────────────┘  │
│                        │                              │
│                        ▼                              │
│  ┌─────────────────────────────────────────────────┐  │
│  │            snet-data: 10.0.2.0/24               │  │
│  │  - Cosmos DB                                    │  │
│  │  - Storage Account                              │  │
│  │  - Key Vault                                    │  │
│  │  - Service Bus                                  │  │
│  └─────────────────────────────────────────────────┘  │
│                                                       │
└───────────────────────────────────────────────────────┘
```

## Tabla de Reglas de Tráfico

| # | Origen | Destino | Puerto | Protocolo | Dirección | Justificación |
|---|--------|---------|--------|-----------|-----------|---------------|
| 1 | Internet | snet-app (Web App) | 443 | TCP | Entrante | Acceso HTTPS a la API de ingesta |
| 2 | snet-app | snet-data (Cosmos DB) | 443 | TCP | Saliente | Consultas a la base de datos |
| 3 | snet-app | snet-data (Storage) | 443 | TCP | Saliente | Almacenar documentos |
| 4 | snet-app | snet-data (Service Bus) | 443 | TCP | Saliente | Publicar eventos de transacción |
| 5 | snet-app | snet-data (Key Vault) | 443 | TCP | Saliente | Obtener secretos |
| 6 | snet-app | snet-functions | 443 | TCP | Saliente | Invocar Function App |
| 7 | snet-functions | snet-data | 443 | TCP | Saliente | Acceso a datos |

## Subredes

### snet-app (10.0.1.0/24)
- **Propósito**: Albergar la API de ingesta y el motor de scoring
- **Tamaño**: 254 direcciones IP
- **Componentes**: Web App, Function App

### snet-data (10.0.2.0/24)
- **Propósito**: Almacenar datos y servicios de mensajería
- **Tamaño**: 254 direcciones IP
- **Componentes**: Cosmos DB, Storage, Key Vault, Service Bus
- **Restricción**: No accesible desde Internet

### snet-functions (10.0.3.0/24)
- **Propósito**: Integración de la Function App con la VNet
- **Tamaño**: 254 direcciones IP
- **Requisito**: Mínimo 32 direcciones para integración VNet

## NSG (Network Security Group)

### Reglas de Entrada

| Prioridad | Nombre | Origen | Destino | Puerto | Acción |
|-----------|--------|--------|---------|--------|--------|
| 100 | AllowHTTPS | Internet | snet-app | 443 | Allow |
| 200 | DenyAllInbound | * | * | * | Deny |

### Reglas de Salida

| Prioridad | Nombre | Origen | Destino | Puerto | Acción |
|-----------|--------|--------|---------|--------|--------|
| 100 | AllowHTTPSOut | snet-app | snet-data | 443 | Allow |
| 200 | DenyAllOutbound | * | * | * | Deny |

## Aislamiento de Datos

**Mecanismo**: Service Endpoints + Private Endpoints

### Service Endpoints (sin costo adicional)

```bash
# Habilitar Service Endpoint para Cosmos DB
az network vnet subnet update \
    --name snet-data \
    --vnet-name vnet-centinela-001 \
    --resource-group rg-centinela-001 \
    --service-endpoints Microsoft.DocumentDB

# Habilitar Service Endpoint para Storage
az network vnet subnet update \
    --name snet-data \
    --vnet-name vnet-centinela-001 \
    --resource-group rg-centinela-001 \
    --service-endpoints Microsoft.Storage

# Habilitar Service Endpoint para Service Bus
az network vnet subnet update \
    --name snet-data \
    --vnet-name vnet-centinela-001 \
    --resource-group rg-centinela-001 \
    --service-endpoints Microsoft.ServiceBus
```

### Diferencia entre Service Endpoints y Private Endpoints

| Característica | Service Endpoints | Private Endpoints |
|----------------|-------------------|-------------------|
| **Costo** | Sin costo adicional | Costo por endpoint |
| **Configuración** | Simple | Compleja |
| **Acceso** | Solo desde la VNet | Desde cualquier lugar |
| **Seguridad** | Buena | Excelente |
| **Uso recomendado** | Desarrollo/Pruebas | Producción |

**Decisión**: Usar Service Endpoints (sin costo) para el proyecto de desarrollo.

## Prueba de Aislamiento

Para demostrar que la capa de datos no es alcanzable desde Internet:

```bash
# Intentar acceder a Cosmos DB desde fuera de la VNet
curl https://cosmos-centinela-001.documents.azure.com:443/

# Resultado esperado: Timeout o Connection Refused
```

## Dimensionamiento para Escalado

| Subred | Rango | Direcciones | Justificación |
|--------|-------|-------------|---------------|
| snet-app | 10.0.1.0/24 | 254 | API + Function App |
| snet-data | 10.0.2.0/24 | 254 | Datos y servicios |
| snet-functions | 10.0.3.0/24 | 254 | Integración Function App |

**Nota**: La subred de integración de Function App requiere mínimo 32 direcciones. Se asignaron 254 para permitir escalado futuro.
