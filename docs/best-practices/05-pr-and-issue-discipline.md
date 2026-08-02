# Best Practice 05 — PR & Issue Discipline (Traceability + Blocker Chain)

> Per ADR-010 + ADR-012. This document is the **operational companion** to those decisions — it shows how to apply ADR-010 day-to-day, the role matrix, the per-lane behaviour, and the template acceptance rules. The human-only action matrix that backs the role-and-responsibility split lives in ADR-012 §12.4 and is enforced by `opencode.json:permission` (§12.2).

## Role & Responsibility Matrix

Centinela runs **five lanes in parallel**, each with its own code surface and its own Azure surface over the same service. Two shared lanes (Lane E-platform) produce cross-cutting substrate that other lanes consume.

| Lane | Owner | Code surface | Azure surface (per-service) | Sprint N capacity |
|---|---|---|---|---|
| **A — ADR & SCRUM** | @SrLampi1001 | `docs/decision-log/`, `docs/architecture/`, `docs/AGENTS.md`, workstream decomposition | None (governance only) | ~12h/sprint |
| **B — Ingestion Service** | @3105jero | `services/ingestion/` domain, ports, use cases | `infrastructure/services/ingestion/` (App def, MI, env refs from Key Vault) | ~32h/sprint |
| **C — Serverless Engine + Engine plumbing** | @SebastianT2006 | `services/serverless-engine/` outbox, idempotency, messaging, infra surface | `infrastructure/services/serverless-engine/` (KEDA `azure-servicebus` scaler, MI, `transactions-raw` topic subscription `serverless-engine` binding) | ~36h/sprint |
| **D — Core Backend + Core Azure** | @JjuanGarcia77 | Modular monolith scaffolding: `cases`, `alerts`, `reporting`, `auth`, `admin`, queries | `infrastructure/services/core-backend/` (HTTP App def, MI, `case-events` topic subscription) | ~28h/sprint |
| **E — Platform + Frontend** | @Santiagodxz | `services/frontend/`, `services/shared-outbox/`, `services/shared-observability/`, `services/test-support/`, `infra/iac-platform/`, CI/CD | Shared `infrastructure/modules/platform/` (Service Bus, KEDA env, Key Vault, App Insights, ACA Env, Budget automation); `infrastructure/services/frontend/` (SWA) | ~30h/sprint |

### Lane-overlap rules

- **Shared modules** (outbox, observability, OpenAPI, CI, link-check) live in **Lane E** as Maven modules and are *consumed* by the other lanes — never re-implemented.
- **Per-service Azure parcels** for Ingestion, Engine, Core live in **Lane B / Lane C / Lane D** respectively.
- **`infrastructure/modules/platform/`** is the single source for Service Bus Standard, Key Vault, Log Analytics, ACA Environment, Budget automation. This is **shared IaC** built once by Lane E.
- **Cross-lane work** (e.g. wiring Ingestion outbox to Engine consumer) requires two paired issues: one in each lane, linked by `Companion infra issue:` and `Blocked by:` lines.

## PR Discipline Convention

### The four-field minimum on every code-side task issue

| Field | Always | Filled by | Enforced at |
|---|---|---|---|
| `Blocked by:` | Yes | Issue claimant (AI or human) | Template + agent preflight + PR template CI |
| `Has azure-impact:` (`yes\|no`) | Yes | Issue claimant | Template |
| `Companion infra issue:` (URL or `#—`) when `Has azure-impact: yes` | Conditional | Issue claimant | Template + PR template CI |
| `EXPECTED DELIVERY` + `COST-ATTRIBUTION` blocks when `Has azure-impact: yes` | Conditional | Issue + companion infra claimant | Template |

### The two-flag minimum on every code-side PR

- `backend` \| `frontend` \| `infra` \| `docs` — at most one
- `azure-impact: yes\|no` — explicit (matches the issue it closes)

If `azure-impact: yes`, the PR's body **must** carry the marker:

```
Companion infra issue: #B.A1
```

CI (the `matrices-build` job + a PR-template lint job at Gate-PR-template-2) **fails the PR** if this field is empty when `azure-impact: yes`.

### Branch model

```
main                  ← released code, on schedule
  ↑ squash merge (1 maintainer + CI green)
develop               ← always-green trunk, branch-pr-resolved
  ↑ merge (1 reviewer per lane + CI green)
feature/<lane>-<acronym>-<issue>  ← per-lane branch
```

- Each lane PR targets `develop`.
- Branch protection on `develop` requires **2 checks**: `link-check`, `matrices-build`.
- No squash/force-push unless explicitly requested by issue.

## Block-chain Examples (per-lane, real)

### Lane B chain (Ingestion — Sprint 1)

```
B.0 parent           (claimable after #E.0 + #B-existing state)
   ├── B.4  X-API-Key filter                         [in PR #120 already raw; pre-emption flagged in #131]
   │       Blocked by: #B.2 (closed), #B.3 (closed), #E.1 (open at sprint start)
   ├── B.5  Idempotency-Key filter
   │       Blocked by: #B.2, #B.3, #B.4
   ├── B.7  Global exception handler
   │       Blocked by: #B.4, #B.5
   ├── B.9  Outbox Publisher                          (companion #B.A1)
   │       Blocked by: #B.8 (closed), #B.2 (closed), #Maven-shared-outbox (Lane E)
   ├── B.11 traceparent injection
   │       Blocked by: #B.9, #E.1
   ├── B.12 Observability adoption                    (companion #B.A2)
   │       Blocked by: #E.1
   ├── B.14 OpenAPI  spec
   │       Blocked by: #B.4, #B.5, #B.7
   ├── B.15 Hexagonal ArchUnit                        (companion none)
   │       Blocked by: #E.1, #B.1 (closed)
   ├── B.16 residual domain tests
   └── B.A1..A3 companion infra

   Block on Lane C: B-finalized → C.consumer (engine) can ship messages.
                    (Lane C is blocked on B-finalized for E2E acceptance.)

   Block on Lane E: B.9 →  E.shared-outbox module name release
                   B.12 →  E.observability starter
                   B.15 →  E.ArchUnitBaseTest
                   B.14 →  E.OpenAPI consolidation (#107)
```

### Lane C chain (Engine — Sprint 1, already mostly done by Jero's PRs)

```
C.0 parent
   ├── C.X domain pure            (CLOSED via PRs #122 #123 #124 #125 #126)
   ├── C.X stage 1 + stage 2      (CLOSED via PR #123 #124)
   ├── C.X aggregator             (CLOSED via PR #125)
   ├── C.X JSONB persistence      (CLOSED via PR #126)
   ├── C.X ArchUnit               (CLOSED via PR #128)
   ├── C.91 KEDA scaler           (OPEN)        BLOCKED BY #127 (PR bootstrap)
   ├── C.92 Managed Identity      (OPEN)        BLOCKED BY #127 + #C.A.CosmosReady
   ├── C.98 consumer-side idempotency (OPEN)    BLOCKED BY #Maven-shared-outbox (E)
   ├── C.99 FraudEvaluationCompleted outbox  (OPEN)  BLOCKED BY #Maven-shared-outbox
   ├── C.100 traceparent        (OPEN)
   ├── C.101 metrics              (OPEN)
   ├── Companion infra: C.A1 (KEDA scaler + MI on transactions-raw/serverless-engine topic subscription)
                          C.A2 (App Insights DCE + env var)
                          C.A3 (Service Bus SDK pinning if not in #39)

   ADR-004 amendment: #133 (Haversine-vs-PostGIS) — pending decision
```

### Lane D chain (Core + Core Azure)

```
D.0 parent
   ├── D.X scaffold               (CLOSED via #55)
   ├── D.X cases domain first cut (OPEN)        ↓ depends on Lane E (Maven-shared-outbox + observability)
   ├── D.X alerts domain first cut (OPEN)
   ├── D.X reporting read role    (OPEN)        BLOCKED BY #63 (Platform PG)
   ├── D.X case-events topic subscriber (OPEN)   BLOCKED BY #C.99 + Lane E
   ├── D.A1..A3 companion per-service ACA App def, MI, bindings
                Blocked by: #127 (PR terraform bootstrap)
```

### Lane E chain (Platform + Frontend)

```
E.0 shared platform (one epic, ingested by every lane)
   ├── E.42 shared-outbox Maven module        (extract from #120 code)
   ├── E.43 shared-observability starter      (LogSanitizer + JSON encoder + traceparent)
   ├── E.44 OpenAPI 3.1 consolidation        (#107)
   ├── E.45 ArchUnitBaseTest                  (#115)
   ├── E.46 docker multi-arch build          (#105)
   ├── E.47 CI matrix                         (#104)
   ├── E.48 ADR-linkage PR-template check    (#108)
   ├── E.49 frontend scaffold activation
   ├── E.A1..A3 companion infra (Service Bus, PG, App Insights)
                Blocked by: #127 + first-wave Azure subscription apply
```

## Expected Delivery / Cost-Attribution — worked example

### `infra`-labelled companion issue #B.A1 (Ingestion — Outbox Publisher wiring)

```
TITLE:       [infra][B.A1] Ingestion: System-Assigned MI + ServiceBus Data Sender role on transactions-raw
TYPE:        infra-change
LANE:        B (consumed by C)
OWNER:       @3105jero
COMPANION:   #B.9

EXPECTED DELIVERY (Azure side)
  RESOURCE DIFF:
    azurerm_container_app.ingestion              +12/+0/0  identity=SystemAssigned
      azurerm_role_assignment.ingestion_sb_sender  +1/+0/0   sb_data_sender on transactions-raw topic (req. scope)
    azurerm_container_app.ingestion.secret       +1/+0/0   svcbus-conn-str (KV ref)

  VERIFICATION EVIDENCE:
    terraform apply log → in PR thread
    az containerapp show --name ingestion-app --query identity
    smoke test: POST /api/v1/transactions → 202 → message lands on SB — commit SHA <XXXXXXXX>
    checkov: 0 high findings

  DRIFT GAUGE:
    azure.ingestion.aca_replica_count (existing ADR-007 §7.7)
    azure.ingestion.sb_outbound_ops_count (NEW per this issue)

  BREAKAGE-PATH:
    - terraform plan fails                  → Lane E (platform) hand-back
    - role not propagated 30s              → re-run `az role assignment create ...`
    - cost > 20% lane ceiling              → this issue is source-of-record

COST-ATTRIBUTION
  Mode:        (a + b)
  Doc-row:
    | lane-b ingestion s1 | ACA App + SB Data Sender role + KV ref | $0.30–0.50 |
       | imp. | #B.A1 (companion #B.9) | start 2026-07-29 | $0.14 first run |

  Tags on resource(s):
    centinela:lp       lane-b
    centinela:epic     [B.0]
    centinela:issue    [B.A1]
    centinela:sprint   sprint-1
    centinela:start    2026-07-29
    centinela:close    <filled at close>
    centinela:action   create
```

## Related Documents

- ADR-010 — `../decision-log/ADR-010-issue-pr-discipline.md`
- ADR-012 — `../decision-log/ADR-012-opencode-execution-and-human-handoff.md` (§12.4 human-only action matrix; §12.5 handoff wording)
- `.github/ISSUE_TEMPLATE/task-managed.md`
- `.github/ISSUE_TEMPLATE/infra-change.md`
- `.github/ISSUE_TEMPLATE/ceremony.md`
- `docs/patterns/07-azure-impact-companion-issue.md`
- `/.github/agent-preflight.md`
