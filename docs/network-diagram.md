# Diagrama de Red - Centinela

## Topologia de Red

```
                    Internet
                       |
                       v
                [NSG-APP]
              AllowHTTPS(443)
              AllowHTTP(80)
              DenyAll(*)
                       |
                       v
    ============================================
    |         Virtual Network 10.0.0.0/16      |
    |  ========================================|
    |  | App Subnet 10.0.1.0/24               ||
    |  | - App Service (API + Scoring)        ||
    |  | - Service Endpoint: Storage          ||
    |  | - Service Endpoint: Key Vault        ||
    |  ========================================|
    |           |                              |
    |           v                              |
    |  ========================================|
    |  | Data Subnet 10.0.2.0/24              ||
    |  | - PostgreSQL Flexible Server         ||
    |  | - Azure Cache for Redis              ||
    |  | - Service Endpoint: Storage          ||
    |  | - Service Endpoint: Service Bus      ||
    |  ========================================|
    |           |                              |
    |           v                              |
    |  ========================================|
    |  | AzureBastionSubnet 10.0.3.0/24       ||
    |  | - Azure Bastion (acceso admin)       ||
    |  ========================================|
    ============================================
```

## Reglas de Trafico

### NSG-APP (Subred de Aplicacion)

| Prioridad | Nombre | Origen | Destino | Puerto | Protocolo | Accion | Justificacion |
|---|---|---|---|---|---|---|---|
| 100 | AllowHTTPS | Internet | Any | 443 | TCP | Allow | Trafico HTTPS de clientes |
| 110 | AllowHTTP | Internet | Any | 80 | TCP | Allow | Redireccion a HTTPS |
| 4096 | DenyAllInbound | Any | Any | * | * | Deny | Denegar todo lo demas |

### NSG-DATA (Subred de Datos)

| Prioridad | Nombre | Origen | Destino | Puerto | Protocolo | Accion | Justificacion |
|---|---|---|---|---|---|---|---|
| 100 | AllowFromAppSubnet | 10.0.1.0/24 | Any | * | * | Allow | Trafico desde app |
| 4096 | DenyAllInbound | Any | Any | * | * | Deny | Denegar todo lo demas |

## Aislamiento de Datos

### Mecanismo: Service Endpoints + NSG

- **PostgreSQL**: Restringido a Data Subnet via Service Endpoint
- **Redis**: Restringido a Data Subnet via Service Endpoint
- **Storage**: Acceso delegado via Managed Identity + Service Endpoint
- **Key Vault**: Acceso delegado via Managed Identity

### Diferencia con Private Endpoints

| Caracteristica | Service Endpoints (actual) | Private Endpoints |
|---|---|---|
| Costo | Sin costo adicional | Costo por endpoint |
| Complejidad | Baja | Media |
| Aislamiento | A nivel de subred | A nivel de NIC |
| DNS | Publico con restriccion | Privado |

**Decision**: Service Endpoints es suficiente para el alcance del proyecto y no genera costo adicional.

## Prueba de Aislamiento

### Intento de acceso desde internet

```bash
# Desde una maquina fuera de la red
psql -h <postgres-server>.postgres.database.azure.com -U centinela_admin -d centinela_db

# Resultado esperado: Connection refused / Timeout
# La conexion es rechazada porque PostgreSQL solo acepta conexiones desde la Data Subnet
```

### Verificacion de Service Endpoints

```bash
# Verificar que la subred tiene el service endpoint configurado
az network vnet subnet show \
  --resource-group centinela-rg \
  --vnet-name centinela-vnet \
  --name data-subnet \
  --query "serviceEndpoints"
```
