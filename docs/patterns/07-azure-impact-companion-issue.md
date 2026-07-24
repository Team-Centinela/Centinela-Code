# Pattern 07 — Azure Impact Companion Issue

> Per ADR-010 §10.3–§10.5. The companion issue is the **code ↔ Azure handshake**: a code-side task and an infra-side task that ship in the same milestone, share a sprint, share the same epic, and meet the same sprint close.

## The pattern at a glance

```
   CODE SIDE                                  AZURE SIDE

   [B.9] Outbox Publisher                     [B.A1] Ingestion: System-Assigned MI
                                                + ServiceBus Data Sender role on
                                                transactions-raw
   type: task                                type: infra-change
   owner: @3105jero                          owner: @3105jero
   lane: B                                   lane: B
   parent: #B.0                              parent: #B.0
   labels: backend, lane-b, sprint-1         labels: infra, lane-b, sprint-1,
                                              companion-to-#B.9
   Has azure-impact: yes                     ────── matches ──────
   Companion infra issue: #B.A1              Companion in code: #B.9

   EXPECTED DELIVERY ........ (see §"Body shape")
   COST-ATTRIBUTION ........ (see §"Body shape")

   Blocked by: #E-shared-outbox (Lane E - Open)
                #B.2 (Flyway - Closed)
                #B.8 (Outbox JPA adapter - Closed)

                                               RESOURCE DIFF:
                                               VERIFICATION EVIDENCE:
                                               DRIFT GAUGE:
                                               BREAKAGE-PATH:

                                               COST-ATTRIBUTION:
```

Both issues share:

- The same `BLOCKED BY` chain (forward direction, code-blocked-by-infra-when-on-same-blocker-path).
- The same `centinela:*` tags on the Azure resources they touch (mode `b` of COST-ATTRIBUTION).
- The same `centinela:lp` set to the lane (`lane-b` in the example).
- The same close stamp; the code issue closes on merge, the infra issue closes only after `VERIFICATION EVIDENCE` is recorded.

## Why this pattern

1. **Back-trace forward**: every cost row in `infrastructure/README.md` points at one or more companion issues. Back-tracing from Azure to dollars ↔ dollars to issue ↔ issue to epic is a single grep.
2. **Forward-trace backward**: a code PR labelled `azure-impact` has a paired ticket for the Azure resource. The reviewer can open both side-by-side.
3. **Reviewer discipline**: A reviewer can sandbox terraform without touching code, side-by-side — each lane owns the parcel it understands best.
4. **Cost guardrails enforced**: each issue's COST-ATTRIBUTION block is the ledger cell for that work.

## Body shape (template)

### Code-side body (excerpt of `task-managed.md`)

```
HAS AZURE-IMPACT
  Yes — `<one-line summary of the deployment-surface touch>`

COMPANION INFRA ISSUE
  #B.A1   (paired issue, opened Day 1 of the claim, sibling issue)

EXPECTED DELIVERY (mirrored in companion)
  (high-level summary of what the Azure side must produce;
   fulfiller writes the resource diff in the companion)
```

### Infra-side body (`infra-change.md`)

```
COMPANION IN CODE
  #B.9   (the code-side task this issue fulfils)

EXPECTED DELIVERY (Azure side)

  RESOURCE DIFF
    azurerm_<...>.<logical_name>  +n/+m/d   <human>

  VERIFICATION EVIDENCE
    - terraform plan/apply output → in PR thread
    - az <show command> output    → in comment
    - smoke test ID + commit SHA  → in comment
    - checkov/tflint output       → 0 high findings needed

  DRIFT GAUGE
    <metric name>            DCE source detail
    <existing-or-new>

  BREAKAGE-PATH
    failure mode → hand-back path → owner lane

COST-ATTRIBUTION
  Mode: (a) / (b) / (a+b)

  Doc-row entry (mode a or a+b):
    | lane-b ingestion s1 | <resource-delta> | $<low>-<$high> |
       | imp. | #B.A1 (companion #B.9) | start <yyyy-mm-dd> | $X.YY |

  Tags (mode b or a+b):
    centinela:lp       lane-b
    centinela:epic     [B.0]
    centinela:issue    [B.A1]
    centinela:sprint   sprint-1
    centinela:start    <yyyy-mm-dd>
    centinela:close    <yyyy-mm-dd>
    centinela:action   create|update|delete
```

## Mode (a) vs mode (b) — when to choose which

| Mode | Choose when | Why |
|---|---|---|
| **a — doc-row only** | Lightweight, low-cost change (1 resource, 1 tag, 1 role). Sprint fast. | Doc-row audit at lane sprint-close catches drift; no Azure-side cost to a tag. |
| **b — tag-only** | Multiple resources across same service; high-volume tagging needed; cost-aggregates bleed across sprints | A single Cost Analysis slice answers "what did this lane do?" |
| **a + b (gold)** | Default for any companion issue that affects a Tier-1 resource (RG, Service Bus Standard, ACA Environment, Key Vault, App Insights) | Both humans (doc-row) and ops (tag) can read it |

## Worked examples

### Example 1: Ingestion Outbox wiring

See the example at the head of this file.

### Example 2: Engine KEDA scaler declaration

```
TITLE:       [infra][C.A1] Engine: KEDA azure-servicebus scaler declaration on transactions-raw
TYPE:        infra-change
OWNER:       @SebastianT2006
LANE:        C
COMPANION:   #C.91

EXPECTED DELIVERY (Azure side)

  RESOURCE DIFF:
    azurerm_container_app.serverless_engine.scale           +6/+0/0
      keda = { AzureServiceBus: { queueName: "transactions-raw" } }
      min_replicas = 0
      max_replicas = 6

  VERIFICATION EVIDENCE:
    terraform apply log → in PR thread
    az containerapp show --name serverless-engine --query "properties.scale"
    smoke test: post 30 events on transactions-raw → replicas scale to ≥ 2 within 30s
    commit SHA <XXXXXXXX>

  DRIFT GAUGE:
    azure.serverless_engine.aca_replica_count (existing)
    azure.servicebus.transactions_raw.active_message_count (existing)

  BREAKAGE-PATH:
    - KEDA poller failover (RBAC missed)                              → Lane E ticket
    - scaler triggers replicas spin-burst (cost spike)                → #C.A1 source-of-record
    - queue depth > 1000 && replicas < 2 (scaler not reconciled)     → drift ticket

COST-ATTRIBUTION
  Mode:        (a + b)
  Doc-row:
    | lane-c serverless-engine s1 | KEDA scaler declaration + MI | $0.10–0.20 |
       | imp. | #C.A1 (companion #C.91) | start 2026-07-30 | first burst $0.07 |
  Tags:
    centinela:lp       lane-c
    centinela:epic     [C.0]
    centinela:issue    [C.A1]
    centinela:sprint   sprint-1
    centinela:start    2026-07-30
    centinela:action   create
```

### Example 3: Cross-lane Handoff (Lane B to Lane C)

The **transaction ingestion** lifecycle ships through two lanes:

- Lane B: `[B.9]` writes `transactions-raw` outbox → companion `[B.A1]` configures the SB queue MI.
- Lane C: `[C.X]` consumes `transactions-raw` → companion `[C.A1]` declares the KEDA scaler.

The shared Surface Bus Standard + the queue itself is owned by **Lane E** (`E.A1`). Cross-lane handoff:

- `[B.A1]` Blocked by `[E.A1]` (queue exists) ✅
- `[C.A1]` Blocked by `[B.A1]` AND `[E.A1]` (queue exists, RBAC propagated)

Without this convention, **no one** would notice Lane C consuming from a queue Lane B hasn't been granted role on.

## Failure modes & remediation

| Failure | Detection | Remediation |
|---|---|---|
| Companion issue opened but no `RESOURCE DIFF` filled | PR-template CI lint | Companion issue rejects until RESOURCE DIFF shows line counts non-zero |
| Code PR merges before companion closes | Reverse blocker check at PR close | CI gate: `closes #B.X` not accepted if companion `[#B.X.A*]` still open |
| Doc-row missing at sprint close | Lane A weekly audit | Lane A surfaces missing rows in `ceremony:retrospective` issue |
| Tags missing on resource | checkov / tfsec policy | `checkov` rejects `resource "azurerm_container_app"` without `tags { centinela:lp = … }` |
| Mode (b) only chosen, no tag on a Tier-1 resource | policy as code | Mandatory-tags gate |

## Related Documents

- ADR-010 — `../decision-log/ADR-010-issue-pr-discipline.md`
- `docs/best-practices/05-pr-and-issue-discipline.md`
- `/.github/agent-preflight.md`
- `.github/ISSUE_TEMPLATE/infra-change.md`
