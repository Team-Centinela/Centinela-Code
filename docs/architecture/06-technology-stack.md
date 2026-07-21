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

- All four backends run as **fully managed serverless containers** — no AKS / Kubernetes control plane to operate. ASSIGNED scope (`ASSIGNMENT.md` §3 *Out of Scope*) explicitly forbids managed cluster orchestrators; Azure Container Apps is a different (PaaS) category.
- Each backend's replica count scales:
  - **HTTP**: on concurrent inbound request count for the Ingestion API and Core Backend REST endpoints.
  - **KEDA `azure-servicebus`**: on the queue/topic-subscription active-message count for the Serverless Engine (`transactions-raw`) and the OCR Worker (`documents-pending`).
- Scales to **zero replicas** when there is no traffic; the first 180,000 vCPU-seconds, 360,000 GiB-seconds, and 2 M requests / month per subscription are free, keeping compute spend effectively nil for bursty workloads inside the $60 budget.
- Built-in KEDA scalers (no YAML of our own beyond the `az containerapp update ... --scale-rule-type azure-servicebus` form) make cold-start recovery an Azure-managed concern.
- One Azure Container Apps **Environment** hosts the four backends together, sharing the same VNet (none currently used) and unified observability wiring through Application Insights.
- See `../decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md` for the full decision record, alternatives analysis, and cost comparison.

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
- ACID compliance for transactional fraud data
- PostGIS extension enables geolocation-based fraud checks
- Hash partitioning by `accountId` resolves the "Get recent transactions for account X" access pattern
- `JSONB` columns store flexible rule evidence payload (`../patterns/04-pipeline-pattern.md`)
- Automated backups, point-in-time restore, and high-availability options
- Single engine for the entire system (ADR-002 supersedes earlier polyglot proposals)

### Azure Service Bus (Standard tier)
- Required tier — Basic **does not** support Topics (`case-events` needs them)
- Native dead-letter queues, scheduled delivery, session support, and topic subscriptions
- Standard base charge ≈ $10/month; first 13M ops/month free — fits inside the $60 budget (ADR-003)
- KEDA's `azure-servicebus` scaler requires `Manage` policy on the connection string so KEDA can poll queue depth; the IaC under `infrastructure/modules/servicebus` provisions this.

### Azure Blob Storage (LRS Hot, WORM policy)
- Holds verification document **blobs only**
- Metadata (`case_id`, `blob_url`, `status`, `extracted_data`) lives in PostgreSQL
- WORM policy to satisfy the audit-by-design requirement
