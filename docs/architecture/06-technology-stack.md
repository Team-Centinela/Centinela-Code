# Technology Stack

Technologies selected and the rationale for each choice.

## Components

| Component | Technology | What It Does |
|-----------|-----------|--------------|
| **Ingestion API** | Spring Boot (Java 21) | Fast HTTP endpoint for transaction submission |
| **Core Backend** | Spring Boot (Java 21) | Rule engine, case management, orchestration logic |
| **OCR Worker** | FastAPI (Python 3.12) | Document image processing via Azure AI Document Intelligence |
| **Frontend** | React + TypeScript (Vite) | Analyst dashboard for case review and rule configuration |
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

## Why Each Technology

### Spring Boot (Java 21)
- Team already knows Java and Spring ecosystem
- Mature support for Azure SDK, JPA, Security, and Cloud Stream
- Strong typing prevents class of runtime errors critical for fraud detection logic

### FastAPI (Python 3.12)
- Azure AI Document Intelligence SDK is Python-first with best API coverage
- Async-native, ideal for I/O-bound OCR operations
- Rapid development cycles for ML/AI integration

### Azure Database for PostgreSQL Flexible Server (B1ms)
- ACID compliance for transactional fraud data
- PostGIS extension enables geolocation-based fraud checks
- Hash partitioning by `accountId` resolves the "Get recent transactions for account X" access pattern
- `JSONB` columns store flexible rule evidence payload (`patterns/04-pipeline-pattern.md`)
- Automated backups, point-in-time restore, and high-availability options
- Single engine for the entire system (ADR-002 supersedes earlier polyglot proposals)

### Azure Service Bus (Standard tier)
- Required tier — Basic **does not** support Topics (`case-events` needs them)
- Native dead-letter queues, scheduled delivery, session support, and topic subscriptions
- Standard base charge ≈ $10/month; first 13M ops/month free — fits inside the $60 budget (ADR-003)

### Azure Blob Storage (LRS Hot, WORM policy)
- Holds verification document **blobs only**
- Metadata (`case_id`, `blob_url`, `status`, `extracted_data`) lives in PostgreSQL
- WORM policy to satisfy the audit-by-design requirement
