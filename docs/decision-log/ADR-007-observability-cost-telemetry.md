# ADR-007: Observability & Cost Telemetry — Application Insights, W3C TraceContext, Budget Alerts

## Context

`ASSIGNMENT.md` §1.5 mandates **End-to-End Observability**: "Provide complete visibility into the lifecycle of every transaction, showing logs, traces, and system metrics when failures occur." §1.6 mandates **Cost-Efficient Cloud Architecture**: "Deliver a fully functional system while keeping total Azure consumption under **$60 USD** out of the provided $200 budget."

The platform runs four backend services (Ingestion API, Serverless Engine, Core Backend, OCR Worker) and one frontend (SWA) on a single Azure Container Apps Environment. Distributed tracing across service boundaries, cost visibility into the $60 budget, and environment-appropriate log verbosity are all required within the 21-day window.

Decision drivers:

1. **Unified observability backbone** — one Application Insights workspace must correlate traces, logs, and metrics across all five deployments.
2. **Standards-based distributed tracing** — W3C TraceContext (`traceparent`, `tracestate`) is the only propagation format supported natively by Azure SDKs, Spring Cloud Sleuth / Micrometer Tracing, OpenTelemetry Python, and Application Insights.
3. **Cost telemetry as first-class metric** — the $60 budget is a hard constraint; we need real-time visibility into PostgreSQL auto-stop/start cycles, ACA replica counts, and Service Bus operation volume.
4. **Budget alerts at 50 / 80 / 90 %** — proactive guardrails prevent budget overrun; Azure Cost Management budget alerts are the native mechanism.
5. **Log levels per environment** — dev/test noise must not drown prod signals; log levels are configuration, not code.

## Decision

### 7.1 Observability Backbone: Azure Application Insights (Workspace-based)

| Property | Decision |
|---|---|
| Resource | Azure Monitor Application Insights (workspace-based, not classic) |
| Workspace | `centinela-log-analytics` (Log Analytics workspace) |
| Sampling | **Adaptive sampling OFF** — for the 21-day window with < 10k transactions/day, we ingest 100 %. Cost impact: ~$2 for 5 GB free tier + overage. |
| Retention | 30 days (default) — covers the project window + 9 days post-mortem |
| Connection string | Stored in Key Vault (`appinsights-connection-string`); injected via `APPLICATIONINSIGHTS_CONNECTION_STRING` env var on each ACA container |
| Instrumentation | **Zero-code + manual**: Spring Boot 3.4 + Micrometer Tracing (OTel bridge) auto-instruments HTTP, JDBC, Service Bus, Redis. FastAPI + `opentelemetry-instrument` auto-instruments HTTP, `azure-servicebus`. SWA uses client-side JS SDK (`@microsoft/applicationinsights-web`). |

**Why not Grafana Cloud / Datadog / self-hosted?** Cost. Application Insights workspace-based is included in the Azure subscription; first 5 GB/month free; no extra SaaS contract. Fits the $60 budget with headroom.

### 7.2 Distributed Tracing — W3C TraceContext Propagation

Every service boundary (HTTP ingress, Service Bus publish/consume, DB query) propagates `traceparent` and `tracestate` per W3C TraceContext spec.

| Boundary | Propagation Mechanism |
|---|---|
| **Client → Ingestion API (HTTP)** | Client sends `traceparent` header (or SDK generates). Spring `WebMvcTraceFilter` extracts/continues. |
| **Ingestion API → Service Bus (`transactions-raw` topic)** | Outbox publisher sets `message.applicationProperties["traceparent"] = currentSpan.traceparent()`; `tracestate` similarly. |
| **Service Bus → Serverless Engine (consumer)** | `SpringCloudStreamBinder` reads `traceparent` from message properties → `Tracer.currentSpanContext()` continues trace. |
| **Serverless Engine → Service Bus (`case-events` topic)** | Same as above — publisher injects `traceparent`/`tracestate` into outbound message properties. |
| **Service Bus → Core Backend (consumer)** | Same consumer-side extraction. |
| **Core Backend → Service Bus (`documents-pending`)** | Same publisher injection. |
| **Service Bus → OCR Worker (consumer)** | Python `azure-servicebus` receiver reads `message.application_properties["traceparent"]` → `trace.set_span_in_context`. |
| **OCR Worker → Document Intelligence (HTTP)** | `opentelemetry-instrument` injects `traceparent` on outbound HTTP. |
| **SWA (browser) → Core Backend (HTTP)** | `@microsoft/applicationinsights-web` sends `traceparent` header; Spring extracts. |

**Correlation ID** (`correlationId` in domain event envelope, `../architecture/04-event-driven-communication.md` §Message Contract) is **business-level**, distinct from `traceparent` (infrastructure-level). Both are logged. `correlationId` = `transactionId` for the ingestion saga; `traceparent` stitches the infra hops.

**Span naming convention** (for queryability in Log Analytics):

| Span Kind | Name Format | Example |
|---|---|---|
| SERVER (HTTP ingress) | `HTTP {METHOD} {route}` | `HTTP POST /api/v1/transactions` |
| CONSUMER (Service Bus) | `SB RECEIVE {queue/topic-sub}` | `SB RECEIVE transactions-raw/serverless-engine` |
| PRODUCER (Service Bus) | `SB SEND {queue/topic}` | `SB SEND case-events` |
| CLIENT (HTTP egress) | `HTTP {METHOD} {host}{path}` | `HTTP POST cognitiveservices.azure.com/documentintelligence` |
| INTERNAL (DB) | `DB {statement}` | `DB SELECT transactions WHERE account_id = ?` |

**Attributes attached to every span** (via `ObservationConvention` in Spring, `span.set_attribute` in Python):

| Attribute | Value |
|---|---|
| `service.name` | `ingestion-api`, `serverless-engine`, `core-backend`, `ocr-worker`, `frontend` |
| `service.namespace` | `centinela` |
| `deployment.environment` | `dev` (only env in 21-day window) |
| `azure.resource.id` | ACA container app resource ID (injected via `CONTAINER_APP_NAME` env var) |
| `span.kind` | per OpenTelemetry semconv |

### 7.3 Cost Telemetry — Custom Metrics in Application Insights

The following **custom metrics** (Micrometer `MeterFilter` + `Counter`/`Gauge` in Spring; `opentelemetry.metrics` Counter/Gauge in Python) are emitted to Application Insights **every 60 seconds**. They are the primary signals for the budget alerts in §7.4.

| Metric Name | Type | Dimensions | Source | Description |
|---|---|---|---|---|
| `centinela.postgres.state` | Gauge (0/1) | `server` | **Core Backend** (scheduled task `@Scheduled(fixedDelay=60000)`) | `1` = running, `0` = stopped. Queries `pg_is_in_recovery()` via admin connection; on exception → `0`. |
| `centinela.postgres.start_duration_seconds` | Timer | `server` | Core Backend (on transition 0→1) | Wall-clock seconds from `az postgres flexible-server start` API call to first successful `pg_is_in_recovery()=false`. |
| `centinela.postgres.stop_duration_seconds` | Timer | `server` | Core Backend (on transition 1→0) | Wall-clock seconds from `az postgres flexible-server stop` to API success. |
| `centinela.aca.replica_count` | Gauge | `container_app` (`ingestion-api`, `serverless-engine`, `core-backend`, `ocr-worker`) | **Each ACA container app** (Micrometer `Gauge` reading `ContainerAppReplicaCount` from Azure Resource Graph or `az containerapp replica list` via managed identity) | Current replica count. 0 = scaled to zero. |
| `centinela.aca.cpu_seconds` | Counter (cumulative) | `container_app` | Each ACA container app (Azure Monitor metric `CpuTime` scraped via `azure-monitor-metrics` exporter) | vCPU-seconds consumed. Correlates with ACA Consumption billing. |
| `centinela.aca.memory_gib_seconds` | Counter (cumulative) | `container_app` | Each ACA container app (Azure Monitor metric `MemoryWorkingSet`) | GiB-seconds consumed. |
| `centinela.servicebus.operations` | Counter | `entity` (`transactions-raw` topic + subscriptions, `documents-pending` queue, `case-events` topic + subscriptions), `operation` (`send`, `receive`, `peek`) | Each publisher/consumer (Micrometer `Counter` incremented on each SDK call) | Service Bus API operations. Standard tier first 13M/mo free; this tracks proximity. |
| `centinela.keyvault.operations` | Counter | `operation` (`get`, `list`, `set`) | Each service (Micrometer `Counter` on `SecretClient` calls) | Key Vault transactions. 25k/mo free. |
| `centinela.document_intelligence.pages` | Counter | `result` (`success`, `error`) | OCR Worker | Pages processed. F0 tier = 500 pages/mo free. |

**Implementation notes**:

- Spring services: `MeterFilter` adds common tags (`service.name`, `deployment.environment`). `@Scheduled` tasks in Core Backend poll Azure Resource Graph (`ResourceGraphClient`) for ACA replica counts and PostgreSQL power state — avoids each app needing `az` CLI.
- Python OCR Worker: `opentelemetry-sdk` `MeterProvider` → `azure.monitor.opentelemetry.exporter.AzureMonitorMetricExporter` → same workspace.
- All metrics are **standard Metric Explorer queries**; dashboards in `infrastructure/dashboards/cost-telemetry.json` (provisioned by Terraform `azurerm_monitor_workbook`).

### 7.4 Business Metrics — Custom Metrics in Application Insights

The following **business metrics** (from `../best-practices/04-logging-and-monitoring.md` §Custom Metrics) are emitted alongside the cost telemetry. They use the same Micrometer / OpenTelemetry pipeline and share the same dimensions (`service.name`, `deployment.environment`).

| Metric Name | Type | Dimensions | Source | Description |
|---|---|---|---|---|
| `centinela.transactions.received` | Counter | `source` (`ingestion-api`) | Ingestion API | Transactions accepted via `POST /transactions` |
| `centinela.transactions.evaluated` | Counter | `result` (`scored`, `case_created`, `stored_only`) | Serverless Engine | Transactions processed through rule pipeline |
| `centinela.fraud.cases.created` | Counter | `triggered_by` (`FR-1`, `FR-2`, `FR-3`, `FR-4`, `threshold`) | Core Backend (Case Module) | Fraud cases opened |
| `centinela.rule.triggered` | Counter | `rule_code` (`FR-1`..`FR-4`), `result` (`fired`, `not_fired`) | Serverless Engine | Per-rule trigger counts |
| `centinela.evaluation.duration_ms` | Timer | `stage` (`stage1`, `stage2`, `total`) | Serverless Engine | Rule pipeline latency per stage |
| `centinela.ingestion.latency_ms` | Timer | — | Ingestion API | End-to-end HTTP request latency |

**Implementation**: Spring `MeterFilter` adds common tags. Each metric is recorded in the respective service's domain handler (e.g., `ScoreTransactionUseCase` records `evaluation.duration_ms`).

### 7.5 Audit Log — Immutable State-Change Trail

Per `ASSIGNMENT.md` §E ("audit-by-design") and `../architecture/01-overview.md` §Operational Posture, every state change to a **Case** or **Alert** writes an `audit_log` row in the same PostgreSQL schema (`cases` / `alerts`).

| Column | Type | Description |
|---|---|---|
| `audit_id` | UUID | Primary key |
| `entity_type` | VARCHAR(20) | `CASE` or `ALERT` |
| `entity_id` | UUID | `case_id` or `alert_id` |
| `subject_user_id` | UUID | Analyst/service identity from `X-MS-CLIENT-PRINCIPAL` or `SERVICE` |
| `timestamp` | TIMESTAMPTZ | `NOW()` |
| `prev_status` | VARCHAR(50) | Previous status value |
| `new_status` | VARCHAR(50) | New status value |
| `correlation_id` | UUID | Business `correlationId` (= `transactionId` for ingestion saga) |
| `trace_id` | CHAR(32) | W3C `traceparent` trace-id (links to distributed trace) |
| `metadata` | JSONB | Optional context (e.g., `{"assigned_to": "analyst-uuid"}`) |

**Write path**: In the same ACID transaction as the status change (Outbox Pattern per ADR-003). No separate audit service.

**Read path**: Reporting Module queries `audit_log` for case timeline UI. Analyst role can read; Auditor role reads all.

### 7.6 Saga Correlation — Linking Traces to Business Flows

`../patterns/05-saga-pattern.md` line 66 requires distributed tracing for saga visibility. This ADR provides it via:

| Saga | Business `correlationId` | Infrastructure `traceparent` | Linkage |
|---|---|---|---|
| **Case Lifecycle Saga** | `transactionId` (from `TransactionReceived`) | Single trace spanning: Ingestion API → Service Bus → Serverless Engine → Service Bus → Core Backend (Case + Alert) | Each span carries `correlationId` as attribute; Log Analytics query `where correlationId == 'tx-123'` reconstructs full saga |
| **Document Verification Saga** | `documentId` (from `DocumentPending`) | Trace: Core Backend → Service Bus → OCR Worker → Document Intelligence → Service Bus → Core Backend | Same linkage via `correlationId` attribute |

**Query example** (Log Analytics / KQL):
```kusto
traces
| where customDimensions.correlationId == "tx-123456"
| project timestamp, serviceName = customDimensions["service.name"], spanName = name, correlationId = customDimensions.correlationId, message
| order by timestamp asc
```

This satisfies the saga visibility requirement without a separate orchestration layer.

### 7.7 Budget Alerts — Azure Cost Management Budget + Action Group

A single **Cost Management Budget** scoped to the resource group `rg-centinela-dev`:

| Threshold | Action | Recipients |
|---|---|---|
| **50 %** ($30) | Email + Teams webhook | Team alias `centinela-team@centinela.onmicrosoft.com` |
| **80 %** ($48) | Email + Teams webhook + **Azure Function** `centinela-budget-action` (stops non-critical ACA apps: sets `minReplicas=0` on `ocr-worker`, `serverless-engine`) | Team alias + on-call rotation |
| **90 %** ($54) | Email + Teams webhook + **Azure Function** `centinela-budget-action` (sets `minReplicas=0` on `ingestion-api`, `core-backend`; stops PostgreSQL Flexible Server via `az postgres flexible-server stop`) | Team alias + on-call rotation |
| **100 %** ($60) | Email + Teams webhook + **Azure Function** (deletes all ACA container apps, stops PostgreSQL, deletes Service Bus namespace — **nuclear option**) | Team alias + on-call rotation |

**Action Group**: `ag-centinela-budget` (Terraform `azurerm_monitor_action_group`). Contains:
- Email receiver: `centinela-team@centinela.onmicrosoft.com`
- Webhook receiver: Teams incoming webhook URL (stored in Key Vault `teams-webhook-url`)
- Function receiver: `centinela-budget-action` (Azure Function App on Consumption plan, separate from Centinela ACA Environment; ~$0.50/mo)

**Budget Terraform** (`infrastructure/modules/budget/main.tf`):
```hcl
resource "azurerm_consumption_budget_resource_group" "centinela" {
  name              = "centinela-21day-budget"
  resource_group_id = azurerm_resource_group.main.id
  amount            = 60
  time_grain        = "BillingMonth"
  start_date        = "2026-07-15"  # project start
  end_date          = "2026-08-04"  # project start + 21 days

  notification {
    threshold           = 50
    operator            = "GreaterThan"
    contact_emails      = ["centinela-team@centinela.onmicrosoft.com"]
    contact_roles       = ["Owner"]
    action_group_id     = azurerm_monitor_action_group.budget.id
  }
  notification {
    threshold           = 80
    operator            = "GreaterThan"
    contact_emails      = ["centinela-team@centinela.onmicrosoft.com"]
    contact_roles       = ["Owner"]
    action_group_id     = azurerm_monitor_action_group.budget.id
  }
  notification {
    threshold           = 90
    operator            = "GreaterThan"
    contact_emails      = ["centinela-team@centinela.onmicrosoft.com"]
    contact_roles       = ["Owner"]
    action_group_id     = azurerm_monitor_action_group.budget.id
  }
}
```

### 7.8 Log Levels Per Environment

Only one environment exists in the 21-day window (`dev`), but the configuration is environment-aware for future staging/prod promotion.

| Logger / Package | `dev` (current) | `staging` (future) | `prod` (future) |
|---|---|---|---|
| `com.centinela` (all modules) | `DEBUG` | `INFO` | `WARN` |
| `org.springframework.web` | `DEBUG` | `WARN` | `WARN` |
| `org.hibernate.SQL` | `DEBUG` (logs SQL) | `WARN` | `OFF` |
| `org.hibernate.type.descriptor.sql` | `TRACE` (bind params) | `OFF` | `OFF` |
| `com.azure.messaging.servicebus` | `INFO` | `WARN` | `WARN` |
| `azure.core` (Python SDK) | `INFO` | `WARNING` | `WARNING` |
| `opentelemetry` (Python) | `INFO` | `WARNING` | `WARNING` |
| ROOT (console) | `INFO` | `WARN` | `ERROR` |

**Implementation**: `logback-spring.xml` (Spring) and `logging.yml` (Python `structlog`) read `LOG_LEVEL_ROOT` and `LOG_LEVEL_COM_CENTINELA` from environment variables. Terraform sets these per-container-app via `env` block:
```hcl
env {
  name  = "LOG_LEVEL_COM_CENTINELA"
  value = var.environment == "dev" ? "DEBUG" : "INFO"
}
```

**Structured logging**: All services log JSON to stdout (ACA captures to Log Analytics). Fields: `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, `service.name`, `correlationId` (when available). `LogstashEncoder` (Spring) / `structlog.processors.JSONRenderer` (Python). No PII in logs (enforced by `LogSanitizer` filter in `../best-practices/04-logging-and-monitoring.md`).

## Consequences

### Positive

- **Single pane of glass** — Application Insights workspace correlates traces, logs, metrics, and cost metrics across all five deployments.
- **Standards-based tracing** — W3C TraceContext works out of the box with Spring Micrometer Tracing, OpenTelemetry Python, and SWA JS SDK. No proprietary headers.
- **Cost visibility as metrics** — PostgreSQL state, ACA replicas, Service Bus ops are first-class metrics, not after-the-fact billing reports.
- **Proactive budget enforcement** — 50/80/90% alerts with automated scaling-to-zero and DB stop prevent the $60 overrun without human intervention.
- **Environment-aware logging** — DEBUG in dev for fast iteration; leaner in staging/prod without code changes.

### Negative

- **Application Insights cost** — at 100 % sampling, estimated ~$2–3 for 21 days. Still within the ~$15–24 total budget (`infrastructure/README.md`).
- **Custom metrics cardinality** — `container_app` dimension (4 values) × 60 s emission = 5,760 data points/day/service. Well within Metric Explorer limits.
- **Budget action function** — separate Consumption Function App adds ~$0.50/mo. Acceptable.
- **Resource Graph polling** — Core Backend scheduled task calls Azure Resource Graph every 60 s. Adds ~1,440 calls/day; free tier allows 100k/mo.

### Mitigations

| Negative | Mitigation |
|---|---|
| App Insights ingestion cost | Adaptive sampling OFF only for 21-day window; can be toggled ON via feature flag if extended. |
| Resource Graph rate limits | Poll interval 60 s is far below 100 calls/min limit. |
| Budget function permissions | Function MI granted `Monitoring Metrics Publisher` + `Website Contributor` on RG only — least privilege. |
| Log volume in dev | Structured JSON + log level config keeps noise manageable; `LogSanitizer` strips PII. |

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Azure Monitor **Metrics only** (no App Insights) | No distributed tracing, no log correlation, no live metrics stream. |
| **Grafana Cloud** (free tier) | Requires separate account, Prometheus remote write config, Loki for logs — operational overhead for 3 weeks. |
| **OpenTelemetry Collector** sidecar on ACA | ACA Consumption does not support sidecars; would require Dedicated plan ($$$). |
| **Log Analytics workspace only** (no App Insights) | No auto-instrumentation, no application map, no transaction diagnostics. |
| Budget alerts **without automated actions** | Human response time > budget burn rate during spike; automated scale-to-zero is the safety net. |
| **Sampling at 10 %** | Loses visibility into rare fraud patterns; 21-day volume is low enough to ingest 100 %. |

## References

- `ASSIGNMENT.md` §1.5 (Observability), §1.6 (Cost-efficient), §3 (Budget)
- `../architecture/06-technology-stack.md` — Application Insights, Key Vault, ACA Consumption
- `../architecture/04-event-driven-communication.md` — Event envelope with `correlationId`
- `../patterns/05-saga-pattern.md` — Distributed tracing reference for saga visibility
- `../best-practices/04-logging-and-monitoring.md` — Structured logging, log sanitization, correlation ID propagation
- `ADR-003-async-messaging-reliability.md` — Service Bus message properties for trace propagation
- `ADR-009-compute-substrate-container-apps-static-web-apps.md` — ACA Environment, SWA
- `infrastructure/README.md` — Cost guardrails table, budget alarms
- `infrastructure/dashboards/cost-telemetry.json` — Workbook provisioned by Terraform
- W3C TraceContext: <https://www.w3.org/TR/trace-context/>
- OpenTelemetry Semantic Conventions: <https://github.com/open-telemetry/opentelemetry-specification/tree/main/semantic_conventions>
- Azure Cost Management Budgets: <https://learn.microsoft.com/azure/cost-management-billing/costs/tutorial-acm-create-budgets>
- Issues: [#4](https://github.com/Team-Centinela/Centinela-Code/issues/4) Observability epic, [#55–#58](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aobservability) Sprint 0 blockers

## Status

**ACCEPTED** (Sprint 0, 2026-07-20) — Ratified during Sprint 0 ADR review ([#16](https://github.com/Team-Centinela/Centinela-Code/issues/16)). Dependency on ADR-009 resolved; tracker [#21](https://github.com/Team-Centinela/Centinela-Code/issues/21) closed in same release cycle as [#48](https://github.com/Team-Centinela/Centinela-Code/issues/48).