# OCR Worker

Extracted per ADR-001 §"Currently Extracted Services / Document OCR Worker".

## Why it is extracted

- **Different technology**: Azure AI Document Intelligence SDK is Python-first (`azure-ai-documentintelligence` PyPI package).
- **Failure isolation**: OCR latency (~3–8 s/doc) does not block scoring or case-management latency budgets.
- **Independent scaling**: OCR is the slowest step in the case-creation pipeline. Scale up independently.

## What it owns

| Capability | Backing |
|---|---|
| Consume messages from `documents-pending` queue | `azure-servicebus` Python SDK |
| Download blob from Azure Blob Storage `documents-worm` | `azure-storage-blob` Python SDK |
| Submit to `prebuilt-read` or `prebuilt-document` model | `azure-ai-documentintelligence` |
| Persist OCR result metadata to PostgreSQL `documents` schema | `psycopg` |
| Publish orchestration event to `case-events` topic | `azure-servicebus` |

## What it does **not** own

- The blob itself (created by Ingestion / Frontend)
- The OCR text content storage schema (Core Backend owns `documents` schema migrations)
- Case creation (Core Backend reacts to `case-events` after OCR completes)

## Owned GitHub Issues

- **Implementation**: Sprint 1 ([#4](https://github.com/Team-Centinela/Centinela-Code/issues/4)) + Sprint 3 ([#6](https://github.com/Team-Centinela/Centinela-Code/issues/6))
- Will appear under `ocr` label
- **Dependency pinning**: per ADR-003 §3.1 (issue #26), OCR Worker uses PyPI `azure-servicebus` 7.x async client. Pinned in `services/ocr-worker/pyproject.toml` once implementation lands (sub-issue #40 / epic #37).

## File layout (target)

```
ocr-worker/
├── src/
│   ├── consumers/
│   │   └── documents_pending_consumer.py
│   ├── ports/
│   │   ├── input/        # Azure SB inbound
│   │   └── output/       # Blob, SB, Postgres interfaces
│   ├── adapters/
│   │   ├── blob_storage.py
│   │   ├── document_intelligence_client.py
│   │   ├── service_bus_publisher.py
│   │   └── postgres_metadata.py
│   └── main.py
├── pyproject.toml
└── Dockerfile
```
