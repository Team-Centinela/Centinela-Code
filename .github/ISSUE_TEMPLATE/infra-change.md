---
name: Infra Change (lane-managed, azure-impact companion)
description: Per ADR-010 §10.3–§10.5 + §10.9, ADR-007 §7.7, ADR-011 §11.6 + §11.7. Companion issue for a code-side task that touches the deployment surface — either real Azure (Phase 2/3) or the emulator stack (Phase 0 / local / CI). Pairs with `task-managed.md`.
title: "[infra][<Lane>.<A*>] "
labels: ["infra-change", "azure-impact-companion"]
assignees: []
---

## Goal

<!-- Single-line: what Azure resource, environment, or wiring this lands. -->

## Lane

- **Lane:** `<letter>`
- **Owner:** `@<user>`
- **Companion in code:** **#—** (must reference one)
- **Parent epic:** #link
- **Surface:** pick one — see §"Surface selection (ADR-010 §10.9.1)"

## Surface selection (ADR-010 §10.9.1) — choose exactly one

- [ ] **Real Azure** (Phase 2 / 3): uses Mode (a)+(b); expected delivery = `terraform plan/apply` + `az <show>` + checkov/tflint.
- [ ] **Emulator** (Phase 0 / local / CI): uses **Mode (c) — Emulator Commitment**; expected delivery = `verify-emulators.sh: 18 PASS / 0 FAIL` + image digests + Testcontainers log.

A single PR that touches both surfaces must declare **two companion issues**, one per surface.

## Acceptance Criteria

- [ ] `terraform plan` clean *(real-Azure only)*
- [ ] `terraform apply` log captured in issue comment *(real-Azure only)*
- [ ] `az <verify command>` output captured in issue comment *(real-Azure only)*
- [ ] `scripts/verify-emulators.{ps1,sh}` capture showing `18 PASS / 0 FAIL` posted in issue comment *(emulator only — per ADR-011 §11.6 layer 1 + Operational note)*
- [ ] Testcontainers run logs captured in PR thread *(emulator only)*
- [ ] Image digest(s) of every touched image posted in PR thread *(emulator only — per ADR-011 §11.2)*
- [ ] Smoke test passing
- [ ] Cost row updated in `infrastructure/README.md` §"Cost guardrails" (Mode a doc-row AND/OR Mode c emulator doc-row depending on surface)
- [ ] All resources under this companion carry the `centinela:*` tags below *(real-Azure only; emulator surface has no Azure resources to tag)*
- [ ] **Documentation touched:** below filled

## EXPECTED DELIVERY (real-Azure surface)

```
RESOURCE DIFF
  azurerm_<resource>.<logical_name>   +n/+m/d   <human>

VERIFICATION EVIDENCE
  - commit SHA of paired PR: <link>
  - terraform plan/apply log: <in comment>
  - az <show command>: <in comment>
  - smoke test ID: <in comment>
  - checkov / tflint: <0 high findings>

DRIFT GAUGE
  <metric name>  (existing/new)  DCE <source>

BREAKAGE-PATH
  failure mode → hand-back path
```

## EXPECTED DELIVERY (emulator surface) — Per ADR-010 §10.9.3 + ADR-011 §11.6

```
RESOURCE DIFF
  docker-compose.yml                +n/+m/d   <human-readable description>
  docker/<image>/init.sql           +n/+m/d   <Flyway / SQL Edge migration>
  docker/servicebus/Config.json     +n/+m/d   <queue/topic declaration>
  application-local-emulator.yml    +n/+m/d   <Spring profile binding>

VERIFICATION EVIDENCE
  - scripts/verify-emulators.{ps1,sh} capture → in issue comment
      MUST report `18 PASS / 0 FAIL` after `docker compose down -v && up -d --wait`
  - testcontainers run logs           → in PR thread
  - image digest(s) (@sha256:...)    → in PR thread
  - smoke test ID                    → in PR thread

DRIFT GAUGE
  <verify-script section name>          (existing — see ADR-011 §11.6 layer 1)
  <testcontainers test class + method>  (NEW per companion issue)

BREAKAGE-PATH
  failure mode → hand-back path
   • verify-script FAILs              → Lane E (platform) hand-back
   • cold start > 5 min budget          → investigate image digest
   • testcontainers fixture drift       → rebuild compose stack
```

## COST-ATTRIBUTION (required)

Pick one mode (a), (b), (c), or (a+b):

### Mode (a) — `infrastructure/README.md` doc row *(real-Azure / emulator both)*

```
| <lp> <service> s<N> | <resource-delta>                | $<low>-$<high> |
   | imp. | #<companion-code>                             | start <yyyy-mm-dd> | first run $<X.YY> |
```

Add this row to `infrastructure/README.md` §"Cost guardrails" table and reference this issue. **Emulator-surface rows use `$0.00–0.00`** per ADR-010 §10.9.3 + a `verify-script <18 PASS / 0 FAIL>` placeholder.

### Mode (b) — Azure resource tags *(real-Azure ONLY)*

```
centinela:lp       <lane letter>          e.g. "lane-b"
centinela:epic     [<epic-id>]           e.g. "[B.0]"
centinela:issue    [<companion-id>]      e.g. "[B.A1]"
centinela:sprint   sprint-<N>
centinela:start    <yyyy-mm-dd>
centinela:close    <yyyy-mm-dd>           (filled at close)
centinela:action   create|update|delete
```

**Mode (b) does NOT apply to emulator-surface closes.** MCR / SB-Emulator / Floci-AZ / `mssql/server:2022-latest` do not support external tags. The `centinela:*` schema is **inferred retrospectively** on the first `terraform apply` of Phase 2.

### Mode (c) — Emulator Commitment *(emulator-surface ONLY — Per ADR-010 §10.9.3)*

```
cost-row (Mode a — $0.00–0.00):
  | lane-X <service> s1 | emulator-side wiring | $0.00–0.00 |
     | imp. | #<companion> | start <yyyy-mm-dd> | verify-script <18 PASS / 0 FAIL> |

commit-evidence (replaces Mode b — no centinela:* tags on MCR images):
  - image digest(s): <mcr.microsoft.com/azure-messaging/servicebus-emulator@sha256:...>
  - verify-script log: <scripts/verify-emulators.{ps1,sh} capture>
  - testcontainers run: <test class + method names>
```

### Mode (a+b) — gold standard *(real-Azure ONLY)*

Both Mode (a) and Mode (b) above. **Default for Tier-1 resources** (RG, Service Bus Standard namespace, ACA Environment, Key Vault, App Insights).

## Blocked by

<!-- Required. List closed issue numbers. -->
- #— (closed)

**Phase 0 / emulator-surface exception** (per ADR-010 §10.9.2): the closed-blocker rule is **substituted** by the verify-script gate — `scripts/verify-emulators.{ps1,sh}` reports `18 PASS / 0 FAIL` after `docker compose down -v && up -d --wait`.

## Has azure-impact

Yes (this is the infrastructure side of an azure-impact pair).

## Documentation Touches (mandatory)

- [ ] `infrastructure/README.md` — Cost guardrails row added (Mode a doc-row, or Mode c emulator doc-row)
- [ ] `services/<service>/README.md` — Infra surface section updated *(real-Azure only)*
- [ ] `docs/architecture/0X-name.md` — if applicable
- [ ] `infrastructure/REGION-QUOTA-CHECK.md` — if applicable

## Back-trace guarantee

```
Real-Azure back-trace:
  Azure Cost Analysis:
    Filter   Resource Tags.centinela:lp == <lane letter>
    Group by Resource Tags.centinela:issue
    Range    centinela:start — centinela:close

OR

  infrastructure/README.md doc-row grep for issue# == #<companion>

Emulator back-trace:
  GitHub Issues: filter [infra] + lane label + companion-to-#<code-issue>
  + verification-evidence comment with verify-script capture
```

## References

- ADR-010 (`docs/decision-log/ADR-010-issue-pr-discipline.md`) §10.3–§10.5 + §10.9 emulation amendment
- ADR-007 (`docs/decision-log/ADR-007-observability-cost-telemetry.md`) §7.7
- **ADR-011** (`docs/decision-log/ADR-011-local-emulator-stack.md`) §11.6 Pre-validation gate (emulator)
- `docs/patterns/07-azure-impact-companion-issue.md` §"Phase 0 Close"
- `docs/best-practices/05-pr-and-issue-discipline.md`
- `/.github/agent-preflight.md`
