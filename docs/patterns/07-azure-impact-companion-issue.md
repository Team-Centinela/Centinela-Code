# Pattern 07 — Azure Impact Companion Issue

> Per ADR-010 §10.3–§10.5. The companion issue is the **code ↔ Azure handshake**: a code-side task and an infra-side task that ship in the same milestone, share a sprint, share the same epic, and meet the same sprint close. Per [ADR-010 §10.9](https://github.com/Team-Centinela/Centinela-Code/blob/feat/adr-010-issue-pr-discipline-implementation/docs/decision-log/ADR-010-issue-pr-discipline.md), the same pattern applies to **emulator-surface** only changes via Mode (c) — Emulator Commitment.

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

For **emulator-surface only changes** (per ADR-010 §10.9.1), the same handshake exists between a code-side task and an `infra`-labelled companion carrying Mode (c) — Emulator Commitment — and the close-out evidence is the §"Phase 0 Close" block below, not the terraform-shaped `EXPECTED DELIVERY`.

## Why this pattern

1. **Back-trace forward**: every cost row in `infrastructure/README.md` points at one or more companion issues. Back-tracing from Azure to dollars ↔ dollars to issue ↔ issue to epic is a single grep.
2. **Forward-trace backward**: a code PR labelled `azure-impact` has a paired ticket for the Azure resource. The reviewer can open both side-by-side.
3. **Reviewer discipline**: A reviewer can sandbox terraform without touching code, side-by-side — each lane owns the parcel it understands best.
4. **Cost guardrails enforced**: each issue's COST-ATTRIBUTION block is the ledger cell for that work.
5. **§10.9 amendment**: emulator-surface closes have the same audit trail without requiring real-Azure resources — verify-script + image digest + Testcontainers log substitutes for `terraform apply` log.

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

### Emulator-side body (emulator-surface closes — §10.9.3)

```
COMPANION IN CODE
  #B.<n>  (the code-side task this issue fulfils; emulator surface)

EXPECTED DELIVERY (Emulator side)

  RESOURCE DIFF
    docker-compose.yml          +n/+m/d   <human-readable description>
    docker/<image>/init.sql     +n/+m/d   <human-readable description>
    application-local-emulator.yml   +n/+m/d

  VERIFICATION EVIDENCE
    - scripts/verify-emulators.{ps1,sh} capture → in issue comment
      (must report `18 PASS / 0 FAIL`)
    - testcontainers run logs → in PR thread
    - image digest(s) of every touched image    → in PR thread

  DRIFT GAUGE
    <verify-script section name>           (existing — see §11.6 layer 1 + Operational note)
    <testcontainers test class + method>    (NEW per companion issue)

  BREAKAGE-PATH
    - verify-script reports FAIL            → Lane E (platform) hand-back
    - cold start > 5 min budget            → investigate image digest
    - testcontainers fixture drift          → rebuild compose stack

COST-ATTRIBUTION
  Mode: (c) — Emulator Commitment  ($0.00–0.00)

  Doc-row entry (mode a — Phase 0 emulator):
    | lane-X <service> s1 | emulator-side wiring | $0.00–0.00 |
       | imp. | #<companion> | start <yyyy-mm-dd> | verify-script <18 PASS / 0 FAIL> |

  Image-digest evidence (replaces mode b — no `centinela:*` tags on MCR images):
    - image digest(s): <mcr.microsoft.com/azure-messaging/servicebus-emulator@sha256:...>
    - verify-script log: <scripts/verify-emulators.{ps1,sh} capture>
    - testcontainers run: <test class + method names>
```

## Mode (a) vs Mode (b) vs Mode (c) — when to choose which

| Mode | Choose when | Why |
|---|---|---|
| **a — doc-row only** | Lightweight, low-cost change (1 resource, 1 tag, 1 role). Sprint fast. | Doc-row audit at lane sprint-close catches drift; no Azure-side cost to a tag. |
| **b — tag-only** | Multiple resources across same service; high-volume tagging needed; cost-aggregates bleed across sprints | A single Cost Analysis slice answers "what did this lane do?" |
| **a + b (gold)** | Default for any companion issue that affects a Tier-1 resource (RG, Service Bus Standard, ACA Environment, Key Vault, App Insights) | Both humans (doc-row) and ops (tag) can read it |
| **c — emulator commitment** | Emulator-surface only change (no real-Azure resource yet) — close uses `$0.00–0.00` + verify-script gate as evidence. First `terraform apply` of Phase 2 converts the row into Mode (a)+(b). | Bridges Phase 0 → Phase 2 without losing audit trail. Per ADR-010 §10.9.3. |

## Worked examples

### Example 1: Ingestion Outbox wiring (real-Azure close)

See the example at the head of this file.

### Example 2: Engine KEDA scaler declaration (real-Azure close)

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

### Example 3: Cross-lane Handoff (Lane B to Lane C, real-Azure)

The **transaction ingestion** lifecycle ships through two lanes:

- Lane B: `[B.9]` writes `transactions-raw` outbox → companion `[B.A1]` configures the SB queue MI.
- Lane C: `[C.X]` consumes `transactions-raw` → companion `[C.A1]` declares the KEDA scaler.

The shared Service Bus Standard + the queue itself is owned by **Lane E** (`E.A1`). Cross-lane handoff:

- `[B.A1]` Blocked by `[E.A1]` (queue exists) ✅
- `[C.A1]` Blocked by `[B.A1]` AND `[E.A1]` (queue exists, RBAC propagated)

Without this convention, **no one** would notice Lane C consuming from a queue Lane B hasn't been granted role on.

## Phase 0 Close — Emulator Commitment Evidence

Per ADR-010 §10.9.3, a Phase 0 / emulator-surface companion closes with **Mode (c)** evidence instead of Mode (a)+(b). The required pieces:

| Evidence | Where | Citation |
|---|---|---|
| **Verify-script gate** | Issue comment with the full `scripts/verify-emulators.{ps1,sh}` capture (must show `18 PASS / 0 FAIL`) | [ADR-011 §11.6 layer 1](../decision-log/ADR-011-local-emulator-stack.md) Pre-validation gate + Operational note (verify-script receipt) |
| **Image digest(s)** | Issue comment listing `@sha256:...` of every image touched in `docker-compose.yml` | [ADR-011 §11.2](https://github.com/Team-Centinela/Centinela-Code/blob/phase-0/0.1-validation/docs/decision-log/ADR-011-local-emulator-stack.md#112-compose-stack--what-runs-and-what-it-covers) image-digest pinning |
| **Testcontainers log** | Issue comment with the test class + method that exercised the change | ADR-011 §11.2 (image-digest pinning) + §11.6 layer 1 (cross-lane E2E green) |
| **Doc-row (Mode a, $0.00–0.00)** | Row added to `infrastructure/README.md` §"Cost guardrails" with the `verify-script <18 PASS / 0 FAIL>` placeholder | ADR-010 §10.9.3 |

This is what unblocks **Mode (a)+(b)** on the **first** `terraform apply` of Phase 2 (conversion to real Azure): the doc-row carries the image digest + verify-script receipt, which the ops reviewer can match against the new `azurerm_*` resources.

### Worked example — emulator-surface close (Phase 0 / pre-validation)

```
TITLE:       [infra][B.A1-l] Ingestion: local-emulator wiring for outbox publisher on transactions-raw
TYPE:        infra-change (emulator surface)
OWNER:       @3105jero
LANE:        B
COMPANION:   #B.9 (code-side, paired same sprint)

EXPECTED DELIVERY (Emulator side)

  RESOURCE DIFF:
    docker-compose.yml                                        +12/+0/0
    docker/postgres/init.sql                                  +0/+1/0  (Flyway V1)
    docker/servicebus/Config.json                             +1/+0/0  (transactions-raw queue declared)
    services/ingestion/src/main/resources/application-local-emulator.yml  +1/+0/0

  VERIFICATION EVIDENCE:
    scripts/verify-emulators.sh capture → in issue comment
      • section "1. compose-up"        PASS
      • section "5. Spring Boot actuator" PASS
      • section "7. Flyway history"     PASS
      total: 18 PASS / 0 FAIL
    Testcontainers run: services/ingestion - IngestionOutboxPublisherIT.shouldAppendOnCommit
    Image digests touched:
      mcr.microsoft.com/azure-messaging/servicebus-emulator@sha256:5a96d893b245031740f7d46e0fe5ff282d24b78c4b7d761dd57590f3f010a9b3
      postgis/postgis:16-3.4-alpine@sha256:681931a625df344215e9b8998bf34daf146b6a395ceacee4439eb9c85869239f
      floci/floci-az@sha256:5e403a7d788c24ab2d1dc03d4803ef2d3962ca01522abba65ed86f221ae87ac0

  DRIFT GAUGE:
    verify-emulators.sh §7 Flyway history                  (existing)
    ingestion Testcontainers (Phase 0.1, IngestionOutboxPublisherIT)   (NEW per companion)

  BREAKAGE-PATH:
    - verify-script reports FAIL on cold start   → Lane E (platform) hand-back
    - docker-compose image pull fails            → re-pin image digest; bump verify-script
    - testcontainers fixture drift               → rebuild compose stack

COST-ATTRIBUTION
  Mode:        (c) — Emulator Commitment
  Doc-row:
    | lane-b ingestion s1 | emulator-side wiring for outbox publisher | $0.00–0.00 |
       | imp. | #B.A1-l (companion #B.9) | start 2026-07-29 | verify-script <18 PASS / 0 FAIL> |

  Image-digest evidence (replaces mode b):
    - see VERIFICATION EVIDENCE above
```

### Claim preamble for an emulator-surface Lane B claim

```
Pre-flight (Rule 1):  Emulator gate — verify-emulators.sh: 18 PASS / 0 FAIL ✓ (§10.9.2 exception
                       applies; Blocked by: #B.2 (Flyway V1) ✓, #E.shared-outbox ✗ → claim on §10.9.2.)
Pre-flight (Rule 2):  Has azure-impact: yes (emulator surface; docker-compose.yml + application-
                       local-emulator.yml touched). Companion: #B.A1-l (paired, same sprint).
Pre-flight (Rule 3):  ADR-010 §10.9.1 + ADR-011 §11.2 + §11.4 + §11.7 + §11.8 re-read.
                       patterns/07 §"Phase 0 Close" re-read.
                       docker-compose.yml + scripts/verify-emulators.{ps1,sh} re-read.
```

## Failure modes & remediation

| Failure | Detection | Remediation |
|---|---|---|
| Companion issue opened but no `RESOURCE DIFF` filled | PR-template CI lint | Companion issue rejects until RESOURCE DIFF shows line counts non-zero |
| Code PR merges before companion closes | Reverse blocker check at PR close | CI gate: `closes #B.X` not accepted if companion `[#B.X.A*]` still open |
| Doc-row missing at sprint close | Lane A weekly audit | Lane A surfaces missing rows in `ceremony:retrospective` issue |
| Tags missing on resource | checkov / tfsec policy | `checkov` rejects `resource "azurerm_container_app"` without `tags { centinela:lp = … }` |
| Mode (b) only chosen, no tag on a Tier-1 resource | policy as code | Mandatory-tags gate |
| **§10.9 amendment** — Mode (c) close without verify-script receipt | verify-script mention in issue comment required | CI gate: `closes #<companion>` not accepted without `verify-script: 18 PASS / 0 FAIL` marker |
| **§10.9 amendment** — emulator claim proceeds with failed verify-script | verify-script re-run required at claim comment time | Lane A revokes claim preamble; Phase 0 work pauses until `18 PASS` re-emitted |

## Related Documents

- ADR-010 — `../decision-log/ADR-010-issue-pr-discipline.md` (§10.9 Emulation Amendment + §10.5.c Mode (c))
- **ADR-011** — `../decision-log/ADR-011-local-emulator-stack.md` (§11.6 Pre-validation gate + §11.7 hard prerequisite gate)
- `docs/best-practices/05-pr-and-issue-discipline.md`
- `/.github/agent-preflight.md` (Rule 3 reads ADR-011 for emulator surfaces)
- `.github/ISSUE_TEMPLATE/infra-change.md`
