# API Contract Snapshots

This directory contains OpenAPI contract snapshots for all Numera platform services.

## Files

| File | Service | Default URL |
|------|---------|-------------|
| `backend_openapi.json` | Spring Boot backend | `http://localhost:8080/v3/api-docs` |
| `ml_service_openapi.json` | ML service (FastAPI) | `http://localhost:8002/openapi.json` |
| `ocr_service_openapi.json` | OCR service (FastAPI) | `http://localhost:8001/openapi.json` |

## Workflow

### Export current contracts

```bash
python scripts/openapi/export_contracts.py --export
```

### Export a single service

```bash
python scripts/openapi/export_contracts.py --export --services backend
```

### Diff against saved snapshots

```bash
python scripts/openapi/export_contracts.py --diff
```

### CI usage

The script uses exit codes for CI integration:

| Code | Meaning |
|------|---------|
| 0 | OK — no breaking changes |
| 1 | Breaking changes detected |
| 2 | Service unavailable / fetch error |

### Breaking change detection

The `--diff` mode detects:

- **Removed paths** — an endpoint that existed in the snapshot is no longer present
- **Removed required fields** — a field marked required in a request/response schema was removed
- **Changed field types** — the `type` of an existing schema property was changed

Non-breaking additions (new paths, new optional fields) are reported as warnings but do not cause a non-zero exit.

## When to update snapshots

Run `--export` and commit the updated snapshots whenever a service's API intentionally changes.
Review the diff in code review to confirm the change is intentional.
