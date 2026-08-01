# Reporte de Cuotas - Centinela

## Suscripcion

| Recurso | Cuota Asignada | Consumo Actual | % Utilizado |
|---|---|---|---|
| vCPU (Linux) | 4 vCPU (Basic B1) | 2 vCPU (2 App Services) | 50% |
| Almacenamiento Managed Disks | 500 GB | < 1 GB | < 1% |
| IP Publicas | 5 (Basic) | 2 | 40% |
| Grupos de Recurso | 80 | 1 | 1.25% |
| Cuentas de Almacenamiento | 250 | 1 | 0.4% |
| Namespaces Service Bus | 1 | 1 | 100% |
| Flexible Servers PostgreSQL | 1 (Burstable B1ms) | 1 | 100% |
| App Services | 16 (Basic) | 2 | 12.5% |
| Container Registries | 1 | 1 | 100% |
| Application Insights | 1 | 1 | 100% |

## Costo Estimado Diario

| Recurso | SKU | Costo Estimado/Dia |
|---|---|---|
| App Service Backend (B1) | Basic | ~$0.075 |
| App Service Frontend (B1) | Basic | ~$0.075 |
| PostgreSQL (B1ms, 32GB) | Burstable | ~$0.17 |
| Azure Cache for Redis (C0) | Basic | ~$0.02 |
| Storage Account (LRS Hot) | Standard | ~$0.01 |
| Service Bus (Standard) | Standard | ~$0.05 |
| Key Vault | Standard | ~$0.01 |
| Application Insights | Basic | ~$0.01 |
| Container Registry (Basic) | Basic | ~$0.17 |
| **Total Estimado** | | **~$0.60/dia** |
| **Total 21 dias (presupuesto $60)** | | **~$12.60** |

## Notas

- El costo real puede variar segun el consumo de CPU y almacenamiento.
- El PostgreSQL consume la mayoria del costo fijo (~28%).
- Se recomienda pausar PostgreSQL con `shutdown.sh` fuera de horario laboral para ahorrar ~$0.15/dia.
- El B1 App Service no tiene SLA de disponibilidad (solo Basic). Para SLA 99.95% se necesita Standard o superior.
