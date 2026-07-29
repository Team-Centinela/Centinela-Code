# ADR-011: Local Emulator Stack for Pre-Validation

## Context

The §21 strategic-pivot comment ([`issuecomment-5084940075`](https://github.com/Team-Centinela/Centinela-Code/pull/127#issuecomment-5084940075)) declared Azure delivery as Sprint-1's critical path and halted all new code until the environment is provisioned. Per #166, the emulator pre-validation pass against `PRs #120–#130` is the permitted `$0`-cost exception that catches ~92 % of post-merge findings (38 of 41) before consuming Azure dollars. #167 §"Cost" pegs the saving at ~$27 of the $60 / 21-day budget.

The pre-validation rollout (merged via PR #171 + PR #185 + PR #192) brought up four emulators in Docker Compose — PostGIS, Microsoft Service Bus Emulator, Azure SQL Edge (state store for SB Emulator), and Floci-AZ — plus the three Spring Boot services on a `local-emulator` Spring profile. The verify script (`scripts/verify-emulators.{ps1,sh}`) is the canonical "is the stack still green" gate. Testcontainers integration tests (#167 §0.1 / §0.2 / §0.3) run **exclusively** against this stack.

The decision to use emulators is architectural, not a developer preference. It changes the failure surface (no SLA, no Azure AD auth, no IMDS managed-identity tokens, no geo-redundancy), the cost model ($0 vs ~$27+ on Azure), and the test contract (cross-lane E2E must be green locally before any real-Azure test runs).

ADR-002, ADR-003, ADR-006, ADR-007, and ADR-009 all assume real Azure. None of them states which emulators substitute for which Azure resources in local pre-validation work, what the emulators cover vs. don't, or how the static SAS key + local Postgres credentials replace Key Vault + managed identity. That gap is what this ADR closes.

## Decision

### 11.1 Adopt the local emulator stack as the canonical pre-validation surface

For **local development** and **CI matrix runs** (`[E.47]` #104), all Azure-touching code paths are exercised against the Docker Compose stack in `docker-compose.yml`, never against real Azure. Real Azure is the **deploy target** of merged code, not a test surface.

| Use case | Surface |
|---|---|
| Unit tests (Spring `@SpringBootTest` w/ H2 2.2.224) | `application-test.yml` — unchanged |
| Integration tests (Testcontainers, #167 §0.1 + §0.2) | Compose stack via `centinela` Docker bridge network |
| Cross-lane E2E smoke (#167 §0.3) | Compose stack — same network, three containers + four emulators |
| Local manual development (`mvn spring-boot:run`) | Compose stack on `local-emulator` profile |
| Real-Azure smoke | Real Azure — applies `terraform apply` results, **never** the compose stack |

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

**Image-digest pinning.** Every `:latest` tag in the table is a **human-readable alias**; the committed `docker-compose.yml` pins each image by `@sha256:...` digest for reproducibility. Pulling a new digest is a deliberate commit — the verify-script gate (`scripts/verify-emulators.{ps1,sh}` reporting all checks pass) is the signal that the local machine's cache matches the digest the team has ratified.

**Why digests, not tags.** Two of the four emulators have an upstream-history worth pinning for:

- `mcr.microsoft.com/azure-messaging/servicebus-emulator:latest` is the official path; the legacy path `mcr.microsoft.com/azure-servicebus-emulator:latest` (no `/azure-messaging/` segment) **404s** and was the path used in `docker-compose.yml` before this ADR — a hidden bug that the cached image on existing dev machines masked.
- `mcr.microsoft.com/mssql/server:2022-latest` replaces the retired `azure-sql-edge:latest`; pinning the digest catches a future SQL Server 2025 transition.
- `floci-az` is young (~v0.5.0 at the time of writing); KQL-subset + ARM-management-plane features are landing monthly; a `:latest` upgrade could break the verify-script gate with no code change on our side.
- `postgis/postgis:16-3.4-alpine` is the most stable of the four (PostGIS releases are conservative); pinning is for symmetry, not for stability.

`scripts/verify-emulators.{ps1,sh}` is the canonical "is the stack still green" gate: assertions across the suite's sections (compose-up, Postgres, SB Emulator, Floci-AZ, SQL Edge, Spring Boot actuator, Flyway history). CI must fail the build on any `[FAIL]` (per AGENTS.md §"Docs CI" link-check rule applied here as a verify-script gate).

### 11.3 Profile convention

- `application.yml` (default profile) — production-only env vars; uses `${AZURE_SERVICEBUS_CONNECTION_STRING:}` and `${APPLICATIONINSIGHTS_CONNECTION_STRING:}` defaults that **fail fast** if unset in production.
- `application-local-emulator.yml` — overrides datasource URL to `jdbc:postgresql://centinela-postgres:5432/centinela` (the bridge network hostname), sets `SPRING_PROFILES_ACTIVE=local-emulator`, hardcodes the SB Emulator connection string per PR #171 §Production Safety contract.
- `application-local.yml` — H2 2.2.224 in-memory profile for unit tests; **does not** touch the compose stack. Pinned to Flyway-certified version per PR #185.
- `application-test.yml` — Testcontainers profile (`[E.43]` #152 starter); uses the same compose stack but with isolated container IDs per test.

Spring's profile resolution: `mvn spring-boot:run -Dspring-boot.run.profiles=local-emulator` → `application.yml` + `application-local-emulator.yml`. The Spring Cloud Stream binder's `auto-startup: false` in `application.yml` is explicitly set per service to keep init fast in non-prod profiles; `local-emulator` flips it on for the three services wired in `docker-compose.yml`.

### 11.4 Static credentials and dev-only contract

Per PR #171 §"Production Safety":

- **SB Emulator SAS key** is the static `SAS_KEY_VALUE` Microsoft ships in `docker/servicebus/Config.json`. This string is **dev-only**; production uses Key Vault references via Spring Cloud Azure's `key-vault-secret-ref` per ADR-006 §6.3.
- **PostgreSQL credentials** are `postgres / postgres`. Production uses Azure AD auth via ACA managed identity (ADR-002 §"Planned DB downtime & outbox restart-drain" + ADR-006 §6.5).
- **Floci-AZ credentials** are not enforced (`dev` auth mode — any account name + any key). Production uses Azure AD tokens via `DefaultAzureCredential`.
- **SB Emulator connection string** contains the SAS key inline (`docker-compose.yml` service env blocks); the **`local-emulator` profile must never be selected in production** — it is gated by an environment check (planned `[E.48]` #108 lint rule).

If a future bug or migration accidentally enables `local-emulator` in production, the SB Emulator connection string **will** be loaded by the SDK and the publisher will attempt AMQP handshakes with a non-existent host. The failure mode is loud (immediate startup exception, not silent corruption).

**Azure SQL Edge retirement** (Microsoft, 2025-09-30). The Service Bus Emulator's state store historically depended on `mcr.microsoft.com/azure-sql-edge:latest`; that image is officially retired, and MCR's continued service is courtesy of Microsoft's backward-compatibility policy, not a guarantee.

**Microsoft has NOT migrated** their own [official Service Bus Emulator compose template](https://github.com/Azure/azure-service-bus-emulator-installer/blob/main/Docker-Compose-Template/docker-compose-default.yml). `Docker-Compose-Template/docker-compose-default.yml:26` pins `mcr.microsoft.com/azure-sql-edge:latest`. The migration request has been on the upstream tracker since 2024-11-20 ([`Azure/azure-service-bus-emulator-installer#18`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/18), still open) and the recent commit log shows zero work touching the SQL backend image. Community users are swapping to `mssql/server:2022-latest` themselves because of ARM64 + Docker Desktop compatibility issues with the retired SQL Edge — see upstream issues [`#122`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/122) (OOM during MSSQL 2022 schema upgrades), [`#124`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/124) (ARM64 breakage), and [`#135`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/135) (BufferQueue-1 entity creation fails on both images, but MSSQL pulls reliably).

**Centinela's `docker-compose.yml` follows the community migration, not the upstream template** — this is an **informed team deviation**, documented here so reviewers can see the choice is intentional. **Verification follow-up owed**: re-run `scripts/verify-emulators.sh` cold-start timing against `mssql/server:2022-latest` to confirm boot time stays within the ≤5 min budget (the script asserts all checks pass against `azure-sql-edge`; heavier image may exceed) and watch for upstream OOM regressions on schema-upgrade.

**For the historical record** (per review feedback at [comment 5109207418](https://github.com/Team-Centinela/Centinela-Code/issues/193#issuecomment-5109207418)): we are migrating **away from Microsoft's stale sqledge dependency onto `mssql/server:2022-latest` ourselves, because Microsoft has not**. This is **our team's informed deviation** — not a follow of upstream guidance that doesn't exist. A reader of this ADR in six months should be able to identify which decision was made and by whom: Centinela's team, against the still-unmigrated Microsoft upstream template. The SQL Edge → `mssql/server` swap is also one INSTANCE of the broader Azure resource-retirement problem documented in §11.8.

### 11.6 Budget protection for emulator-vs-production fidelity gaps

The emulators do not perfectly match Azure semantics (managed identity token acquisition, retry timing, AAD token refresh, real Service Bus geo-redundancy, real App Insights ingestion latency). Fidelity-gap bugs that only surface on real Azure must not be allowed to **run unbounded**. Five layers of defense minimize residual risk; **the residual budget-at-risk for a fidelity-gap bug is qualitatively bounded, not numerically pinned** (see "Why no specific $X figure" below).

| # | Layer | Mechanism | Trigger | Ref |
|---|---|---|---|---|
| 1 | **Pre-validation gate** | Cross-lane E2E green locally BEFORE any `terraform apply` | `mvn -pl services/e2e verify` BUILD SUCCESS + log + test ID posted in #159 | #159 |
| 2 | **Apply runbook + companion close-out discipline** | Every `[E.A*]` / `[B/C/D.A*]` companion closes only after `az <show>` + `terraform apply` log + paired-code-PR SHA + smoke test ID per ADR-010 §10.4 | Companion issue comment + close | ADR-010, #160 |
| 3 | **Scale-to-zero on ACA** | KEDA `minReplicas=0` + consumption plan per ADR-009 §9.3 | Idle (no HTTP / no queue depth) | ADR-009 |
| 4 | **Auto-stop PostgreSQL** | B1ms `auto_stop` enabled, 60-min idle per ADR-002 §"Planned DB downtime" | Scheduled (weekday nights + weekends) | ADR-002 |
| 5 | **Cost Management budget + Action Group escalator** | 50/80/90/100 % alerts at $60 ceiling per ADR-007 §7.7 | Cumulative spend threshold | ADR-007 |

**Layer 5 escalation ladder** (per ADR-007 §7.7):

- **50 %**: email + Teams webhook — `centinela-team@centinela.onmicrosoft.com`
- **80 %**: + Azure Function `centinela-budget-action` — sets `minReplicas=0` on OCR Worker + Serverless Engine (the two KEDA-scaled services)
- **90 %**: + sets `minReplicas=0` on Ingestion API + Core Backend + stops PostgreSQL Flexible Server via `az postgres flexible-server stop`
- **100 %**: **nuclear option** — deletes all ACA apps, stops PostgreSQL, deletes Service Bus namespace. Requires manual re-provisioning; not auto-recovered.

**Apply-discipline contract**: the first `terraform apply` against real Azure MUST be preceded by (a) cross-lane E2E green locally, AND (b) the §11.4 cleanliness fixes merged. If pre-validation surfaces any `🔴 S1` finding not in the cleanup scope, apply pauses. **No code path lands on real Azure before local pre-validation confirms it.**

**Residual risk characterization** (qualitative). A fidelity-gap bug that only surfaces on real Azure (e.g. managed identity token acquisition fails after 60 days of MI rotation, real SB dead-letter timing diverges from emulator, real App Insights ingestion 429-throttles a runaway publisher) cannot be caught by the local pre-validation suite. Layer 1 prevents the bug from reaching Azure at all; layers 2–4 cap sustained burn while the bug is being detected; layer 5 escalates the response. The qualitative ceiling is: **time-to-trigger × worst-case sustained burn rate between thresholds**, where time-to-trigger is bounded by ADR-007 §7.7's action-function execution time + Azure Cost Management alert latency (typically 1–4 hours per [docs](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/cost-mgmt-alerts-monitor-usage)).

**Why no specific $X residual figure**. Azure public pricing pages (Azure Container Apps Consumption, Service Bus Standard) render per-second / per-million-ops / per-hour rates as `$-` placeholders that require region selection + an authenticated Azure portal session to materialize into dollar amounts; without running telemetry on a deployed resource group, any specific residual dollar figure is **fabricated precision**. The qualitative defense is what matters: layers 1–4 minimize the probability of a fidelity-gap bug reaching Azure; layer 5 minimizes the burn if it does. **Real cost data will be available after the first real-Azure apply + first full sprint; this ADR will be amended with grounded numbers once telemetry exists.**

**Operational note**: before any `terraform apply`, the operator MUST run `scripts/verify-emulators.{ps1,sh}` and confirm all checks pass. The script's output is the receipts for layer 1 of this section.

### 11.7 ADR-010 hard prerequisite gate

ADR-010 (Issue & PR Discipline) Markdown is the canonical source for the apply runbook + companion close-out discipline cited in §11.6 layer 2. Without ADR-010 §10.4 on disk in the merged branch, companion close-outs are informal and `az <show>` + `terraform apply` log + paired-PR SHA + smoke test ID requirements have no canonical form.

**This is therefore a HARD prerequisite gate for the first real-Azure `terraform apply`, not a tracked issue**: the apply MUST NOT run until ADR-010 markdown has merged. The merge unblocks:

- §11.6 layer 2 (close-out template)
- Per-issue closing-comment tracker #160 (closed S1 issues #62 / #88 / #89 / #93–#97 / #102 / #103)
- PR #130 audit labelling #158 (per ADR-010 §10.4)
- Lane-A governance per #168 §1.1

**Enforcement path**: `infrastructure/REGION-QUOTA-CHECK.md` and the [E.A*] companion issues should each include an explicit "ADR-010 merged" checkbox. The Lane-A epic #134 tracks the merge as one of its acceptance criteria. A new tracking issue to land the merge should be opened with the label `adr, governance, lane-a, sprint-1` and a `Blocked by:` line that explicitly references this ADR-011 §11.7.

### 11.8 Azure resource retirement awareness + migration playbook

**The SQL Edge retirement documented in §11.4 is one instance of a class of risk**: Azure-managed services can be retired at any time with typically 12 months notice (sometimes less). Centinela's planned Azure footprint per ADR-002 / 003 / 006 / 007 / 009 depends on resources that could be retired during the 21-day project window or in the future. **Retirement awareness is part of the §11.6 budget-protection contract** — if Cost Management alerts + Action Group runs (layer 5) or auto-stop PostgreSQL (layer 4) silently fail because those services retire, the $60 ceiling is no longer protected.

The current Centinela-affected retirement snapshot is maintained **outside this ADR** (a recurring audit lives in a separate issue) so the ADR stays stable as the snapshot evolves. The monitoring sources and migration playbook below are the durable procedure.

**Monitoring sources** — subscribe so retirements affecting Centinela resources are visible before they bite:

1. **Azure Advisor Service Retirement workbook** (built-in to every Azure subscription) — resource-level view of *which of YOUR resources* are impacted by upcoming retirements. Accessible at `Azure Portal → Advisor → Workbooks → Gallery → Azure Advisor → Service Retirement`. The **Impacted Services** view filters by subscription / resource group / location. API + Azure Resource Graph automation per [service-upgrade-retirement-recommendations](https://learn.microsoft.com/en-us/azure/advisor/advisor-how-to-use-service-upgrade-retirement-recommendations).
2. **[retire.azu.fyi](https://retire.azu.fyi/)** — third-party retirement calendar maintained by [@kongou_ae](https://twitter.com/kongou_ae). Verified accurate against the SQL Edge case.
3. **[azurefeeds.com/tag/retirements](https://azurefeeds.com/tag/retirements/)** — Azure retirement feed aggregator.
4. **[Azure Updates RSS — `updateType=retirements`](https://azure.microsoft.com/en-us/updates/?updateType=retirements)** — canonical Microsoft source.
5. **Azure Service Health alerts** — subscription-level Service Health alerts for service retirements; configure per [Microsoft Learn](https://learn.microsoft.com/en-us/azure/service-health/service-health-alert-profiles). Catches retirements that affect resources in your subscription specifically.

**Migration playbook** (per retirement notice affecting a Centinela resource):

1. **Triage (≤ 7 days from notice)**: file a `[infra]`-labelled GitHub issue with `Blocked by:` referencing the retirement announcement URL + the Centinela-affected component. Labels: `infra, retirement, lane-e`. Owner: Lane E.
2. **Impact assessment (≤ 14 days)**: quantify (a) which Centinela resources are impacted (per Azure Advisor workbook **Impacted Services** view); (b) what code/config touches the retiring SDK/service; (c) what is the migration path per the retirement notice's recommended alternative.
3. **Migration branch**: cut a branch from the development trunk. Apply the SDK/service swap. Verify locally against `docker compose up -d --wait` (if emulator surface affected) + `mvn verify` (Spring) / `pytest` (Python OCR Worker). Update `docker-compose.yml` if the swap affects emulator dependencies (the §11.4 SQL Edge pattern is the template).
4. **Apply runbook**: per ADR-010 §10.4 companion close-out discipline. Companion `[E.A*]` style close-out with `az <show>` evidence.
5. **Post-migration verification**: re-run the emulator pre-validation suite + the cross-lane E2E smoke. Add the migration to the next Sprint retro.
6. **Update ADR**: amend the affected ADR (002 / 003 / 006 / 007 / 009 / etc.) with the new version pin per ADR-010 amendment process. **Do not silently ship a new ADR.**

**Cost of this discipline**: ~1 hour/week per Lane E owner to triage retire.azu.fyi + check Azure Advisor workbook for `rg-centinela-dev` once it exists. Negligible compared to the cost of discovering a retirement mid-deploy.

**Inherited from §11.4**: the SQL Edge → `mssql/server` swap is a case study for this playbook. Future upstream SB Emulator migrations (e.g. dropping AMQP 1.0, dropping the Artemis sidecar) would follow the same §11.8 migration path.

## Consequences

### Positive

- **$0 dev cost** — full integration test coverage without consuming Azure budget. Saves ~$27 of the $60 / 21-day ceiling per #167 §"Cost".
- **Fast local feedback** — cold-start budget per `scripts/verify-emulators.sh`: SB Emulator ≤60 s, Floci-AZ ≤30 s, Postgres + Flyway history ≤90 s. Total ≤3 min on a clean boot.
- **Pre-validation gate** — cross-lane E2E must be green locally before the same flow runs on real Azure, catching regressions before Azure dollars are spent.
- **CI-friendly** — `docker compose up` works in headless CI (no Azure subscription, no Azure AD app registration, no secrets). CI matrix runs cost nothing per `[E.47]` #104.
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
| **Azurite only** (Microsoft's official Blob emulator) | No Service Bus, no Key Vault, no App Insights. Insufficient. Per [Floci-AZ comparison](https://floci.io/az/), Azurite covers 1 service; Floci-AZ covers 20+. |
| **Microsoft SB Emulator only** | No PostgreSQL, no KV, no App Insights. Insufficient. |
| **Cloud-hosted ephemeral dev env** (Azure DevTest Labs, ACA jumpbox) | Costs money every active hour; defeats the $0 pre-validation goal. |
| **In-memory mocks** (Mockito, H2, embedded broker) | Cannot exercise Flyway + PostGIS dialect path; cannot verify `processed_events` ledger ON CONFLICT semantics; cannot catch ADR-003 §3.2 lifecycle bugs that only surface against real AMQP. Per PR #185 the H2-only tests missed 11 bugs that the compose stack caught. |
| **docker-compose per developer, no shared verify scripts** | Loses the suite-pass reproducibility gate; bugs regress silently across machines. |
| **No pre-validation — straight to Azure** | The §21 pivot explicitly forbids this; the historical post-merge findings on the engine prove the cost. |
| **Testcontainers only** (ephemeral per-test containers) | Testcontainers spin-up cost per test (~10–30 s) compounds across the integration + cross-lane E2E suite. A shared compose stack amortizes to ~once per CI run. Testcontainers remains the right choice for per-test isolation (`[E.43]` #152 starter), not for shared-resource pre-validation. |

## References

- **Tracking / issues**: #166 (W1 consolidation parent), #167 (pre-validation epic; #167 §0.0 already merged, §0.1 / §0.2 / §0.3 are the work this ADR formalizes), #169 (Azure Delivery; §11.6 budget-protection layers consumed by #169 §2.4), #193 (this ADR's tracking issue), #160 (per-issue closing-comment tracker), #158 (PR #130 audit labelling), #159 (cross-lane E2E).
- **PRs**: #120–#130 (work the emulator stack validates), #171 (emulator stack initial wiring), #185 (11-issue fix-up block), #192 (docs sweep), #194 (ADR-010 implementation).
- **Snapshots / cleanliness**: `.context-snapshots/phase-0-0.0-finish.md`, `.context-snapshots/w1-consolidation-PRs-120-130.md` §15 + §20 + §30.5.
- **Peer ADRs**: ADR-002 §Schemas + §"Planned DB downtime", ADR-003 §3.1 + §3.4, ADR-006 §6.3 + §6.5, ADR-007 §7.1 / §7.2 / §7.4 / §7.7, ADR-009 §9.1 + §9.3, ADR-010 (hard prerequisite per §11.7 — without the markdown on disk, the close-out template cited by §11.6 layer 2 is informal).
- **Compose artifacts**: `docker-compose.yml`, `scripts/verify-emulators.{ps1,sh}`, `docker/postgres/init.sql`, `docker/servicebus/Config.json`, `services/*/src/main/resources/application-local-emulator.yml`.
- **Floci-AZ**: [`floci.io/floci-az/services/key-vault/`](https://floci.io/floci-az/services/key-vault/) (Key Vault `ForceHttp` pattern), [`floci.io/az/`](https://floci.io/az/) (Blob endpoint + Monitor KQL subset).
- **Microsoft SB Emulator** ([`Azure/azure-service-bus-emulator-installer`](https://github.com/Azure/azure-service-bus-emulator-installer) compose template; pins `azure-sql-edge:latest` at line 26 per §11.4) + [`microsoft/azure-messaging-servicebus-emulator`](https://hub.docker.com/r/microsoft/azure-messaging-servicebus-emulator) current image + [`MicrosoftDocs/azure-docs`](https://github.com/MicrosoftDocs/azure-docs/blob/main/articles/service-bus-messaging/test-locally-with-service-bus-emulator.md) doc page (last reviewed 2025-10-27, shows `mssql/server:2022-latest` sample).
- **Azure SQL Edge retirement** (per §11.4): [`azure.microsoft.com/updates?id=azure-sql-edge-retirement`](https://azure.microsoft.com/en-us/updates/?id=azure-sql-edge-retirement) — Microsoft announcement, effective 2025-09-30.
- **Upstream installer issues** (driving the community migration per §11.4): [`#18`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/18) (canonical "please migrate" ticket, opened 2024-11-20), [`#122`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/122) (OOM on schema upgrade), [`#124`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/124) (ARM64 motivation), [`#135`](https://github.com/Azure/azure-service-bus-emulator-installer/issues/135) (BufferQueue-1 entity creation fails).
- **Azure resource retirement monitoring** (per §11.8): [`retire.azu.fyi`](https://retire.azu.fyi/) (third-party calendar by [@kongou_ae](https://twitter.com/kongou_ae)), [Azure Advisor Service Retirement workbook](https://learn.microsoft.com/en-us/azure/advisor/advisor-workbook-service-retirement), [Advisor recommendations](https://learn.microsoft.com/en-us/azure/advisor/advisor-how-to-use-service-upgrade-retirement-recommendations), [Azure Service Health alerts](https://learn.microsoft.com/en-us/azure/service-health/service-health-alert-profiles), [Azure Updates RSS — `updateType=retirements`](https://azure.microsoft.com/en-us/updates/?updateType=retirements), [azurefeeds.com/tag/retirements](https://azurefeeds.com/tag/retirements/).

## Status

**ACCEPTED** (Sprint 0, 2026-07-28). Tracking #193. §11.7 hard-prerequisite gate and §11.6 budget-protection layers are consumed by real-Azure apply per ADR-010 §10.4. Pre-validation acceptance criteria live in the emulator pre-validation tracker (#167), not in this ADR.