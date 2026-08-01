# Convencion de Nombres - Centinela

## Formato General

Todos los recursos siguen el patron: `centinela-{tipo}-{sufijo}`

Donde:
- `centinela`: prefijo del proyecto
- `tipo`: abreviatura del tipo de recurso
- `sufijo`: identificador unico (solo para recursos que lo requieren)

## Tabla de Recursos

| Tipo de Recurso | Abreviatura | Ejemplo | Unicidad |
|---|---|---|---|
| Resource Group | `rg` | `centinela-rg` | Regional |
| Virtual Network | `vnet` | `centinela-vnet` | Regional |
| Subnet | `sub` | `app-subnet` | Dentro de VNet |
| NSG | `nsg` | `centinela-nsg-app` | Regional |
| Storage Account | `st` | `centinestorage{timestamp}` | Global |
| PostgreSQL Server | `pg` | `centinela-pg-{timestamp}` | Global |
| Key Vault | `kv` | `centinela-kv-{timestamp}` | Global |
| Service Bus Namespace | `sb` | `centinela-sb-{timestamp}` | Global |
| App Service Plan | `plan` | `centinela-plan` | Regional |
| Web App | `api` | `centinela-api-{timestamp}` | Global |
| Container Registry | `acr` | `centinelaacr{timestamp}` | Global |
| Redis Cache | `redis` | `centinela-redis` | Global |
| App Insights | `ai` | `centinela-ai` | Regional |

## Convenciones de Base de Datos

### PostgreSQL
- Servidor: `centinela-pg-{timestamp}`
- Base de datos: `centinela_db`
- Usuario admin: `centinela_admin`
- Usuario app: `centinela_app`

### Service Bus
- Namespace: `centinela-sb-{timestamp}`
- Cola ingestion: `transacciones-ingestion`
- Topic casos: `casos-fraude`
- Subscription: `gestion-casos`

## Recursos con Unicidad Global

Los recursos que requieren nombre unico global usan sufijo `$(date +%s)` para evitar colisiones:
- Storage Account
- PostgreSQL Server
- Key Vault
- Service Bus Namespace
- Container Registry
- Web App

## Convenciones de Codigo

### Java (Backend)
- Paquetes: `com.centinela.{modulo}.{capa}`
- Clases: PascalCase (`IngestionController`)
- Variables: camelCase (`cuentaId`)
- Constantes: UPPER_SNAKE_CASE (`MAX_FILE_SIZE`)

### SQL
- Tablas: snake_case, plural (`transacciones`, `fraud_cases`)
- Columnas: snake_case (`marca_tiempo`, `cuenta_id`)
- Indices: `idx_{tabla}_{columna}`

### Docker
- Servicios: lowercase, hyphen-separated (`centinela-backend`)
- Imagenes: `{registry}/{servicio}:{tag}`
