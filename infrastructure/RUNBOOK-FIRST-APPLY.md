# First Azure Apply Runbook (Phase 2)

The recipe for going from a green local emulator stack to a green real-Azure deployment. **Human-only execution** per `docs/decision-log/ADR-012-opencode-execution-and-human-handoff.md` §12.4 (`terraform apply`, `az`, `gh pr merge` are denied to AI agents via `opencode.json:permission`).

## When this runbook applies

- First time any Terraform-managed Azure resource is provisioned (PR #127 already shipped the bootstrap RG + state backend; this is for the workload resources).
- After any drift requires re-apply.
- After any controlled failure recovery (nuclear-option §7.7 trigger rolls forward).

## Pre-flight gate (ADR-011 §11.6 layer 1)

| # | Check | Pass criterion | Block on fail |
|---|---|---|---|
| 1 | `pwsh ./scripts/verify-emulators.ps1` | `Result: 22 PASS / 0 FAIL` | Yes |
| 2 | `pwsh ./scripts/verify-emulators.ps1 -Phase01` | `Result: 28 PASS / 0 FAIL` (Floci-AZ Monitor 501 routes INFO, not FAIL) | Yes |
| 3 | Cross-lane E2E smoke on emulator surface | Test ID + log posted to **#159**; status `COMPLETED` | Yes |
| 4 | ADR-010 markdown on merged branch | PR #257 merged (closes #196/#132/#131/#158/#193/#195) | Yes |
| 5 | Phase 0 acceptance criteria on #167 | All ACs green; **§0.3 cross-lane E2E green confirmed** | Yes |
| 6 | Companion infra issues `[<Lane>.A*]` paired with the apply | All open companion issues have `Has azure-impact: yes` + `EXPECTED DELIVERY` + `COST-ATTRIBUTION` blocks filled | Yes |
| 7 | `REGION-QUOTA-CHECK.md` | Status table green (Day-1 region/quota verification) | Yes |

**Block on fail** means the runbook halts at the failing check. Resolve and restart from step 1.

## Bootstrap pre-condition (PR #127)

`rg-tfstate-bootstrap` resource group + `stcentinelatfstate` storage account + `tfstate` container are already provisioned (PR #127, issue #62). No action needed.

If absent (e.g., fresh subscription), bootstrap as a separate PR with the `infra, sprint-0` labels and a companion `[infra][S0.A1]`. Do **not** provision the bootstrap from this runbook.

## Apply steps (human-only)

### Step A — Terraform init

```
cd infrastructure
terraform init -backend-config="resource_group_name=rg-tfstate-bootstrap" -backend-config="storage_account_name=stcentinelatfstate" -backend-config="container_name=tfstate" -backend-config="key=centinela.tfstate"
```

Expected: provider plugins (azurerm ~3.x, azurerm ~3.x) install; backend initialized; no errors.

### Step B — Terraform validate

```
terraform validate
```

Expected: `Success! The configuration is valid.` (no warnings about unused variables, etc., per `terraform validate -json` JSON exit code 0).

### Step C — Checkov + tflint

```
checkov -d . --framework terraform
tflint --recursive
```

Both expected exit 0 (with possibly tflint warnings on existing items; no errors). The `centinela:*` tag convention + Service Bus `max_delivery_count = 3` policy per ADR-010 §10.5 + ADR-003 §3.4 must pass.

### Step D — Terraform plan (capture to file)

```
terraform plan -out=tfplan.bin -var-file=secrets.tfvars 2>&1 | tee tfplan.log
terraform show -json tfplan.bin > tfplan.json
```

**Capture log + JSON in the PR thread** (ADR-010 §10.4 `VERIFICATION EVIDENCE`).

### Step E — Terraform apply

```
terraform apply tfplan.bin 2>&1 | tee tfapply.log
```

Expected: every resource reaches `Creation complete` or `Update complete`. End-to-end duration typically 5–15 min for a first apply.

### Step F — Post-apply verification

For each resource created, run `az <show>` and capture the receipt (per ADR-010 §10.4 `VERIFICATION EVIDENCE`):

```
# Resource group
az group show --name rg-centinela-dev -o json
# Container Apps Environment + first app
az containerapp show --name centinela-ingestion -g rg-centinela-dev -o json
# Service Bus namespace + first entity
az servicebus namespace show --name centinela-sb -g rg-centinela-dev -o json
az servicebus queue show --name transactions-raw --namespace-name centinela-sb -g rg-centinela-dev -o json
# PostgreSQL Flexible Server
az postgres flexible-server show --name centinela-pgflex --resource-group rg-centinela-dev -o json
# Key Vault access from ACA managed identity
az keyvault set-policy --name centinela-kv --object-id $(az containerapp show --name centinela-ingestion -g rg-centinela-dev --query identity.principalId -o tsv) --secret-permissions get list
```

### Step G — Smoke test on real Azure (per #170 §3 acceptance)

```
# Public ingress on Ingestion API
curl -fsS -X POST https://centinela-ingestion.<region>.azurecontainerapps.io/api/v1/transactions \
  -H "X-API-Key: $CENTINELA_API_KEY" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"accountId":"acc-001","amountUsd":99.99,"merchantId":"merch-001","geo":{"lat":4.6,"lon":-74.08}}'
```

Expected: `HTTP 202 Accepted` with `transactionId` echoed. Then `GET /actuator/health` returns `{"status":"UP"}`.

### Step H — Companion close-out (ADR-010 §10.4)

Per companion infra issue, post a closing comment with:

```
Closed by `terraform apply` <yyyy-mm-dd HH:mm> UTC. Verification evidence:
- tfplan.json: <sha256> (this PR thread)
- tfapply.log: <sha256> (this PR thread)
- az <show> outputs: <sha256> (this issue thread)
- Smoke test ID: <sha256 of curl response>
- Cost-row added to infrastructure/README.md §Cost guardrails (this PR)

Drift gauge: <metric name> (per ADR-007 §7.X) — first observed value <N>.

Breakage-path: <none|as below>. <if any, link escalation>
```

Then `gh issue close <N> --reason completed` (human-only per §12.4 + opencode.json:permission).

## Cost-row addition (ADR-010 §10.5 Mode (a))

Append one row per first-apply run to `infrastructure/README.md` §"Per-lane cost rows (live)" with the `centinela:lp / epic / issue / sprint / start / close / action` schema. Format:

```
| lane-A | governance | sprint-1 | first-apply terraform state | $X.XX | companion #<num> | 2026-07-31 | first observed $X.XX |
```

## Budget alarm confirmation (ADR-007 §7.7)

```
az consumption budget list --resource-group rg-centinela-dev
```

Expected: `centinela-21day-budget` is `active` with three notifications at 50/80/90 percent thresholds and an action group email + Teams webhook + Azure Function receiver per ADR-007 §7.7.

If absent: re-apply the budget module (`infrastructure/modules/budget/main.tf`); do **not** skip.

## Drift monitoring (per ADR-007 §7.3)

After apply, the cost-telemetry custom metrics begin emitting:

- `centinela.postgres.state` (Gauge 0/1) — Core Backend scheduled task
- `centinela.aca.replica_count` (Gauge) — each ACA app
- `centinela.aca.cpu_seconds` / `centinela.aca.memory_gib_seconds` (Counter) — each ACA app
- `centinela.servicebus.operations` (Counter, by entity + operation) — publishers + consumers

Verify the workbook `infrastructure/dashboards/cost-telemetry.json` is provisioned (`az monitor workbook list -g rg-centinela-dev` shows it).

## Failure mode — rollback

If `terraform apply` aborts mid-run with state corruption (`Error: Provider produced inconsistent final plan`):

```
terraform state list        # verify partial state
terraform plan -destroy-only -out=tfplan-destroy.bin 2>&1 | tee tfplan-destroy.log
terraform apply tfplan-destroy.bin 2>&1 | tee tfdestroy.log
```

Then diagnose root cause; do **not** `terraform destroy` the bootstrap RG or `stcentinelatfstate` storage account (state would be lost; requires manual recovery from the `.tfstate` blob).

## Failure mode — budget trigger (ADR-007 §7.7 layer 5)

If a budget threshold triggers during apply or shortly after:

| Threshold | Reaction (Azure Function `centinela-budget-action`) | Human action |
|---|---|---|
| 50 % ($30) | Email + Teams | Acknowledge, continue |
| 80 % ($48) | + scale OCR Worker + Serverless Engine to `minReplicas=0` | Decide whether to keep services warm; escalate if false alarm |
| 90 % ($54) | + scale Ingestion + Core Backend to `minReplicas=0` + stop PostgreSQL | Stop non-essential work |
| 100 % ($60) | nuclear option (delete all ACA apps, stop PostgreSQL, delete Service Bus) | **Manual re-provisioning required; cannot auto-recover** — file incident |

## Refs

- `../docs/decision-log/ADR-011-local-emulator-stack.md` §11.6 + §11.7
- `../docs/decision-log/ADR-010-issue-pr-discipline.md` §10.4 + §10.5
- `../docs/decision-log/ADR-007-observability-cost-telemetry.md` §7.7
- `../docs/decision-log/ADR-012-opencode-execution-and-human-handoff.md` §12.4 + §12.5
- `../docs/decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md` §9.3
- `infrastructure/REGION-QUOTA-CHECK.md` (Day-1 verification)
- `.context-snapshots/phase-0-0.0-finish.md` (historical Phase 0 close)
- Issues: #62 (bootstrap; PR #127 merged 2026-07-24), #159 (Phase 0 close-out), #167 (Phase 0 AC), #169 (Phase 2 critical path), #170 (real-Azure smoke)
