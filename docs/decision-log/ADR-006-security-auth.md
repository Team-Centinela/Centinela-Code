# ADR-006: Security & Auth

## Status

**ACCEPTED** — Ratified during Sprint 0 ADR review ([#16](https://github.com/Team-Centinela/Centinela-Code/issues/16)). Tracked at [#3](https://github.com/Team-Centinela/Centinela-Code/issues/3). Implementation sub-issues #44 (Ingestion API key auth), #45 (Idempotency-Key filter), #46 (Key Vault wiring), #47 (Auth module boundary), and #48 (ACA managed identity / SWA auth) roll up to epic #43.

## Context

The platform has four distinct authentication and authorization surfaces:

| Surface | Consumer | Requirement |
|---|---|---|
| **Ingestion API** | External fintech clients submitting transactions | API key per tenant; `Idempotency-Key` header (RFC 9110) mandatory on every `POST /transactions`; reject with `400` if missing; key validated against `auth.api_keys` table |
| **Core Backend (analyst UI)** | Internal analysts via Azure Static Web Apps | Azure AD / Entra ID authentication via SWA built-in auth; role-based access (`ANALYST`, `ADMIN`) mapped from AAD groups; session via SWA-managed cookie |
| **Serverless Engine / OCR Worker** | Backend-to-backend (no human) | Azure Container Apps **managed identity**; Service Bus `Manage` policy via Key Vault secret; PostgreSQL via managed identity + Azure AD token (no password in config) |
| **Infrastructure / CI/CD** | GitHub Actions, Terraform | Service principal with least-privilege RBAC; secrets only in GitHub Environments + Key Vault; no secrets in repo |

Decision drivers:

1. **Budget**: $60 / 21 days — no API Management tier, no dedicated identity provider. Use Azure-native, zero-additional-cost auth mechanisms.
2. **Operational simplicity**: 4-person team, no SRE. Managed identity eliminates secret rotation for service-to-service. SWA built-in auth eliminates custom login code for the analyst portal.
3. **Compliance** (`ASSIGNMENT.md` §E, §T.4): API keys must be rotatable, auditable, and stored encrypted. `Idempotency-Key` must be enforced at the HTTP boundary per RFC 9110 §9.3.1.
4. **Module boundary discipline** (`../architecture/02-modular-monolith.md`): Auth logic lives in the **Auth module** (`auth` schema) inside the Core Backend monolith; extracted services consume it via managed identity, not by sharing the schema.

## Decision

### 6.1 Ingestion API — API Key Authentication

- **Header**: `X-API-Key: <tenant-api-key>`
- **Validation**: Spring `OncePerRequestFilter` reads header → looks up `auth.api_keys` where `key_hash = SHA-256(header_value)` and `revoked_at IS NULL` and `expires_at > NOW()`.
- **Tenant context**: On success, filter populates `SecurityContext` with `Authentication` principal carrying `tenantId`, `clientId`, and `roles = ["INGESTION_CLIENT"]`.
- **Revocation**: `revoked_at` timestamp set by admin UI; filter respects it immediately (no cache).
- **Rate limiting**: Not in scope for 21-day budget; rely on ACA HTTP scaler + Service Bus back-pressure. Revisit in V2.
- **DB-down contract**: While PostgreSQL is stopped (per ADR-002 §Planned DB downtime), the filter's `SELECT` fails → Ingestion API returns `503 Service Unavailable` + `Retry-After` header. This is the Auth-filter side of the cross-cutting DB-down contract.

### 6.2 Idempotency-Key Header Contract (RFC 9110 §9.3.1)

Every `POST /api/v1/transactions` **must** include:

```
Idempotency-Key: <client-generated-UUID-v4>
```

| Condition | Response |
|---|---|
| Header missing | `400 Bad Request` — `{"error":"IDEMPOTENCY_KEY_REQUIRED","message":"Idempotency-Key header is required"}` |
| Header malformed (not UUID v4) | `400 Bad Request` — `{"error":"IDEMPOTENCY_KEY_INVALID"}` |
| Key seen within 24 h (same `tenantId`) | `200 OK` with **original response body** (cached), `Idempotency-Key` echoed back |
| Key seen but different request hash | `409 Conflict` — `{"error":"IDEMPOTENCY_KEY_REUSE","message":"Idempotency-Key reused with different payload"}` |

**Storage**: `auth.idempotency_keys` table (schema `auth`):

```sql
CREATE TABLE auth.idempotency_keys (
    tenant_id     UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash  CHAR(64) NOT NULL,         -- SHA-256 of canonical request body
    response_body JSONB NOT NULL,             -- cached successful response
    status_code   SMALLINT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, idempotency_key)
);
CREATE INDEX ON auth.idempotency_keys (created_at) WHERE created_at > NOW() - INTERVAL '24 hours';
```

TTL: 24 hours. A nightly cron (`@Scheduled(cron="0 3 * * *")`) deletes rows older than 24 h.

**Why not Service Bus deduplication alone?** Service Bus de-dup is broker-level (sliding window, `messageId`). The HTTP contract requires **application-level** idempotency visible to the client (replay with same key returns same response). See `../patterns/06-idempotency-key.md` §"Idempotency by Layer".

### 6.3 Azure Key Vault — Secret Management

All secrets live in a single Key Vault (`centinela-kv-<env>`). Access policies:

| Principal | Permissions | Purpose |
|---|---|---|
| `centinela-aca-mi` (ACA managed identity, shared by all 4 backends) | `get` on `secrets` | Read `servicebus-connection-string`, `postgresql-admin-password` (bootstrap only), `document-intelligence-key` |
| `centinela-swa-mi` (SWA managed identity) | `get` on `secrets` | Read `aad-client-secret` for SWA built-in auth config |
| GitHub Actions `ID_TOKEN` (OIDC) | `set`/`delete` on `secrets` | CI/CD rotates `servicebus-connection-string` on key rollover; no human ever sees the value |

**Naming convention**: `<service>-<secret>-<rotation-name>` — e.g., `ingestion-servicebus-connection-string`, `core-postgresql-admin-password`, `ocr-document-intelligence-key`.

**No secrets in**:
- Terraform state (Key Vault references via `azurerm_key_vault_secret` data sources only)
- Docker images
- GitHub repo (`.env`, `application.yml`, `settings.xml`)
- Application Insights logs (sanitized by `LogFilter` in `../best-practices/04-logging-and-monitoring.md`)

### 6.4 Auth Module Boundary (Core Backend Monolith)

The **Auth module** lives inside the Core Backend modular monolith (`modules/auth/`) and owns the `auth` PostgreSQL schema exclusively.

| Schema | Tables | Owner |
|---|---|---|
| `auth` | `api_keys`, `idempotency_keys`, `users`, `roles`, `role_assignments` | Auth module only |

**Ports exposed by Auth module** (Java interfaces in `application/` + `port/`):

```java
// Inbound — used by Ingestion API filter (via REST) and SWA auth config (via internal call)
public interface ApiKeyValidationPort {
    Optional<ApiKeyPrincipal> validate(String keyHash);
}

// Inbound — used by admin UI (Case Module controller)
public interface ApiKeyManagementPort {
    ApiKey create(TenantId tenantId, String name, Duration ttl);
    void revoke(ApiKeyId id);
    Page<ApiKey> list(TenantId tenantId, Pageable pageable);
}

// Outbound — implemented by JpaApiKeyRepository in adapter/persistence/
public interface ApiKeyRepository {
    Optional<ApiKeyEntity> findByKeyHash(String keyHash);
    void save(ApiKeyEntity entity);
}
```

**No other module imports `auth.domain` or `auth.application`**. Cross-module auth needs (e.g., Case Module checking `ANALYST` role) are resolved by:

1. SWA passes authenticated user principal via `X-MS-CLIENT-PRINCIPAL` header → Case Module `RestController` reads it.
2. For service-to-service (Serverless Engine → Core Backend), the call is **async via Service Bus event** — no sync auth check needed. The event carries `tenantId` and `correlationId`; the consumer trusts the broker.

### 6.5 Extracted Service Authentication (ACA Managed Identity + SWA)

| Service | Identity | Resource Access |
|---|---|---|
| **Ingestion API** (ACA) | System-assigned MI `centinela-ingestion-mi` | Key Vault (`get` secrets), PostgreSQL (Azure AD token via `azure-identity` + `pgjdbc`), Service Bus (`Manage` via connection string from KV) |
| **Serverless Engine** (ACA) | System-assigned MI `centinela-engine-mi` | Key Vault (`get`), PostgreSQL (Azure AD token), Service Bus (`Manage` + `Listen` on `transactions-raw`) |
| **Core Backend** (ACA) | System-assigned MI `centinela-core-mi` | Key Vault (`get`), PostgreSQL (Azure AD token), Service Bus (`Manage` + `Send` on `case-events`, `Listen` on `case-events/*`) |
| **OCR Worker** (ACA) | System-assigned MI `centinela-ocr-mi` | Key Vault (`get`), PostgreSQL (Azure AD token), Service Bus (`Listen` on `documents-pending`), Document Intelligence (Azure AD token) |
| **Frontend** (SWA) | SWA built-in auth (Entra ID app registration `centinela-swa-app`) | User sign-in → SWA sets `X-MS-CLIENT-PRINCIPAL` header → Core Backend reads roles from header |

**Terraform**: each ACA resource gets `identity { type = "SystemAssigned" }`. Key Vault access policies are granted to the MI principal IDs via `azurerm_key_vault_access_policy`. PostgreSQL `azure_ad_authentication = true` on the Flexible Server; each MI is granted `azure_ad_admin` + `pg_read_all_data` / `pg_write_all_data` via `azuread_group_membership` (group `centinela-db-writers` / `centinela-db-readers`).

## Consequences

### Positive

- **Zero secret in code or IaC state** — all secrets in Key Vault, accessed via MI.
- **API key auth is stateless and fast** — single indexed lookup, no JWT validation overhead.
- **Idempotency at HTTP boundary** satisfies RFC 9110 and protects downstream from duplicate ingestion.
- **Auth module isolation** — schema-per-module discipline preserved; extracted services never touch `auth` tables directly.
- **SWA built-in auth** eliminates custom login/logout/session code for the analyst portal.
- **Managed identity everywhere** — no password rotation, no connection string leakage, works with PostgreSQL Flexible Server Azure AD auth.

### Negative

- **API key in header** — not as strong as mTLS; acceptable for 21-day pilot with HTTPS-only endpoints. Rotate keys weekly via admin UI.
- **Idempotency key storage** adds a write per ingestion request (same transaction as `transactions` + `outbox_events`). Negligible on B1ms.
- **SWA auth header parsing** couples Core Backend to SWA-specific `X-MS-CLIENT-PRINCIPAL` format. Mitigated by a thin `SwaPrincipalExtractor` adapter in `auth.adapter.rest`.
- **Managed identity token acquisition** adds ~50 ms latency on first DB/Service Bus call after cold start. Acceptable for bursty workloads.

### Mitigations

| Negative | Mitigation |
|---|---|
| API key in header | Enforce HTTPS (ACA ingress + SWA), short TTL (default 30 days), admin revocation UI. |
| Idempotency write overhead | Single transaction with business write; indexed lookup is < 2 ms on B1ms. |
| SWA header coupling | Extractor is a 20-line adapter; swapping to APIM later only touches that adapter. |
| MI cold-start latency | ACA min-replicas = 1 during test hours (issue #10); token cached by `DefaultAzureCredential` for 5 min. |

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Azure API Management (Consumption) | Costs ~$15/mo even idle; exceeds budget headroom. |
| JWT tokens issued by Auth module for Ingestion API | Adds token issuance/validation complexity; API key is simpler and sufficient for server-to-server. |
| Shared PostgreSQL user/password in Key Vault (no MI) | Password rotation operational burden; MI is native and free. |
| Custom OAuth2 / OIDC provider (IdentityServer, Keycloak) | Operational overhead; 3-week project cannot absorb. |
| Service Bus SAS keys in app config | SAS keys cannot be rotated without downtime; MI + KV rotation is zero-downtime. |

## References

- `ASSIGNMENT.md` §E (Persistence strategy), §T.4 (Architectural justification), §3 (Budget)
- `../architecture/02-modular-monolith.md` — Module boundaries, schema-per-module
- `../architecture/03-hexagonal-architecture.md` — Port/adapter contracts for Auth module
- `../architecture/06-technology-stack.md` — Key Vault, ACA MI, SWA auth
- `../patterns/06-idempotency-key.md` — Idempotency layers, HTTP `Idempotency-Key` header
- `ADR-002-postgresql-only-db.md` — PostgreSQL Azure AD auth
- `ADR-003-async-messaging-reliability.md` — Service Bus access via MI
- `ADR-009-compute-substrate-container-apps-static-web-apps.md` — ACA MI, SWA auth
- `infrastructure/README.md` — Cost guardrails, Key Vault ownership
- RFC 9110 §9.3.1 — `Idempotency-Key` header semantics
- Issues: [#3](https://github.com/Team-Centinela/Centinela-Code/issues/3) ADR-006 tracker, [#43](https://github.com/Team-Centinela/Centinela-Code/issues/43) epic, [#44–#48](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aauth) sub-issues

## Status

**ACCEPTED** (Sprint 0, 2026-07-17) — addresses blockers #44–#48 raised in [#16](https://github.com/Team-Centinela/Centinela-Code/issues/16) Sprint 0 review. This ADR clusters with [#3](https://github.com/Team-Centinela/Centinela-Code/issues/3) Auth & Secrets epic.