# Chat - Sesión de Desarrollo Centinela
**Fecha**: 30 de Julio de 2026

---

## Contexto del Proyecto

El usuario está comenzando un curso de Azure nivel básico como programador principiante. El proyecto se llama **Centinela**: un motor de detección de fraude transaccional en tiempo real.

El proyecto tiene 3 semanas:
- **Semana 1**: Infraestructura, identidad, red, API de ingesta
- **Semana 2**: Motor de scoring serverless, almacenes de datos, mensajería
- **Semana 3**: CI/CD, contenedores, verificación documental, explicador, observabilidad

---

## Preguntas Iniciales

**P**: Tienes una cuenta de Azure gratuita ya creada?
**R**: La creé hace un momento

**P**: Qué lenguaje de programación prefieres para el backend?
**R**: Python

**P**: Tienes Azure CLI instalado?
**R**: Sí, está instalado

---

## Desarrollo

### 1. Verificación de Login

```bash
az login
```

Se completó exitosamente. Suscripción: `Azure subscription 1` (ID: `062f4300-aea0-4349-bb62-5fbd2e1c2a35`)

### 2. Scripts Creados

#### Script de Aprovisionamiento (`scripts/provision.sh`)
- Crea Resource Group
- VNet con 3 subredes (app, data, functions)
- Storage Account
- Key Vault
- Service Bus con colas
- Cosmos DB con base de datos
- App Service Plan + Web App
- Function App
- Identidades gestionadas
- Guarda secretos en Key Vault

#### Script de Apagado (`scripts/shutdown.sh`)
- Elimina todo el Resource Group para ahorrar crédito

#### Script de Verificación de Cuotas (`scripts/verify-quotas.sh`)
- Verifica suscripción, cores, servicios disponibles

#### Script de Alertas de Presupuesto (`scripts/setup-budget-alerts.sh`)
- Configura alertas al 50%, 70%, 90% del crédito

### 3. Documentación Generada

| Archivo | Contenido |
|---------|-----------|
| `docs/01-naming-convention.md` | Convención de nombres para recursos |
| `docs/02-transaction-contract.md` | Contrato JSON de transacción con 4 decisiones explícitas |
| `docs/03-roles-permissions.md` | Matriz de 4 roles: Analista, Admin, Servicio, Auditor |
| `docs/04-network-diagram.md` | Topología VNet con reglas de tráfico |
| `docs/05-architecture-decisions.md` | 12 decisiones de arquitectura documentadas |
| `docs/06-idempotency-strategy.md` | Estrategia contra transacciones duplicadas |

### 4. Código Fuente

#### API de Ingesta (`src/api/main.py`)
- Framework: FastAPI
- Endpoint: `POST /api/v1/transactions`
- Validación completa del contrato
- Marca de tiempo del servidor

#### Motor de Scoring (`src/scoring/engine.py`)
- 4 reglas: velocidad, monto atípico, geo-imposible, comercio de riesgo
- Umbral configurable (60 puntos por defecto)
- Fórmula de Haversine para distancias

#### Explicador (`src/explainer/explainer.py`)
- Genera texto legible para analistas
- Plantilla determinista (sin IA)

---

## Problemas Encontrados y Soluciones

### Problema 1: Proveedores de recursos no registrados
**Error**: `MissingSubscriptionRegistration`

**Solución**:
```bash
az provider register --namespace Microsoft.Storage
az provider register --namespace Microsoft.KeyVault
az provider register --namespace Microsoft.DocumentDB
az provider register --namespace Microsoft.ServiceBus
az provider register --namespace Microsoft.Web
az provider register --namespace Microsoft.Compute
az provider register --namespace Microsoft.OperationalInsights
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.ContainerInstance
```

### Problema 2: Alta demanda de Cosmos DB en East US
**Error**: `ServiceUnavailable` - alta demanda

**Solución**: Crear en East US 2 con nombre sin guiones
```bash
az cosmosdb create --name cosmoscentinela001 --locations regionName=eastus2
```

### Problema 3: Sin cuota de VMs para App Service
**Error**: `Additional quota` - Current Limit (Total VMs): 0

**Solución**: Usar Container Instances en lugar de App Service
```bash
az container create --name api-centinela --image mcr.microsoft.com/azuredocs/containerapps-helloworld:latest --os-type Linux --cpu 1 --memory 1.5
```

### Problema 4: Container Apps falló (AKS heavy usage)
**Error**: `AKSCapacityHeavyUsage` en East US y Central US

**Solución**: No se pudo resolver, se usó Container Instances como alternativa

### Problema 5: Azure Functions Linux Consumption no disponible
**Error**: `Linux dynamic workers are not available`

**Solución**: No se pudo resolver con la configuración actual

---

## Estado Actual de Recursos

### Recursos Creados ✅

| Recurso | Nombre | Región | Estado |
|---------|--------|--------|--------|
| Resource Group | rg-centinela-001 | East US | Activo |
| VNet | vnet-centinela-001 | East US | Activo |
| Storage Account | stcentinela001 | East US | Activo |
| Key Vault | kv-centinela-001 | East US | Activo |
| Service Bus | sb-centinela-001 | East US | Activo |
| Cosmos DB | cosmoscentinela001 | East US 2 | Activo |
| Container Instance | api-centinela | East US | Corriendo |

### Recursos No Creados ❌

| Recurso | Razón |
|---------|-------|
| App Service Plan | Sin cuota VMs |
| Web App | Sin cuota VMs |
| Function App | Linux Consumption no disponible |
| Container Apps | AKS con alta demanda |

---

## Archivos del Proyecto

```
centinela/
├── README.md
├── .gitignore
├── docs/
│   ├── 01-naming-convention.md
│   ├── 02-transaction-contract.md
│   ├── 03-roles-permissions.md
│   ├── 04-network-diagram.md
│   ├── 05-architecture-decisions.md
│   └── 06-idempotency-strategy.md
├── scripts/
│   ├── provision.sh
│   ├── shutdown.sh
│   ├── deploy-api.sh
│   ├── setup-budget-alerts.sh
│   └── verify-quotas.sh
├── src/
│   ├── api/
│   │   ├── main.py
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   ├── scoring/
│   │   └── engine.py
│   └── explainer/
│       └── explainer.py
└── tests/
```

---

## Próximos Pasos

1. **Deploy de la API real** en Container Instance (reemplazar imagen de prueba)
2. **Configurar identidades gestionadas** para acceso a servicios
3. **Migrar secretos** a Key Vault
4. **Probar la API** con transacciones reales
5. **Implementar motor de scoring** como Container Instance separado
6. **Configurar monitoreo** con Application Insights

---

## Presupuesto

- **Crédito disponible**: $200 (suscripción gratuita)
- **Costo estimado hasta ahora**: ~$5-10
- **Costo estimado total (21 días)**: ~$36
- **Margen de error**: ~$164

---

## Notas Importantes

1. **Ejecutar `./scripts/shutdown.sh` al final de cada jornada** para ahorrar crédito
2. La región principal es **East US**, Cosmos DB está en **East US 2**
3. El nombre de Cosmos DB es `cosmoscentinela001` (sin guiones por restricción)
4. La Container Instance tiene IP pública pero puede estar bloqueada por firewalls corporativos
5. La suscripción es nueva y tiene limitaciones de cuota en varias regiones
