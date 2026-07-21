# Technology Stack

Technologies selected and the rationale for each choice.

## Components

| Component | Technology | What It Does |
|-----------|-----------|--------------|
| **Ingestion API** | Spring Boot (Java 21) on Azure Container Apps (Consumption, HTTP-scaled) | Fast HTTP endpoint for transaction submission |
| **Serverless Engine** (Rule Engine) | Spring Boot (Java 21) on Azure Container Apps (Consumption, KEDA `azure-servicebus` scaler on `transactions-raw`) | Evaluates fraud rules (FR-1..FR-4), calculates risk scores, publishes `FraudEvaluationCompleted` |
| **Core Backend** | Spring Boot (Java 21) on Azure Container Apps (Consumption, HTTP-scaled) | Case management, alerts, reporting, auth, transaction query API |
| **OCR Worker** | FastAPI (Python 3.12) on Azure Container Apps (Consumption, KEDA `azure-servicebus` scaler on `documents-pending`) | Document image processing via Azure AI Document Intelligence |
| **Frontend** | React + TypeScript (Vite) on Azure Static Web Apps | Analyst dashboard for case review and rule configuration |
| **Compute substrate** | Azure Container Apps (Consumption) | Serverless containers for all four backends — scale to zero, per-second billing, KEDA-driven event scaling |
| **Message Broker** | Azure Service Bus (Standard) | Async communication; **Standard tier** required for Topics (e.g., `case-events`). See ADR-003. |
| **Database** | Azure Database for PostgreSQL Flexible Server (B1ms) | Primary data store for **all** modules (transactions, cases, alerts, audit). Schema-per-module. See ADR-002. |
| **Object Storage** | Azure Blob Storage (LRS Hot, WORM policy) | Verification document blobs only (write-once-read-rarely). Metadata stays in PostgreSQL. See ADR-002. |
| **Secrets** | Azure Key Vault | Secure storage for credentials, API keys, connection strings |
| **Observability** | Azure Application Insights | Distributed tracing, metrics collection, log aggregation |
| **Infrastructure** | Terraform (AzureRM) | Declarative provisioning of all Azure resources |

## Version Requirements

| Tool | Minimum Version |
|------|----------------|
| Java | 21 (LTS) |
| Spring Boot | 3.4.x |
| Python | 3.12 |
| Node.js | 22 (LTS) |
| Terraform | 1.9+ |
| Docker | 27+ |
| Azure Container Apps | Consumption plan (any region with KEDA + azure-servicebus scaler; see `infrastructure/REGION-QUOTA-CHECK.md`) |

## Why Each Technology

### Azure Container Apps (Consumption)

All four backends run as fully managed serverless containers on one ACA Environment — KEDA built-in for HTTP/Service Bus scaling, scale to zero at idle, first 180k vCPU-seconds/month per subscription free. Full rationale, alternatives, and cost impact: [`../decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md`](../decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md).

### Spring Boot (Java 21)
- Team already knows Java and Spring ecosystem
- Mature support for Azure SDK, JPA, Security, and Cloud Stream
- Strong typing prevents class of runtime errors critical for fraud detection logic
- The same Spring Boot stack drives Ingestion API, Serverless Engine, and Core Backend — only the deployment unit and the inbound adapter differ; the hexagonal layout and event contracts are identical (`docs/architecture/03-hexagonal-architecture.md`).

### FastAPI (Python 3.12)
- Azure AI Document Intelligence SDK is Python-first with best API coverage
- Async-native, ideal for I/O-bound OCR operations
- Rapid development cycles for ML/AI integration

### Azure Database for PostgreSQL Flexible Server (B1ms)
Single engine for the entire system (B1ms with schema-per-module, auto-stop after 1h idle). Full rationale, partition strategy, and polyglot rejection: [`../decision-log/ADR-002-postgresql-only-db.md`](../decision-log/ADR-002-postgresql-only-db.md).

### Azure Service Bus (Standard tier)
- Required tier — Basic **does not** support Topics (`case-events` needs them)
- Standard base charge ≈ $10/month; first 13M ops/month free — fits inside the $60 budget
- KEDA's `azure-servicebus` scaler requires `Manage` policy on the connection string so KEDA can poll queue depth; the IaC under `infrastructure/modules/servicebus` provisions this.

See [`../decision-log/ADR-003-async-messaging-reliability.md`](../decision-log/ADR-003-async-messaging-reliability.md) for full tier rationale and reliability design.

### Azure Blob Storage (LRS Hot, WORM policy)
- Holds verification document **blobs only**
- Metadata (`case_id`, `blob_url`, `status`, `extracted_data`) lives in PostgreSQL
- WORM policy to satisfy the audit-by-design requirement
