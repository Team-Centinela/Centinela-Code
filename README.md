# Centinela

**Motor de detección de fraude transaccional en tiempo real**

## Descripción

Centinela es un sistema que vigila el flujo de transacciones financieras y detecta, en tiempo real, cuáles son sospechosas. Cada vez que un cliente hace una compra, transferencia o retiro, la transacción entra a Centinela. El sistema la analiza contra un conjunto de reglas de riesgo, calcula un puntaje (score) y decide en milisegundos si es fraudulenta.

## Arquitectura

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Cliente   │────▶│     API     │────▶│  Cola Msgs  │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                    │
                           ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐
                    │ Cosmos DB   │     │   Scoring   │
                    └─────────────┘     └─────────────┘
                                              │
                                              ▼
                                       ┌─────────────┐
                                       │   Casos     │
                                       └─────────────┘
```

## Componentes

| Componente | Descripción | Tecnología |
|------------|-------------|------------|
| **API de Ingesta** | Recibe transacciones y las persiste | Python, FastAPI |
| **Motor de Scoring** | Analiza transacciones y calcula score | Azure Functions |
| **Explicador** | Genera explicaciones legibles | Python |
| **Almacenamiento** | Documentos de verificación | Azure Blob Storage |
| **Base de datos** | Transacciones y casos | Azure Cosmos DB |
| **Mensajería** | Comunicación asíncrona | Azure Service Bus |

## Requisitos

- Python 3.11+
- Azure CLI
- Cuenta de Azure con crédito

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/centinela.git
cd centinela
```

### 2. Ejecutar script de aprovisionamiento

```bash
chmod +x scripts/provision.sh
./scripts/provision.sh
```

### 3. Configurar la API

```bash
cd src/api
pip install -r requirements.txt
uvicorn main:app --reload
```

### 4. Probar la API

```bash
curl -X POST http://localhost:8000/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-01-15T14:30:00Z",
    "account_id": "ACC-123456",
    "amount": 150000,
    "currency": "COP",
    "type": "purchase",
    "location": {"latitude": 6.2442, "longitude": -75.5812},
    "merchant": {"id": "MERCH-001", "name": "Supermercado", "category": "grocery"}
  }'
```

## Reglas de Detección

| Regla | Criterio | Puntos |
|-------|----------|--------|
| **Velocidad** | Más de 3 transacciones en 5 minutos | 35 |
| **Monto atípico** | Monto > 10× promedio histórico | 30 |
| **Geo-imposible** | Distancia imposible en el tiempo transcurrido | 17 |
| **Comercio de riesgo** | Categoría en lista de riesgo | 20 |

**Umbral por defecto:** 60 puntos

## Estructura del Proyecto

```
centinela/
├── docs/                    # Documentación
├── scripts/                 # Scripts de infraestructura
│   ├── provision.sh        # Script de aprovisionamiento
│   └── shutdown.sh         # Script de apagado
├── src/                     # Código fuente
│   ├── api/                # API de ingesta
│   ├── scoring/            # Motor de scoring
│   └── explainer/          # Explicador de casos
└── tests/                   # Pruebas
```

## Apagar Recursos

Al final de cada jornada, ejecutar:

```bash
./scripts/shutdown.sh
```

Esto eliminará todos los recursos de Azure para ahorrar crédito.

## Documentación

- [Convención de Nombres](docs/01-naming-convention.md)
- [Contrato de Transacción](docs/02-transaction-contract.md)
- [Roles y Permisos](docs/03-roles-permissions.md)
- [Diagrama de Red](docs/04-network-diagram.md)
