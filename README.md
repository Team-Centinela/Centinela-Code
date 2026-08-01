# Centinela — Motor de Deteccion de Fraude Transaccional en Tiempo Real

## Arquitectura

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CENTINELA                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐           │
│  │   Frontend    │     │   Backend    │     │   Scoring    │           │
│  │   Next.js     │────▶│  Spring Boot │────▶│   Engine     │           │
│  │   React       │     │  Hexagonal   │     │  Serverless  │           │
│  └──────────────┘     └──────────────┘     └──────────────┘           │
│         │                    │                    │                     │
│         │                    │                    │                     │
│         ▼                    ▼                    ▼                     │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐           │
│  │  Azure CDN   │     │  Azure SQL   │     │  Cosmos DB   │           │
│  │  Front Door  │     │  PostgreSQL  │     │  NoSQL       │           │
│  └──────────────┘     └──────────────┘     └──────────────┘           │
│                                                                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐           │
│  │  Key Vault   │     │  Event Grid  │     │  Blob Storage│           │
│  │  Secretos    │     │  Mensajeria  │     │  Documentos  │           │
│  └──────────────┘     └──────────────┘     └──────────────┘           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Equipo

| Nombre | Rol | Responsabilidad |
|--------|-----|-----------------|
| Santiago G. | Tech Lead | Arquitectura, Infraestructura Azure |
| Sebastian | Backend Developer | Motor de scoring, Reglas de deteccion |
| Jeronimo | Backend Developer | API de ingesta, Dominio |
| Santiago S. | Frontend Developer | Dashboard de analista, UI/UX |
| Juan David | DevOps Engineer | CI/CD, Containers, Observabilidad |

## Stack Tecnologico

### Backend
- **Lenguaje:** Java 17
- **Framework:** Spring Boot 3.3.5
- **Arquitectura:** Hexagonal (puertos y adaptadores)
- **Base de datos:** PostgreSQL (relacional), Cosmos DB (NoSQL)
- **Cache:** Redis
- **Mensajeria:** Azure Service Bus / Event Grid

### Frontend
- **Framework:** Next.js 16
- **UI:** React 19, Tailwind CSS, shadcn/ui
- **Lenguaje:** TypeScript

### Azure
- **Region:** Poland Central
- **Recursos:**
  - App Service (B1) - Backend API
  - Cosmos DB (Serverless) - Transacciones
  - Azure SQL PostgreSQL - Casos de fraude
  - Blob Storage - Documentos de verificacion
  - Service Bus - Cola de transacciones
  - Event Grid - Publicacion de eventos
  - Key Vault - Secretos
  - Redis Cache - Cache de sesion
  - Container Registry - Imagenes Docker
  - Application Insights - Observabilidad

## Estructura del Proyecto

```
centinela/
├── centinela-backend/           # Backend Spring Boot
│   ├── src/main/java/com/centinela/
│   │   ├── shared/              # Componentes compartidos
│   │   │   ├── config/
│   │   │   ├── events/
│   │   │   └── exceptions/
│   │   ├── ingestion/           # Modulo de ingesta
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   └── infrastructure/
│   │   ├── scoring/             # Modulo de scoring
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   └── infrastructure/
│   │   ├── cases/               # Modulo de casos
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   └── infrastructure/
│   │   └── explanation/         # Modulo de explicacion
│   │       ├── domain/
│   │       ├── application/
│   │       └── infrastructure/
│   └── Dockerfile
├── centinela-frontend/          # Frontend Next.js
│   ├── app/
│   ├── components/
│   └── Dockerfile
├── provision.sh                 # Script de aprovisionamiento
├── shutdown.sh                  # Script de apagado
└── setup.sh                     # Script de configuracion local
```

## Arquitectura Hexagonal

El backend sigue el patron de arquitectura hexagonal (puertos y adaptadores):

### Dominio
- **Entidades:** Transaccion, ScoredTransaction, FraudCase
- **Puertos de entrada:** API REST, Event Listeners
- **Puertos de salida:** Repositories, Event Publishers

### Modulos
1. **Ingestion:** Recibe y valida transacciones
2. **Scoring:** Evalua transacciones contra reglas de fraude
3. **Cases:** Gestiona casos de fraude
4. **Explanation:** Genera explicaciones legibles

## Reglas de Deteccion

| Regla | Descripcion | Puntos |
|-------|-------------|--------|
| VELOCITY | Multiples transacciones en ventana corta | 35 |
| AMOUNT | Monto significativamente superior al historico | 30 |
| GEO_IMPOSSIBLE | Ubicaciones incompatibles en tiempo | 25 |
| MERCHANT_RISK | Comercio o categoria de riesgo | 20 |

**Umbral:** 60 puntos (configurable via Azure App Configuration)

## Contrato de Transaccion

```json
{
  "cuentaId": "string (requerido)",
  "monto": "number (requerido, min: 0.01)",
  "moneda": "string (default: USD)",
  "marcaTiempo": "long (epoch millis)",
  "ubicacionLat": "double (-90 a 90)",
  "ubicacionLon": "double (-180 a 180)",
  "comercioId": "string",
  "comercioCategoria": "string"
}
```

## Configuracion

### Variables de Entorno

| Variable | Descripcion | Default |
|----------|-------------|---------|
| SPRING_PROFILES_ACTIVE | Perfil de Spring | local |
| REDIS_HOST | Host de Redis | localhost |
| REDIS_PORT | Puerto de Redis | 6379 |
| KEYVAULT_URL | URL de Key Vault | - |
| COSMOS_ENDPOINT | Endpoint de Cosmos DB | - |
| COSMOS_KEY | Clave de Cosmos DB | - |
| SCORING_THRESHOLD | Umbral de scoring | 60 |

### Perfiles
- **local:** Desarrollo local con MySQL y datos en memoria
- **azure:** Produccion en Azure con todos los servicios

## Endpoints API

### Ingesta
- `POST /api/v1/transacciones` - Recibir transaccion
- `GET /api/v1/transacciones/{id}` - Obtener transaccion
- `GET /api/v1/transacciones/cuenta/{cuentaId}` - Transacciones por cuenta

### Scoring
- `GET /api/v1/scoring/cases` - Listar casos de fraude
- `GET /api/v1/scoring/cases/{caseId}` - Obtener caso

### Health
- `GET /api/v1/transacciones/health` - Health check ingesta
- `GET /api/v1/scoring/health` - Health check scoring
- `GET /actuator/health` - Health check general

## Despliegue

### Local
```bash
# Instalar dependencias
./setup.sh

# Ejecutar backend
cd centinela-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Ejecutar frontend
cd centinela-frontend
pnpm dev
```

### Azure
```bash
# Aprovisionar infraestructura
chmod +x provision.sh
./provision.sh

# Desplegar backend
cd centinela-backend
mvn clean package -DskipTests
az webapp deployment source config-zip \
    --name centinela-api-<suffix> \
    --resource-group centinela-rg \
    --src target/centinela-backend-1.0.0-SNAPSHOT.jar

# Apagar recursos al final del dia
./shutdown.sh
```

## Presupuesto

- **Credito total:** $200 USD (30 dias)
- **Objetivo:** <$60 USD
- **Region:** Poland Central (costo optimizado)

### Costo Estimado Diario
| Servicio | Costo/dia |
|----------|-----------|
| App Service B1 | ~$0.10 |
| Cosmos DB Serverless | ~$0.25 |
| PostgreSQL | ~$0.20 |
| Storage | ~$0.05 |
| Redis | ~$0.10 |
| **Total** | **~$0.70/dia** |

## Script de Apagado

Ejecutar al cierre de cada jornada:
```bash
./shutdown.sh
```

Esto detiene App Service y Redis Cache para evitar consumo innecesario de credito.

## Desarrollo

### Commits
- Santiago G.: Arquitectura, Scripts Azure
- Sebastian: Motor de scoring, Reglas
- Jeronimo: API de ingesta, Dominio
- Santiago S.: Frontend, Dashboard
- Juan David: CI/CD, Docker, Observabilidad

### Ramas
- `main` - Produccion
- `develop` - Desarrollo
- `feature/*` - Features
- `fix/*` - Fixes
