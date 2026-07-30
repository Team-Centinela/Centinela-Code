# Convención de Nombres - Centinela

## Formato General

```
[proyecto]-[tipo-recurso]-[secuencia]
```

Donde:
- **proyecto**: `centinela` (en minúsculas, sin espacios ni guiones)
- **tipo-recurso**: Código abreviado del tipo de recurso
- **secuencia**: Número de 3 dígitos (001, 002, ...)

## Tabla de Recursos

| Tipo de Recurso | Código | Ejemplo |
|----------------|--------|---------|
| Grupo de recursos | `rg` | `rg-centinela-001` |
| Red Virtual | `vnet` | `vnet-centinela-001` |
| Subnet | `snet` | `snet-app` |
| Almacenamiento | `st` | `stcentinela001` |
| Key Vault | `kv` | `kv-centinela-001` |
| App Service Plan | `asp` | `asp-centinela-001` |
| Web App | `api` | `api-centinela-001` |
| Function App | `func` | `func-scoring-001` |
| Cosmos DB | `cosmos` | `cosmos-centinela-001` |
| Service Bus | `sb` | `sb-centinela-001` |
| Contenedor Storage | `cnt` | `cnt-documents` |
| Cola Service Bus | `q` | `q-transactions` |

## Reglas de Unicidad

Los siguientes recursos requieren nombres **globalmente únicos** en Azure:

1. **Almacenamiento** (st): Máximo 24 caracteres, solo minúsculas y números
2. **Key Vault** (kv): 3-24 caracteres, solo letras, números y guiones
3. **Cosmos DB** (cosmos): Solo letras minúsculas, números y guiones
4. **Service Bus** (sb): Solo letras minúsculas, números y guiones
5. **Web App** (api): Solo letras, números y guiones
6. **Function App** (func): Solo letras y números

## Ejemplos Completos

```
rg-centinela-001          # Grupo de recursos
vnet-centinela-001        # Red Virtual
snet-app                  # Subred de aplicación
snet-data                 # Subred de datos
snet-functions            # Subred de Function Apps
stcentinela001            # Almacenamiento
kv-centinela-001          # Key Vault
asp-centinela-001         # App Service Plan
api-centinela-001         # API de ingesta
func-scoring-001          # Motor de scoring
cosmos-centinela-001      # Cosmos DB
sb-centinela-001          # Service Bus
cnt-documents             # Contenedor de documentos
q-transactions            # Cola de transacciones
q-cases                   # Cola de casos
```

## Justificación

- **Prefijo del proyecto**: Permite identificar fácilmente a qué proyecto pertenece cada recurso
- **Código de tipo**: Facilita la identificación del tipo de recurso
- **Secuencia numérica**: Permite múltiples instancias del mismo tipo
- **Sin guiones en almacenamiento**: Requisito de Azure para nombres de Storage Account
