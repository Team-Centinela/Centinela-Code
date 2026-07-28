---
name: Task (lane-managed)
description: Per ADR-010 + §10.9 emulation amendment. A lane-managed task with explicit blockers, Azure-impact declaration, surface selection (real Azure vs emulator), and documentation touch points.
title: "[<Lane>.<N>] "
labels: ["task", "lane-managed"]
assignees: []
---

## Goal

<!-- One-line outcome. The branch name should match this. -->

## Lane

<!-- Lane letter and owner. Examples: "B — Ingestion Service — @3105jero" -->
- **Lane:**
- **Owner:**
- **Parent epic:** #link

## Acceptance Criteria

- [ ] …
- [ ] …
- [ ] **Surface selection** (if `Has azure-impact` is `yes`): real-Azure OR emulator — see §"Surface — REQUIRED"
- [ ] **Documentation touched:** `path/to/file.md` — one-line summary

## Documentation Touches (mandatory)

<!-- REQUIRED: list every documentation file this task lands. -->
- [ ] `<file path>` — `<change summary>`
- [ ] `<file path>` — `<change summary>`

## ADRs (required)

<!-- REQUIRED: link at least one ADR. If this task is purely housekeeping,
     link the parent epic / sprint tracker. -->
- ADR-NNN (`docs/decision-log/...`)
- ADR-011 (`docs/decision-log/ADR-011-local-emulator-stack.md`) if `Surface` = emulator

## Blocked by

<!-- List ONLY closed issue numbers, OR explicitly state "no blockers".
     Pattern disqualifies outdated references — open blockers cannot appear here.

     PHASE 0 EXCEPTION (ADR-010 §10.9.2): an emulator-surface task may proceed
     even when technical blocker issues are still open, gated by the
     verify-script (18 PASS / 0 FAIL) + Phase 0.3 cross-lane E2E green per #167. -->
- #— (closed)
- #— (closed)

## Has azure-impact (required)

<!-- Pick exactly one. Yes triggers §Surface, §Companion infra issue, and the EXPECTED DELIVERY block. -->
- [ ] **No**
- [ ] **Yes** (then §Surface, §Companion infra issue, and §EXPECTED DELIVERY below become mandatory)

### Surface — REQUIRED if `Has azure-impact: yes`

Per ADR-010 §10.9.1, pick exactly one:

- [ ] **Real Azure** (Phase 2 / 3): code touches `*.tf`, KEDA, MI, Key Vault refs, Service Bus binding, ACA env config, container build for ACA. Companion closes via Mode (a)+(b). Use §EXPECTED DELIVERY (real-Azure) below.
- [ ] **Emulator** (Phase 0 / local / CI): code touches `docker-compose.yml`, `docker/**`, `application-local-emulator.yml`, `docker/postgres/init.sql`, `docker/servicebus/Config.json`. Companion closes via **Mode (c) — Emulator Commitment**. Use §EXPECTED DELIVERY (emulator) below.

A single PR that touches both surfaces must declare **two companion issues**, one per surface.

### If yes (real-Azure surface) — EXPECTED DELIVERY (mirrored in companion)

```
RESOURCE DIFF
  <provider>.<resource_type>.<logical_name>   +n/+m/d   <human>

VERIFICATION EVIDENCE
  - commit SHA: <link>
  - terraform plan/apply log: <link>
  - az <verify command>: <output>
  - smoke test: <id>

DRIFT GAUGE
  <metric name>   <existing or new>    DCE <source>

BREAKAGE-PATH
  failure mode → hand-back path
```

### If yes (emulator surface) — EXPECTED DELIVERY (mirrored in companion) — Per ADR-010 §10.9.3 + ADR-011 §11.5

```
RESOURCE DIFF
  docker-compose.yml                +n/+m/d   <human-readable description>
  docker/<image>/init.sql           +n/+m/d   <SQL or Flyway migration>
  docker/servicebus/Config.json     +n/+m/d   <queue/topic declaration>
  application-local-emulator.yml    +n/+m/d   <Spring profile binding>

VERIFICATION EVIDENCE
  - scripts/verify-emulators.{ps1,sh} capture → in issue comment
      MUST report `18 PASS / 0 FAIL`
  - testcontainers run logs           → in PR thread
  - image digest(s) (@sha256:...)    → in PR thread
  - smoke test ID                    → in PR thread

DRIFT GAUGE
  <verify-script section name>          (existing — ADR-011 §11.5)
  <testcontainers test class + method>  (NEW per companion issue)

BREAKAGE-PATH
  failure mode → hand-back path
   • verify-script FAILs              → Lane E (platform) hand-back
   • cold start > 5 min budget          → investigate image digest
   • testcontainers fixture drift       → rebuild compose stack
```

### COST-ATTRIBUTION (emulator surface uses Mode c)

| Surface | Mode | What to record |
|---|---|---|
| real-Azure | Mode (a)+(b) | Doc-row in `infrastructure/README.md` + `centinela:*` tags on Azure resources |
| emulator | Mode (c) | Doc-row `$0.00–0.00` + verify-script `<18 PASS / 0 FAIL>` + image digest(s) |

`centinela:*` tags do NOT apply to emulator-surface closes (MCR images have no external tags).

## Companion infra issue (required if azure-impact: yes)

- **#—** (paired companion issue, opened in **same sprint**, in `infra-change.md` template with the matching surface)

## Does / Does Not (scope fences)

### Does
- …

### Does Not
- … (Link tasks it could be confused with; for example, "out-of-scope here: B.5 Idempotency-Key filter")

## Rubric (auto-grade hint)

<!-- What makes the AI review / Santiago's review fire green lights. -->
- …
- `mvn verify -pl <service>` passes *(real-Azure track)*
- **`mvn verify -P h2` passes** *(emulator-unit track; default)*
- **`docker compose down -v && up -d --wait && scripts/verify-emulators.sh` → 18 PASS / 0 FAIL** *(emulator Phase 0 integration track)*
- ≥ N unit tests + ≥ 1 integration test
- Documented cross-refs intact

## References

- docs/architecture/<file.md>
- docs/patterns/<file.md>
- docs/decision-log/ADR-N-<name>.md
- **ADR-010** (`docs/decision-log/ADR-010-issue-pr-discipline.md`) — §10 (process), §10.9 (emulator surface)
- **ADR-011** (`docs/decision-log/ADR-011-local-emulator-stack.md`) — §11.5 acceptance gate *(if emulator surface)*
- `services/<service>/README.md`
- `/.github/agent-preflight.md`
