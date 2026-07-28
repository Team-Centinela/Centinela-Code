# ADR-011: Local Emulator Stack for Pre-Validation

## Status

**PROPOSED** — drafted on `phase-0/0.1-validation` against `feat/week-one-consolidation @ 44fcb61`. Tracked at #193. Promotion to **ACCEPTED** after team review per #193 §Acceptance Criteria.

## Context

The §21 strategic-pivot comment ([`issuecomment-5084940075`](https://github.com/Team-Centinela/Centinela-Code/pull/127#issuecomment-5084940075)) declared Azure delivery as Sprint-1's critical path and halted all new code until the environment is provisioned. Per #166, **Phase 0** is the permitted exception: a `$0`-cost local pre-validation pass against `PRs #120–#130` that catches ~92 % of post-merge findings (38 of 41) before consuming Azure dollars. #167 §"Cost" pegs the saving at ~$27 of the $60 / 21-day budget.

Phase 0.0 (merged at `44fcb61` via PR #171 + PR #185 + PR #192) brought up four emulators in Docker Compose — PostGIS, Microsoft Service Bus Emulator, Azure SQL Edge (state store for SB Emulator), and Floci-AZ — plus the three Spring Boot services on a `local-emulator` Spring profile. Verify scripts (`scripts/verify-emulators.{ps1,sh}`) report `18 PASS / 0 FAIL`. Phase 0.1 + 0.2 + 0.3 Testcontainers tests run **exclusively** against this stack.

The decision to use emulators is architectural, not a developer preference. It changes the failure surface (no SLA, no Azure AD auth, no IMDS managed-identity tokens, no geo-redundancy), the cost model ($0 vs ~$27+ on Azure), and the test contract (§0.3 cross-lane E2E must be green locally before any real-Azure test runs).

ADR-002, ADR-003, ADR-006, ADR-007, and ADR-009 all assume real Azure. None of them states which emulators substitute for which Azure resources in local + Phase 0 work, what the emulators cover vs. don't, or how the static SAS key + local Postgres credentials replace Key Vault + managed identity. That gap is what this ADR closes.

## Decision

### 11.1 Adopt the local emulator stack as the canonical pre-validation surface

For **local development**, **Phase 0 pre-validation**, and **future CI matrix runs** (`[E.47]` #104), all Azure-touching code paths are exercised against the Docker Compose stack in `docker-compose.yml`, never against real Azure. Real Azure is the **deploy target** of merged code, not a test surface.

| Use case | Surface |
|---|---|
| Unit tests (Spring `@SpringBootTest` w/ H2 2.2.224) | `application-test.yml` — unchanged |
| Integration tests (Testcontainers, Phase 0.1 + 0.2) | Compose stack via `centinela` Docker bridge network |
| Cross-lane E2E smoke (Phase 0.3) | Compose stack — same network, three containers + four emulators |
| Local manual development (`mvn spring-boot:run`) | Compose stack on `local-emulator` profile |
| Real-Azure smoke (Phase 2 / Phase 3) | Real Azure — applies `terraform apply` results, **never** the compose stack |

### 11.2 Compose stack — what runs and what it covers

| Container | Image | Port | Replaces | Covers |
|---|---|---|---|---|
| `centinela-postgres` | `postgis/postgis:16-3.4-alpine@sha256:681931a625df344215e9b8998bf34daf146b6a395ceacee4439eb9c85869239f` | 5432 | Azure DB for PostgreSQL Flexible Server B1ms (ADR-002) | Schema-per-module, PostGIS, `uuid-ossp`, Flyway V1 + V2, partitioned `oltp.transactions`, JSONB, all 9 schemas |
| `centinela-servicebus` | `mcr.microsoft.com/azure-messaging/servicebus-emulator:latest@sha256:5a96d893b245031740f7d46e0fe5ff282d24b78c4b7d761dd57590f3f010a9b3` | 5672 (AMQP), 5300 (mgmt) | Azure Service Bus Standard (ADR-003) | Queues + topics + subscriptions + dead-letter siblings (`maxDeliveryCount=3`); declared in `docker/servicebus/Config.json` |
| `centinela-sqledge` | `mcr.microsoft.com/mssql/server:2022-latest@sha256:90488d58c6a5c19f24ff716e14330b85b5b26ee54b44a36ea24e6206533e7edd` | 1433 | (none — internal state store for SB Emulator) | NOT a Centinela dependency; only the SB Emulator talks to it. Per Microsoft sample at `azure-service-bus-emulator-installer` |
| `centinela-floci-az` | `floci/floci-az:latest@sha256:5e403a7d788c24ab2d1dc03d4803ef2d3962ca01522abba65ed86f221ae87ac0` | 4577 | Azure Key Vault + Blob Storage + App Configuration + Azure Monitor (ADR-006 + ADR-007) | Secrets CRUD (`PUT/GET /secrets/{name}`); Blob (`PUT/GET /{account}/{container}/{blob}`); Logs Ingestion API (`POST /dataCollectionRules/{id}/streams/{stream}`); Log Analytics KQL subset (`where`/`project`/`take`/`limit`) |
| `centinela-ingestion` | local Dockerfile | 8081 | (deployed service, not Azure replacement) | Spring Boot on `local-emulator` profile |
| `centinela-core-backend` | local Dockerfile | 8080 | (deployed service) | Spring Boot on `local-emulator` profile |
| `centinela-serverless-engine` | local Dockerfile | 8082 | (deployed service) | Spring Boot on `local-emulator` profile |

**Image-digest pinning.** Every `:latest` tag in the table is a **human-readable alias**; the committed `docker-compose.yml` pins each image by `@sha256:...` digest for reproducibility. Pulling a new digest is a deliberate commit — the verify-script gate (`scripts/verify-emulators.{ps1,sh}` reporting `18 PASS / 0 FAIL`) is the signal that the local machine's cache matches the digest the team has ratified.

**Why digests, not tags.** Two of the four emulators have an upstream-history worth pinning for:

- `mcr.microsoft.com/azure-messaging/servicebus-emulator:latest` is the official path as of 2026-07-28; the legacy path `mcr.microsoft.com/azure-servicebus-emulator:latest` (no `/azure-messaging/` segment) **404s** and was the path used in `docker-compose.yml:50` before this ADR — a hidden bug that the cached image on existing dev machines masked.
- `mcr.microsoft.com/mssql/server:2022-latest` replaces the retired `azure-sql-edge:latest`; pinning the digest catches a future SQL Server 2025 transition.
- `floci-az` is young (~v0.5.0 as of 2026); KQL-subset + ARM-management-plane features are landing monthly; a `:latest` upgrade could break the verify-script gate with no code change on our side.
- `postgis/postgis:16-3.4-alpine` is the most stable of the four (PostGIS releases are conservative); pinning is for symmetry, not for stability.

`scripts/verify-emulators.{ps1,sh}` is the canonical "is the stack still green" gate: 18 assertions across 7 sections (compose-up, Postgres, SB Emulator, Floci-AZ, SQL Edge, Spring Boot actuator, Flyway history). CI must fail the build on any `[FAIL]` (per AGENTS.md §"Docs CI" link-check rule applied here as a verify-script gate).

### 11.3 Profile convention

- `application.yml` (default profile) — production-only env vars; uses `${AZURE_SERVICEBUS_CONNECTION_STRING:}` and `${APPLICATIONINSIGHTS_CONNECTION_STRING:}` defaults that **fail fast** if unset in production.
- `application-local-emulator.yml` — overrides datasource URL to `jdbc:postgresql://centinela-postgres:5432/centinela` (the bridge network hostname), sets `SPRING_PROFILES_ACTIVE=local-emulator`, hardcodes the SB Emulator connection string per PR #171 §Production Safety contract.
- `application-local.yml` — H2 2.2.224 in-memory profile for unit tests; **does not** touch the compose stack. Pinned to Flyway-certified version per PR #185.
- `application-test.yml` — Testcontainers profile (future `[E.43]` #152 starter); uses the same compose stack but with isolated container IDs per test.

Spring's profile resolution: `mvn spring-boot:run -Dspring-boot.run.profiles=local-emulator` → `application.yml` + `application-local-emulator.yml`. The Spring Cloud Stream binder's `auto-startup: false` in `application.yml:35` is explicitly set per service to keep init fast in non-prod profiles; `local-emulator` flips it on for the three services wired in `docker-compose.yml:127-211`.

### 11.4 Static credentials and dev-only contract

Per PR #171 §"Production Safety":

- **SB Emulator SAS key** is the static `SAS_KEY_VALUE` Microsoft ships in `docker/servicebus/Config.json`. This string is **dev-only**; production uses Key Vault references via Spring Cloud Azure's `key-vault-secret-ref` per ADR-006 §6.3.
- **PostgreSQL credentials** are `postgres / postgres`. Production uses Azure AD auth via ACA managed identity (ADR-002 §"Planned DB downtime & outbox restart-drain" + ADR-006 §6.5).
- **Floci-AZ credentials** are not enforced (`dev` auth mode — any account name + any key). Production uses Azure AD tokens via `DefaultAzureCredential`.
- **SB Emulator connection string** contains the SAS key inline (`docker-compose.yml:139, 186, 205`); the **`local-emulator` profile must never be selected in production** — it is gated by an environment check (planned `[E.48]` #108 lint rule).

If a future bug or migration accidentally enables `local-emulator` in production, the SB Emulator connection string **will** be loaded by the SDK and the publisher will attempt AMQP handshakes with a non-existent host. The failure mode is loud (immediate startup exception, not silent corruption).

**Azure SQL Edge retirement** (Microsoft, 2025-09-30). The Service Bus Emulator's state store historically depended on `mcr.microsoft.com/azure-sql-edge:latest`; that image is officially retired, and MCR's continued service is courtesy of Microsoft's backward-compatibility policy, not a guarantee.

**Microsoft has NOT migrated** their own [official Service Bus Emulator compose template](https://github.com/Azure/azure-service-bus-emulator-installer/blob/main/Docker-Compose-Template/docker-compose-default.yml). `Docker-Compose-Template/docker-compose-default.yml:26` still pins `mcr.microsoft.com/azure-sql-edge:latest` as of 2026-07-28. The migration request has been on the upstream tracker since 2024-11-20 ([`Azure/azure-service-bus-emulator-installer#18`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/18), still open 20 months later, 7 +1s) and the recent commit log shows zero work touching the SQL backend image. Community users are swapping to `mssql/server:2022-latest` themselves because of ARM64 + Docker Desktop compatibility issues with the retired SQL Edge — see upstream issues [`#122`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/122) (OOM during MSSQL 2022 schema upgrades), [`#124`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/124) (ARM64 breakage), and [`#135`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/135) (BufferQueue-1 entity creation fails on both images, but MSSQL pulls reliably).

**Centinela's `docker-compose.yml` follows the community migration, not the upstream template** — this is an **informed team deviation**, documented here so reviewers can see the choice is intentional. **Verification follow-up owed**: re-run `scripts/verify-emulators.sh` cold-start timing against `mssql/server:2022-latest` to confirm boot time stays within the ≤5 min budget (the script currently asserts `18 PASS / 0 FAIL` against `azure-sql-edge`; heavier image may exceed) and watch for upstream OOM regressions on schema-upgrade.

**For the historical record** (per review feedback at [comment 5109207418](https://github.com/Team-Centinela/Centinela-Code/issues/193#issuecomment-5109207418)): we are migrating **away from Microsoft's stale sqledge dependency onto `mssql/server:2022-latest` ourselves, because Microsoft has not**. This is **our team's informed deviation** — not a follow of upstream guidance that doesn't exist. A reader of this ADR in six months should be able to identify which decision was made and by whom: Centinela's team, against the still-unmigrated Microsoft upstream template. The SQL Edge → `mssql/server` swap is also one INSTANCE of the broader Azure resource-retirement problem documented in §11.8.

### 11.5 Acceptance gate for Phase 0 work

A Testcontainers integration test in Phase 0.1 / 0.2 / 0.3 is considered green **only** if:

1. `docker compose down -v && docker compose up -d --wait` boots the four emulators cleanly (`scripts/verify-emulators.{ps1,sh}` §1 `[PASS]`).
2. The test asserts on **real PG round-trip** (not H2) when the test exercises Flyway V1 + PostGIS or `outbox.outbox_events` (per #167 §0.1.1).
3. The test asserts on **real AMQP** (not Mockito) when the test exercises `transactions-raw` / `case-events` (per #167 §0.1.2).
4. The test asserts on **real Floci-AZ round-trip** (not in-memory mock) when the test exercises Key Vault / Blob / App Insights ingestion (per #167 §0.1.3).

### 11.6 Budget protection for emulator-vs-production fidelity gaps

The emulators catch ~92 % of regressions per #167, but not 100 %. The residual 8 % — bugs that slip through, or bugs introduced by the gap between emulator and real Azure semantics (managed identity token acquisition, retry timing, AAD token refresh, real Service Bus geo-redundancy, real App Insights ingestion latency) — must not be allowed to **run unbounded on real Azure**. Five layers of defense minimize the residual risk; **the residual budget-at-risk for a fidelity-gap bug is qualitatively bounded, not numerically pinned** (see "Why no specific $X figure" below).

| # | Layer | Mechanism | Trigger | Ref |
|---|---|---|---|---|
| 1 | **Pre-validation gate** | Phase 0.3 cross-lane E2E green locally BEFORE any `terraform apply` per #167 §0.3 + §11.5 | `mvn -pl services/e2e verify` BUILD SUCCESS + log + test ID posted in #159 | #167, #159 |
| 2 | **Apply runbook + companion close-out discipline** | Every `[E.A*]` / `[B/C/D.A*]` companion closes only after `az <show>` + `terraform apply` log + paired-code-PR SHA + smoke test ID per ADR-010 §10.4 | Companion issue comment + close | ADR-010, #160 |
| 3 | **Scale-to-zero on ACA** | KEDA `minReplicas=0` + consumption plan per ADR-009 §9.3 | Idle (no HTTP / no queue depth) | ADR-009 |
| 4 | **Auto-stop PostgreSQL** | B1ms `auto_stop` enabled, 60-min idle per ADR-002 §"Planned DB downtime" | Scheduled (weekday nights + weekends) | ADR-002 |
| 5 | **Cost Management budget + Action Group escalator** | 50/80/90/100 % alerts at $60 ceiling per ADR-007 §7.7 | Cumulative spend threshold | ADR-007 |

**Layer 5 escalation ladder** (per ADR-007 §7.7):

- **50 %**: email + Teams webhook — `centinela-team@centinela.onmicrosoft.com`
- **80 %**: + Azure Function `centinela-budget-action` — sets `minReplicas=0` on OCR Worker + Serverless Engine (the two KEDA-scaled services)
- **90 %**: + sets `minReplicas=0` on Ingestion API + Core Backend + stops PostgreSQL Flexible Server via `az postgres flexible-server stop`
- **100 %**: **nuclear option** — deletes all ACA apps, stops PostgreSQL, deletes Service Bus namespace. Requires manual re-provisioning; not auto-recovered.

**Apply-discipline contract** (Phase 2 step 2.4 per #169 §2.4): `terraform apply` of PR #127 MUST be preceded by (a) Phase 0.3 cross-lane E2E green, AND (b) Phase 0.2 §15 / §30.5 cleanliness fixes merged. If Phase 0 surfaces any `🔴 S1` finding not in §15 / §30.5, apply pauses per #167 §0.3 acceptance criteria. **No code path lands on real Azure before local pre-validation confirms it.**

**Residual risk characterization** (qualitative). A fidelity-gap bug that only surfaces on real Azure (e.g. managed identity token acquisition fails after 60 days of MI rotation, real SB dead-letter timing diverges from emulator, real App Insights ingestion 429-throttles a runaway publisher) cannot be caught by §11.5. Layer 1 prevents the bug from reaching Azure at all; layers 2–4 cap sustained burn while the bug is being detected; layer 5 escalates the response. The qualitative ceiling is: **time-to-trigger × worst-case sustained burn rate between thresholds**, where time-to-trigger is bounded by ADR-007 §7.7's action-function execution time + Azure Cost Management alert latency (typically 1–4 hours per [docs](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/cost-mgmt-alerts-monitor-usage)).

**Why no specific $X residual figure**. Azure public pricing pages (Azure Container Apps Consumption, Service Bus Standard) render per-second / per-million-ops / per-hour rates as `$-` placeholders that require region selection + an authenticated Azure portal session to materialize into dollar amounts; without running telemetry on a deployed resource group, any specific residual dollar figure is **fabricated precision**. The qualitative defense is what matters: layers 1–4 minimize the probability of a fidelity-gap bug reaching Azure; layer 5 minimizes the burn if it does. **Real cost data will be available after Phase 2 apply + first full sprint; this ADR will be amended with grounded numbers once telemetry exists.**

**Operational note for Phase 2**: before any `terraform apply`, the operator MUST run `scripts/verify-emulators.{ps1,sh}` and confirm `18 PASS / 0 FAIL`. The script's output is the receipts for layer 1 of this section.

### 11.7 ADR-010 hard prerequisite gate

ADR-010 (Issue & PR Discipline) Markdown is currently on branch `feat/adr-010-issue-pr-discipline-implementation`, **NOT merged** to `feat/week-one-consolidation` as of 2026-07-28. Every `[E.A*]` / `[B/C/D.A*]` companion's close-out per §11.6 layer 2 cites ADR-010 §10.4 — the apply runbook + companion close-out discipline depends on it being on disk in the merged branch.

**This is therefore a HARD prerequisite gate for Phase 2 apply, not a tracked issue**: Phase 2 step 2.4 (`terraform apply` of PR #127 per #169) MUST NOT run until ADR-010 markdown has merged into `feat/week-one-consolidation`. The merge unblocks:

- §11.6 layer 2 (close-out template) — without ADR-010 §10.4 on disk, companion close-outs are informal and `az <show>` + `terraform apply` log + paired-PR SHA + smoke test ID requirements have no canonical form
- #160 (the per-issue closing-comment tracker for closed S1 issues #62 / #88 / #89 / #93–#97 / #102 / #103)
- #158 (PR #130 audit labelling per ADR-010 §10.4)
- Phase 1 governance (per #168 §1.1)

**Enforcement path**: `infrastructure/REGION-QUOTA-CHECK.md` and `[E.A*]` companions `#148 / #149 / #150` should each include an explicit "ADR-010 merged" checkbox. The `[Lane-A]` epic #134 tracks the merge as one of its acceptance criteria. A new tracking issue to land the merge should be opened in Phase 1 (Lane A) with the label `adr, governance, lane-a, sprint-1` and a `Blocked by:` line that explicitly references this ADR-011 §11.7.

### 11.8 Azure resource retirement awareness + migration playbook

**The SQL Edge retirement documented in §11.4 is one instance of a class of risk**: Azure-managed services can be retired at any time with typically 12 months notice (sometimes less). Centinela's planned Azure footprint per ADR-002 / 003 / 006 / 007 / 009 depends on resources that could be retired during the 21-day project window or in the future. **Retirement awareness is part of the §11.6 budget-protection contract** — if Cost Management alerts + Action Group runs (layer 5) or auto-stop PostgreSQL (layer 4) silently fail because those services retire, the $60 ceiling is no longer protected.

**Centinela-affected retirements as of 2026-07-28** (per [retire.azu.fyi](https://retire.azu.fyi/), last updated 2026-07-27 — *real, legitimate third-party retirement tracker* maintained by [@kongou_ae](https://twitter.com/kongou_ae)):

| # | Retirement | Date | Affected Centinela component | Source |
|---|---|---|---|---|
| 1 | Some Azure Service Bus SDK libraries — migrate to latest SDKs | 2026-09-30 | `spring-cloud-azure-starter-servicebus` pinning per ADR-003 §3.1 | [link](https://azure.microsoft.com/en-us/updates/retirement-notice-update-your-azure-service-bus-sdk-libraries-by-30-september-2026/) |
| 2 | Migrate to Azure AI Document Intelligence v3.1 GA | 2026-08 | OCR Worker SDK per ADR-006 §6.4 (`document-intelligence-key` from KV) | [link](https://azure.microsoft.com/en-us/updates/migrate-to-azure-ai-document-intelligence-v31-ga-version/) |
| 3 | GPv1 and Legacy Blob storage account creation | 2026-10 | Storage account per ADR-002 — must provision **GPv2** explicitly (GPv1 is the default; need `account_kind=StorageV2`) | [link](https://azure.microsoft.com/en-us/updates/564441/) |
| 4 | Transition to ContainerLogV2 table | 2026-09-30 | Log Analytics writes per ADR-007 §7.1 | [link](https://azure.microsoft.com/en-us/updates/transition-to-the-containerlogv2-table-by-30-september-2026/) |
| 5 | Azure SQL Edge (emulator dep) | 2025-09-30 (already retired) | `centinela-sqledge` compose service per §11.4 | [link](https://azure.microsoft.com/en-us/updates/?id=azure-sql-edge-retirement) |

**This list is a snapshot; the team MUST re-verify before each Phase 2 / Phase 3 apply.** A separate issue ([Phase 0 follow-up](#193 §"Outstanding for team review")) will own the recurring audit.

**Monitoring sources** — subscribe before Phase 2 apply:

1. **Azure Advisor Service Retirement workbook** (built-in to every Azure subscription) — resource-level view of *which of YOUR resources* are impacted by upcoming retirements. Accessible at `Azure Portal → Advisor → Workbooks → Gallery → Azure Advisor → Service Retirement`. The **Impacted Services** view filters by subscription / resource group / location; use `rg-centinela-dev` after the bootstrap RG exists per #127. Direct link: [`portal.azure.com/#.../AzureServiceRetirement`](https://portal.azure.com/#blade/AppInsightsExtension/UsageNotebookBlade/ComponentId/Azure%20Advisor/ConfigurationId/community-Workbooks%2FAzure%20Advisor%2FAzureServiceRetirement/WorkbookTemplateName/Service%20Retirement). API + Azure Resource Graph automation per [service-upgrade-retirement-recommendations](https://learn.microsoft.com/en-us/azure/advisor/advisor-how-to-use-service-upgrade-retirement-recommendations).
2. **[retire.azu.fyi](https://retire.azu.fyi/)** — third-party retirement calendar maintained by [@kongou_ae](https://twitter.com/kongou_ae). Verified accurate against the SQL Edge case. Subscribe to the RSS feed or check monthly during Sprint retro. **GitHub Action candidate** (future CI): weekly query + fail if any retirement affecting Centinela is within 90 days and not yet triaged.
3. **[azurefeeds.com/tag/retirements](https://azurefeeds.com/tag/retirements/)** — Azure retirement feed aggregator.
4. **[Azure Updates RSS — `updateType=retirements`](https://azure.microsoft.com/en-us/updates/?updateType=retirements)** — canonical Microsoft source.
5. **Azure Service Health alerts** — subscription-level Service Health alerts for service retirements; configure per [Microsoft Learn](https://learn.microsoft.com/en-us/azure/service-health/service-health-alert-profiles). Catches retirements that affect resources in your subscription specifically.
6. **Microsoft Learn Advisor docs** — [`advisor-workbook-service-retirement`](https://learn.microsoft.com/en-us/azure/advisor/advisor-workbook-service-retirement) + [`advisor-how-to-use-service-upgrade-retirement-recommendations`](https://learn.microsoft.com/en-us/azure/advisor/advisor-how-to-use-service-upgrade-retirement-recommendations).

**Migration playbook** (per retirement notice affecting a Centinela resource):

1. **Triage (≤ 7 days from notice)**: file a `[infra]`-labelled GitHub issue with `Blocked by:` referencing the retirement announcement URL + the Centinela-affected component. Labels: `infra, retirement, lane-e`. Owner: Lane E.
2. **Impact assessment (≤ 14 days)**: quantify (a) which Centinela resources are impacted (per Azure Advisor workbook **Impacted Services** view); (b) what code/config touches the retiring SDK/service; (c) what is the migration path per the retirement notice's recommended alternative.
3. **Migration branch**: cut `phase-X/migration-<retirement-name>` from `feat/week-one-consolidation`. Apply the SDK/service swap. Verify locally against `docker compose up -d --wait` (if emulator surface affected) + `mvn verify` (Spring) / `pytest` (Python OCR Worker). Update `docker-compose.yml` if the swap affects emulator dependencies (the §11.4 SQL Edge pattern is the template).
4. **Apply runbook** (Phase 2 only): per #169 §2.4 + ADR-010 §10.4 companion close-out discipline. Companion `[E.A*]` style close-out with `az <show>` evidence.
5. **Post-migration verification**: re-run `scripts/verify-emulators.{ps1,sh}` (must remain `18 PASS / 0 FAIL`) + the Phase 0.3 cross-lane E2E smoke (when §0.3 lands). Add the migration to the next Sprint retro.
6. **Update ADR**: amend the affected ADR (002 / 003 / 006 / 007 / 009 / etc.) with the new version pin per ADR-010 amendment process. **Do not silently ship a new ADR.**

**Cost of this discipline**: ~1 hour/week per Lane E owner to triage retire.azu.fyi + check Azure Advisor workbook for `rg-centinela-dev` once it exists. Negligible compared to the cost of discovering a retirement mid-deploy.

**Inherited from §11.4**: the SQL Edge → `mssql/server` swap is a case study for this playbook. Future upstream SB Emulator migrations (e.g. dropping AMQP 1.0, dropping the Artemis sidecar) would follow the same §11.8 migration path.

## Consequences

### Positive

- **$0 dev cost** — full integration test coverage without consuming Azure budget. Saves ~$27 of the $60 / 21-day ceiling per #167 §"Cost".
- **Fast local feedback** — cold-start budget per `scripts/verify-emulators.sh`: SB Emulator ≤60 s, Floci-AZ ≤30 s, Postgres + Flyway history ≤90 s. Total ≤3 min on a clean boot.
- **Pre-validation gate** — Phase 0.3 cross-lane E2E must be green locally before the same flow runs on real Azure. Catches ~92 % of regressions before Azure dollars are spent.
- **CI-friendly** — `docker compose up` works in headless CI (no Azure subscription, no Azure AD app registration, no secrets). Future `[E.47]` #104 can run the matrix without cost.
- **One profile convention** — `local-emulator` is the only "non-prod-but-real" surface; H2 (`local`) and Testcontainers (`test`) are subsets. Engineers do not need to choose between three conflicting test surfaces.

### Negative

- **Fidelity gap** — the emulators do not perfectly match Azure:
  - No Azure AD authentication (Postgres uses local creds; Floci-AZ dev auth mode)
  - No IMDS managed-identity token acquisition (use static SAS key + creds)
  - No real Service Bus retry semantics, dead-letter timing, geo-redundancy
  - No real Application Insights ingestion (data is local; KQL subset queryable)
  - No Azure Cost Management budget alerts (out of scope locally)
- **Static SAS key in compose file** — must be gated by `[E.48]` #108 lint rule to prevent `local-emulator` profile activation in production. PR #171 §Production Safety is a contract; this ADR formalizes it.
- **Floci-AZ HTTPS enforcement** — Azure SDKs (`azure-security-keyvault-secrets`, `azure-storage-blob`, App Configuration) enforce HTTPS on the vault URL; a `ForceHttp` transport policy is required in client code to rewrite `https://localhost:4577` → `http://localhost:4577`. This is a known Floci-AZ pattern ([docs](https://floci.io/floci-az/services/key-vault/)), not a Centinela workaround.
- **SQL Edge warmup** — ~90–120 s cold start on Windows + Docker Desktop; the verify script budgets 5 min total.
- **Compose-network hostnames** — services inside the `centinela` bridge network reference each other by service name (`centinela-postgres`, `centinela-servicebus`, `centinela-floci-az`). Localhost access from the host machine uses `localhost:PORT`. Engineers must remember which surface they are testing.
- **Maintenance burden** — the emulator stack itself needs updates (Floci-AZ, SB Emulator images; `docker/postgres/init.sql` per schema change; `docker/servicebus/Config.json` per queue/topology change). This is a deliberate trade-off vs. running against real Azure.

## Alternatives Considered

| Alternative | Reason rejected |
|---|---|
| **Azurite only** (Microsoft's official Blob emulator) | No Service Bus, no Key Vault, no App Insights. Insufficient for Phase 0.1 + 0.3. Per [Floci-AZ comparison](https://floci.io/az/), Azurite covers 1 service; Floci-AZ covers 20+. |
| **Microsoft SB Emulator only** | No PostgreSQL, no KV, no App Insights. Insufficient. |
| **Cloud-hosted ephemeral dev env** (Azure DevTest Labs, ACA jumpbox) | Costs money every active hour; defeats the $0 pre-validation goal. |
| **In-memory mocks** (Mockito, H2, embedded broker) | Cannot exercise Flyway + PostGIS dialect path; cannot verify `processed_events` ledger ON CONFLICT semantics; cannot catch ADR-003 §3.2 lifecycle bugs that only surface against real AMQP. Per PR #185 the H2-only tests missed 11 bugs that the compose stack caught. |
| **docker-compose per developer, no shared verify scripts** | Loses the `18 PASS / 0 FAIL` reproducibility gate; bugs regress silently across machines. |
| **No pre-validation — straight to Azure** | The §21 pivot explicitly forbids this for non-Phase-0 work, and the historical 31 post-merge findings (PRs #123–#126) on the engine prove the cost: a single B1ms round-trip per bug = $0.001 × 38 = $3.80 of "test budget" Azure burn that the compose stack catches for free. |
| **Testcontainers only** (ephemeral per-test containers) | Testcontainers spin-up cost per test (~10–30 s) compounds across the Phase 0.1 + 0.2 + 0.3 suite (~50 tests). A shared compose stack amortizes to ~once per CI run. Testcontainers remains the right choice for per-test isolation (future `[E.43]` #152 starter), not for shared-resource pre-validation. |

## References

- #166 — W1 consolidation parent
- #167 — Phase 0 epic (§0.0 already merged; §0.1 / 0.2 / 0.3 are the work this ADR formalizes)
- #169 — Phase 2 Azure Delivery; this ADR's §11.6 budget-protection layers are consumed by #169 §2.4 (apply runbook contract)
- #193 — this ADR's tracking issue
- PR #171 — emulator stack initial wiring (`1b12ef2`, `2cfc8cb`)
- PR #185 — 11-issue fix-up block (Service Bus endpoint, outbox `published_at`, Flyway V1 safety, H2 pin to 2.2.224, etc.)
- PR #192 — docs sweep (ADR-003 §3.2 filename, ADR-002 + Outbox Pattern `status='SENT'` → `status='PUBLISHED'`, team-size 4 → 5)
- `.context-snapshots/phase-0-0.0-finish.md` — full Phase 0.0 post-merge state
- `.context-snapshots/w1-consolidation-PRs-120-130.md` §15 + §20 + §30.5 — Phase 0.2 cleanliness-fix scope
- ADR-002 §"Schemas" + §"Planned DB downtime & outbox restart-drain" — local `postgres/postgres` replaces B1ms + Azure AD auth; auto-stop feeds §11.6 layer 4
- ADR-003 §3.1 + §3.4 — Service Bus Standard + KEDA + binder chain; SB Emulator static SAS key replaces Key Vault refs
- ADR-006 §6.3 + §6.5 — Key Vault + Managed Identity; emulators replace both with static creds + dev auth mode (Markdown present in `feat/week-one-consolidation`)
- ADR-007 §7.1, §7.2, §7.4, **§7.7** — App Insights + W3C traceparent + business metrics + **50/80/90/100 % budget alarms** (Markdown present; §7.7 is the source for §11.6 layer 5)
- ADR-009 §9.1 + §9.3 — compute substrate + scale-to-zero (same Docker images build for ACA + local emulator; feeds §11.6 layer 3)
- **ADR-010 — issue/PR discipline** — Markdown lives on branch `feat/adr-010-issue-pr-discipline-implementation` as of 2026-07-28; **NOT merged to `feat/week-one-consolidation`**. **HARD prerequisite gate for Phase 2 step 2.4 per §11.7**: every `[E.A*]` companion close-out cites ADR-010 §10.4; without the markdown the close-out template is informal. This ADR's §11.6 layer 2 + §11.7 both depend on ADR-010 being on disk.
- `docker-compose.yml`, `scripts/verify-emulators.{ps1,sh}`, `docker/postgres/init.sql`, `docker/servicebus/Config.json`, `services/*/src/main/resources/application-local-emulator.yml`
- Floci-AZ docs — [`floci.io/floci-az/services/key-vault/`](https://floci.io/floci-az/services/key-vault/), [`floci.io/az/`](https://floci.io/az/) (Key Vault `ForceHttp` pattern, Blob Storage endpoint shape, Monitor KQL subset)
- Microsoft Service Bus Emulator installer — [`Azure/azure-service-bus-emulator-installer`](https://github.com/Azure/azure-service-bus-emulator-installer) (compose template at `Docker-Compose-Template/docker-compose-default.yml`; **still pins `azure-sql-edge:latest` at line 26** as of 2026-07-28 per §11.4)
- Microsoft SB Emulator Docker Hub — [`microsoft/azure-messaging-servicebus-emulator`](https://hub.docker.com/r/microsoft/azure-messaging-servicebus-emulator) (current official image)
- Microsoft Docs — [Test locally by using the Azure Service Bus emulator](https://github.com/MicrosoftDocs/azure-docs/blob/main/articles/service-bus-messaging/test-locally-with-service-bus-emulator.md) (last reviewed 2025-10-27; the published doc page **shows** a sample config using `mssql/server:2022-latest` — useful reference for the migration, but the published doc's sample diverges from the upstream installer template, which still uses `azure-sql-edge`)
- Azure SQL Edge retirement — [`azure.microsoft.com/updates?id=azure-sql-edge-retirement`](https://azure.microsoft.com/en-us/updates/?id=azure-sql-edge-retirement) (Microsoft announcement, effective 2025-09-30)
- Azure retirement calendar — [`retire.azu.fyi`](https://retire.azu.fyi/) (full list of upcoming Azure service retirements)
- Upstream installer issues driving the community migration (cited in §11.4):
  - [`Azure/azure-service-bus-emulator-installer#18`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/18) — "Azure SQL Edge will be retired on September 30th, 2025" — opened 2024-11-20, **still open 20 months later**, 7 +1s. The canonical "please migrate to mssql/server" ticket.
  - [`Azure/azure-service-bus-emulator-installer#122`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/122) — "Service Bus Emulator crashes with OOM when MSSQL 2022-latest runs database schema upgrades" (2026-02-25) — known downstream regression on the heavier image; watchlist item for our verification follow-up.
  - [`Azure/azure-service-bus-emulator-installer#124`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/124) — "Remove MSSQL as a hard dependency or switch to a real multi-architecture alternative" (2026-03-03) — ARM64 motivation for community migration.
  - [`Azure/azure-service-bus-emulator-installer#135`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/135) — "Emulator v2.0.0: Internal BufferQueue-1 entity creation fails" (2026-04-10) — tested both images, same failure on both; **MSSQL pulls reliably** is the only confirmed differentiator.