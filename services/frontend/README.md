# Frontend (Analyst Dashboard)

Static SPA, served from CDN. Consult `../../docs/architecture/01-overview.md`  "External Entry Points" for the scope.

## Why static SPA

- ADR-001 chose Modular Monolith without frontend concerns in mind; Frontend is a thin presentation layer on top of public APIs.
- Static delivery permits easy cost minimization: $0 hosting from Azure Static Web Apps free tier (no APIM, no premium tier) — fits the $60 budget window.

## What it owns

| Capability | Backing |
|---|---|
| Analyst dashboard (case list, case detail) | HTML/CSS/JS |
| Document upload UI (POST → Ingestion or directly → Blob SAS URL) | Direct browser-to-blob upload |
| Auth (delegates to Core Backend `app.auth` module) | OIDC redirect |

## What it does **not** own

- *Any* business rule logic
- Storage of fraud cases (Core Backend owns PostgreSQL `cases` schema)
- Document storage (Blob Storage account, uploaded docs)

## Owned GitHub Issues

- **Implementation**: Sprint 3 ([#6](https://github.com/Team-Centinela/Centinela-Code/issues/6))

## File layout (target)

```
frontend/
├── src/
│   ├── pages/
│   ├── components/
│   ├── lib/
│   └── main.ts
├── public/
├── package.json
└── Dockerfile
```
