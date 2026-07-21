# ADR-009: Compute Substrate — Azure Container Apps Consumption + Static Web Apps Free

## Status

**ACCEPTED** (Sprint 0, 2026-07-17) — Ratified during Sprint 0 ADR review ([#16](https://github.com/Team-Centinela/Centinela-Code/issues/16)). Tracker [#48](https://github.com/Team-Centinela/Centinela-Code/issues/48) closed.

## Context

The platform runs four backend services (Ingestion API, Serverless Engine, Core Backend, OCR Worker) and one frontend (React + Vite SPA). Each needs a compute runtime. ASSIGNMENT.md §1.6 mandates cost-efficient cloud architecture under $60 over 21 days. ASSIGNMENT.md §3 (Out of Scope) explicitly forbids managed cluster orchestrators (AKS / Kubernetes). ASSIGNMENT.md §T.4 (Architectural Justification) requires every technical choice to be documented with cost and trade-off reasoning.

Decision drivers:

1. **Budget**: $60 hard ceiling. Compute must be near-zero for idle/burst workloads. No monthly base charges that burn budget without traffic.
2. **Scale to zero**: Fraud detection is bursty — hours of no transactions followed by a spike. Compute must not charge for idle replicas.
3. **KEDA integration**: The Serverless Engine and OCR Worker scale on Azure Service Bus queue depth via KEDA. The runtime must support event-driven scaling natively.
4. **Spring Boot compatibility**: Three services run on Spring Boot (Java 21). The runtime must run standard container images without framework adaptation.
5. **Operational simplicity**: 4-person team, 21 days, no SRE. No cluster control plane to manage, patch, or monitor.

## Decision

### 9.1 All four backends → Azure Container Apps (Consumption plan)

All four backend services run on a single Azure Container Apps Environment using the **Consumption plan**:

| Backend | Scaler | Replica count |
|---------|--------|---------------|
| **Ingestion API** | HTTP (concurrent requests) | 0–N, scales on incoming HTTP traffic |
| **Serverless Engine** (Rule Engine) | KEDA `azure-servicebus` on `transactions-raw` queue depth | 0–N, scales on pending messages |
| **Core Backend** | HTTP (concurrent requests) | 0–N, scales on incoming HTTP traffic |
| **OCR Worker** | KEDA `azure-servicebus` on `documents-pending` queue depth | 0–N, scales on pending messages |

One Azure Container Apps Environment hosts all four backends together, sharing the same observability wiring (Application Insights) and the same VNet (none configured currently; empty VNet added later for private endpoints if budget permits).

### 9.2 Frontend → Azure Static Web Apps (Free plan)

The React + Vite SPA frontend is hosted on Azure Static Web Apps **Free plan**:

| Property | Detail |
|----------|--------|
| **Hosting cost** | $0 (Free plan) |
| **Bandwidth** | 100 GB/month included — sufficient for 21-day demo traffic |
| **Custom domains** | 2 per app — covers `<app>.azurestaticapps.net` + one custom domain |
| **SSL** | Free, auto-renewed |
| **CI/CD** | GitHub-native — deploy on push to `main` |
| **SLA** | None — acceptable for 21-day project |

### 9.3 Cost impact

| Component | Plan | Est. 21-day cost |
|-----------|------|------------------|
| Azure Container Apps (Consumption) — all 4 backends | Pay-per-second, first 180,000 vCPU-seconds + 360,000 GiB-seconds + 2M requests/subscription/month free | **~$0–5** |
| Azure Static Web Apps (Free) | Free | **$0** |
| **Compute subtotal** | | **~$0–5** |

The free grant covers the expected workload for a 21-day demo: intermittent HTTP traffic, a few thousand transaction evaluations, and occasional document OCR requests. See `infrastructure/README.md` for the full budget table.

## Consequences

### Positive

- **No cluster management** — Azure Container Apps is a fully managed serverless container service (PaaS), not AKS. No control plane to operate. No kubeconfig to manage. Complies with ASSIGNMENT.md §3 "Out of Scope" (managed cluster orchestrators are AKS, not ACA).
- **Scale to zero** — all four backends can scale to zero replicas when idle. Cost drops to $0 when no traffic or queue messages exist.
- **KEDA built in** — the KEDA `azure-servicebus` scaler is available as a first-party scaling rule (`az containerapp update --scale-rule-type azure-servicebus`). No custom KEDA YAML or operator management required.
- **Standard container images** — the same Docker images built for local development deploy to ACA unchanged. No adaptation or re-architecture needed.
- **Single environment** — one ACA Environment unifies observability, logging, and networking across all four backends. Application Insights correlation IDs cross all four services by default.
- **Frontend at $0** — Static Web Apps Free tier provides CDN-backed hosting, custom domains, SSL, and CI/CD integration at no cost. The frontend is a pure SPA (no server-side rendering), so runtime constraints do not apply.

### Negative

- **Cold start** — a replica scaled to zero takes ~1–2 s to start on first request. For HTTP-scaled services (Ingestion API, Core Backend), this means the first request after idle may experience latency. Acceptable for the 21-day demo budget. Mitigated by reserving a minimum 1 replica during business hours if needed.
- **No VNet integration on Consumption plan** — private endpoints require the Dedicated plan, which costs more. Not needed for the 21-day scope; all traffic is public with Key Vault for secrets.
- **Static Web Apps Free has no SLA** — if the frontend is unreachable, the site is simply unavailable until the platform recovers. Acceptable for a demo project.
- **Static Web Apps storage limit** — 250 MB per environment, 500 MB total per app on Free plan. The Vite build output is <10 MB; no issue.

### Mitigations

| Negative | Mitigation |
|----------|------------|
| Cold start on HTTP services | `az containerapp update --min-replicas 1` during active testing hours; revert to 0 for overnight idle |
| No VNet on Consumption | Not needed for 21-day scope; secrets via Key Vault + managed identity are sufficient |
| SWA Free no SLA | Acceptable risk; a `fallback` Static Web Apps Standard ($9/app/month) upgrade takes 5 minutes if demo availability is critical |

## Alternatives considered

| Alternative | Reason rejected |
|-------------|----------------|
| **AKS / managed Kubernetes** | Explicitly forbidden by ASSIGNMENT.md §3 "Out of Scope". Even a minimal 3-node cluster with node-level auto-scaling costs >$60/month before any workload runs. |
| **Azure Functions (Consumption plan)** for the Rule Engine | Cold starts on the Consumption plan hit p99 latency above the real-time fraud detection budget. Functions Premium would erase the cost advantage. Spring Boot does not run natively on Functions without a custom container, which defeats the purpose. |
| **Azure App Service (B1)** | No scale-to-zero. B1 Linux plan costs ~$13/month even when idle. No KEDA integration — scaling on Service Bus requires custom logic. |
| **Azure Container Instances** | No managed scaling; no KEDA integration. Each container group is an independent deployment — operating four backends requires external orchestration or a scheduler. Per-second billing but no idle-scaling to zero. |
| **Virtual Machine Scale Sets** | Full OS management, patching, monitoring burden. No scale-to-zero. Cost exceeds ACA consumption for bursty workloads. |
| **Standalone Static Web Hosting (Blob static website)** | No CI/CD integration, no custom domain SSL, no path-level routing configuration. SWA Free tier is strictly superior for an SPA at $0. |

## References

- `ASSIGNMENT.md` §1.6 — Cost-efficient cloud architecture
- `ASSIGNMENT.md` §3 — Out of scope (no managed clusters)
- `ASSIGNMENT.md` §T.4 — Architectural Justification (every choice must be cost-justified)
- `docs/architecture/06-technology-stack.md` — Components table + Azure Container Apps rationale
- `docs/architecture/01-overview.md` — System diagram showing ACA as compute substrate
- `docs/infrastructure/README.md` — Cost guardrails and per-resource table
- `ADR-001-modular-monolith-hexagonal.md` §Extracted services — references ACA for the three extracted services
- `ADR-003-async-messaging-reliability.md` §3.1 — KEDA `azure-servicebus` scaler
- `infrastructure/REGION-QUOTA-CHECK.md` — Day-1 region verification for ACA + SWA
- Issue [#48](https://github.com/Team-Centinela/Centinela-Code/issues/48) — ADR-009 tracker

## Status

**ACCEPTED** (Sprint 0, 2026-07-17) — Ratified during Sprint 0 ADR review ([#16](https://github.com/Team-Centinela/Centinela-Code/issues/16)). Tracker [#48](https://github.com/Team-Centinela/Centinela-Code/issues/48) closed. This ADR clusters with [#4](https://github.com/Team-Centinela/Centinela-Code/issues/4) (Infra epic) and [#51](https://github.com/Team-Centinela/Centinela-Code/issues/51) (Serverless Engine module registration).
