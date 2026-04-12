#!/usr/bin/env python3
"""
OpenAPI contract export and diff tool for the Numera platform.

Usage:
    python export_contracts.py --export                        Export all service contracts
    python export_contracts.py --export --services backend ml  Export specific services
    python export_contracts.py --diff                          Diff live APIs against saved snapshots
    python export_contracts.py --diff --services backend       Diff a specific service

Exit codes:
    0  OK (no breaking changes)
    1  Breaking changes detected
    2  Service unavailable / fetch error
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

try:
    import requests
except ImportError:
    print(
        "ERROR: 'requests' package is required. "
        "Install via: pip install -r scripts/openapi/requirements.txt",
        file=sys.stderr,
    )
    sys.exit(2)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent
CONTRACTS_DIR = PROJECT_ROOT / "docs" / "contracts"

SERVICE_CONFIGS: dict[str, dict[str, str]] = {
    "backend": {
        "url": os.environ.get(
            "NUMERA_BACKEND_OPENAPI_URL", "http://localhost:8080/v3/api-docs"
        ),
        "filename": "backend_openapi.json",
    },
    "ml": {
        "url": os.environ.get(
            "NUMERA_ML_OPENAPI_URL", "http://localhost:8002/openapi.json"
        ),
        "filename": "ml_service_openapi.json",
    },
    "ocr": {
        "url": os.environ.get(
            "NUMERA_OCR_OPENAPI_URL", "http://localhost:8001/openapi.json"
        ),
        "filename": "ocr_service_openapi.json",
    },
}

FETCH_TIMEOUT_SECONDS = 15

# ---------------------------------------------------------------------------
# Fetching
# ---------------------------------------------------------------------------


def fetch_openapi(service_name: str) -> dict[str, Any] | None:
    """Fetch the OpenAPI spec from a running service. Returns None on failure."""
    config = SERVICE_CONFIGS[service_name]
    url = config["url"]
    try:
        resp = requests.get(url, timeout=FETCH_TIMEOUT_SECONDS)
        resp.raise_for_status()
        return resp.json()
    except requests.ConnectionError:
        print(
            f"  ERROR: Could not connect to {service_name} at {url}", file=sys.stderr
        )
    except requests.Timeout:
        print(
            f"  ERROR: Timeout connecting to {service_name} at {url}", file=sys.stderr
        )
    except requests.HTTPError as exc:
        print(
            f"  ERROR: HTTP {exc.response.status_code} from {service_name} at {url}",
            file=sys.stderr,
        )
    except (json.JSONDecodeError, ValueError):
        print(
            f"  ERROR: Invalid JSON response from {service_name} at {url}",
            file=sys.stderr,
        )
    return None


# ---------------------------------------------------------------------------
# Export
# ---------------------------------------------------------------------------


def export_contracts(services: list[str]) -> int:
    """Fetch and save OpenAPI specs. Returns exit code."""
    CONTRACTS_DIR.mkdir(parents=True, exist_ok=True)
    any_failure = False

    for svc in services:
        print(f"Fetching {svc} OpenAPI spec...")
        spec = fetch_openapi(svc)
        if spec is None:
            any_failure = True
            continue

        dest = CONTRACTS_DIR / SERVICE_CONFIGS[svc]["filename"]
        dest.write_text(
            json.dumps(spec, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(f"  Saved -> {dest.relative_to(PROJECT_ROOT)}")

    return 2 if any_failure else 0


# ---------------------------------------------------------------------------
# Diff / breaking-change detection
# ---------------------------------------------------------------------------


class BreakingChange:
    """A single breaking change entry."""

    def __init__(self, category: str, path: str, detail: str) -> None:
        self.category = category
        self.path = path
        self.detail = detail

    def __str__(self) -> str:
        return f"[{self.category}] {self.path}: {self.detail}"


def _collect_schema_properties(
    schema: dict[str, Any], prefix: str = ""
) -> dict[str, dict[str, Any]]:
    """Recursively collect property paths and their type info from a JSON Schema."""
    props: dict[str, dict[str, Any]] = {}
    for name, defn in schema.get("properties", {}).items():
        full = f"{prefix}.{name}" if prefix else name
        props[full] = defn
        if defn.get("type") == "object":
            props.update(_collect_schema_properties(defn, full))
    return props


def _find_breaking_changes(
    old: dict[str, Any], new: dict[str, Any]
) -> tuple[list[BreakingChange], list[str]]:
    """Compare two OpenAPI specs and return (breaking_changes, warnings)."""
    breaking: list[BreakingChange] = []
    warnings: list[str] = []

    old_paths = set(old.get("paths", {}).keys())
    new_paths = set(new.get("paths", {}).keys())

    # --- Removed paths ---
    for removed in sorted(old_paths - new_paths):
        breaking.append(
            BreakingChange("REMOVED_PATH", removed, "Endpoint was removed")
        )

    # --- New paths (non-breaking) ---
    for added in sorted(new_paths - old_paths):
        warnings.append(f"New endpoint added: {added}")

    # --- Check shared paths for method / schema changes ---
    for path in sorted(old_paths & new_paths):
        old_methods = old["paths"][path]
        new_methods = new["paths"][path]

        for method in sorted(set(old_methods.keys()) | set(new_methods.keys())):
            if method.startswith("x-"):
                continue
            op_path = f"{method.upper()} {path}"

            if method in old_methods and method not in new_methods:
                breaking.append(
                    BreakingChange(
                        "REMOVED_METHOD", op_path, "HTTP method was removed"
                    )
                )
                continue
            if method not in old_methods:
                warnings.append(f"New method added: {op_path}")
                continue

            old_op = old_methods[method]
            new_op = new_methods[method]

            # Compare request body schemas
            _compare_schemas(
                old_op.get("requestBody", {}),
                new_op.get("requestBody", {}),
                f"{op_path} requestBody",
                breaking,
                warnings,
            )

            # Compare response schemas
            old_responses = old_op.get("responses", {})
            new_responses = new_op.get("responses", {})
            for status in sorted(
                set(old_responses.keys()) | set(new_responses.keys())
            ):
                if status in old_responses and status in new_responses:
                    _compare_schemas(
                        old_responses[status],
                        new_responses[status],
                        f"{op_path} response[{status}]",
                        breaking,
                        warnings,
                    )

    # --- Check top-level component schemas ---
    old_schemas = old.get("components", {}).get("schemas", {})
    new_schemas = new.get("components", {}).get("schemas", {})

    for schema_name in sorted(set(old_schemas.keys()) | set(new_schemas.keys())):
        if schema_name in old_schemas and schema_name not in new_schemas:
            breaking.append(
                BreakingChange(
                    "REMOVED_SCHEMA",
                    f"#/components/schemas/{schema_name}",
                    "Schema was removed",
                )
            )
            continue
        if schema_name not in old_schemas:
            warnings.append(f"New schema added: {schema_name}")
            continue

        _compare_schema_properties(
            old_schemas[schema_name],
            new_schemas[schema_name],
            f"#/components/schemas/{schema_name}",
            breaking,
            warnings,
        )

    return breaking, warnings


def _extract_schema(content_block: dict[str, Any]) -> dict[str, Any]:
    """Extract the schema dict from a requestBody or response content block."""
    content = content_block.get("content", {})
    for media_type in ("application/json", "application/hal+json", "*/*"):
        if media_type in content:
            return content[media_type].get("schema", {})
    # Fallback: first content type
    if content:
        first = next(iter(content.values()))
        return first.get("schema", {})
    return {}


def _compare_schemas(
    old_block: dict[str, Any],
    new_block: dict[str, Any],
    context: str,
    breaking: list[BreakingChange],
    warnings: list[str],
) -> None:
    old_schema = _extract_schema(old_block)
    new_schema = _extract_schema(new_block)
    if old_schema or new_schema:
        _compare_schema_properties(old_schema, new_schema, context, breaking, warnings)


def _compare_schema_properties(
    old_schema: dict[str, Any],
    new_schema: dict[str, Any],
    context: str,
    breaking: list[BreakingChange],
    warnings: list[str],
) -> None:
    old_props = _collect_schema_properties(old_schema)
    new_props = _collect_schema_properties(new_schema)

    old_required = set(old_schema.get("required", []))
    new_required = set(new_schema.get("required", []))

    # Removed required fields
    for field in sorted(old_required - new_required):
        if field not in new_props:
            breaking.append(
                BreakingChange(
                    "REMOVED_REQUIRED_FIELD",
                    f"{context}.{field}",
                    "Required field was removed",
                )
            )

    # Type changes on existing fields
    for field in sorted(set(old_props.keys()) & set(new_props.keys())):
        old_type = old_props[field].get("type")
        new_type = new_props[field].get("type")
        if old_type and new_type and old_type != new_type:
            breaking.append(
                BreakingChange(
                    "TYPE_CHANGED",
                    f"{context}.{field}",
                    f"Type changed from '{old_type}' to '{new_type}'",
                )
            )

    # New fields (non-breaking)
    for field in sorted(set(new_props.keys()) - set(old_props.keys())):
        warnings.append(f"New field added: {context}.{field}")


def diff_contracts(services: list[str]) -> int:
    """Diff live OpenAPI specs against saved snapshots. Returns exit code."""
    has_breaking = False
    any_failure = False

    for svc in services:
        snapshot_path = CONTRACTS_DIR / SERVICE_CONFIGS[svc]["filename"]
        print(f"\n{'=' * 60}")
        print(f"Diffing {svc}...")
        print(f"{'=' * 60}")

        if not snapshot_path.exists():
            print(
                f"  SKIP: No saved snapshot at "
                f"{snapshot_path.relative_to(PROJECT_ROOT)}"
            )
            print("        Run --export first to create the baseline.")
            continue

        live_spec = fetch_openapi(svc)
        if live_spec is None:
            any_failure = True
            continue

        saved_spec = json.loads(snapshot_path.read_text(encoding="utf-8"))
        breaking_changes, warnings = _find_breaking_changes(saved_spec, live_spec)

        for w in warnings:
            print(f"  WARN: {w}")

        if breaking_changes:
            has_breaking = True
            for bc in breaking_changes:
                print(f"  BREAK: {bc}")
            print(
                f"\n  {len(breaking_changes)} breaking change(s) detected in {svc}!"
            )
        else:
            print(f"  OK: No breaking changes in {svc}.")

    if any_failure:
        return 2
    return 1 if has_breaking else 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Numera OpenAPI contract export & diff tool.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument(
        "--export",
        action="store_true",
        help="Fetch and save OpenAPI specs to docs/contracts/",
    )
    action.add_argument(
        "--diff",
        action="store_true",
        help="Compare live specs against saved snapshots",
    )

    parser.add_argument(
        "--services",
        nargs="+",
        choices=list(SERVICE_CONFIGS.keys()),
        default=list(SERVICE_CONFIGS.keys()),
        help="Which services to process (default: all)",
    )
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    if args.export:
        rc = export_contracts(args.services)
    else:
        rc = diff_contracts(args.services)

    sys.exit(rc)


if __name__ == "__main__":
    main()
#!/usr/bin/env python3
"""Export canonical OpenAPI contracts for backend, ml-service, and ocr-service."""

from __future__ import annotations

import argparse
import importlib
import json
import os
import signal
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def export_fastapi(service_dir: Path, output_file: Path) -> None:
    for module_name in ("app.main",):
        if module_name in sys.modules:
            del sys.modules[module_name]

    sys.path.insert(0, str(service_dir))
    try:
        module = importlib.import_module("app.main")
        app = getattr(module, "app")
        spec = app.openapi()
    finally:
        sys.path.pop(0)

    write_json(output_file, spec)


def wait_for_backend_openapi(
    url: str,
    timeout_seconds: int = 150,
    proc: subprocess.Popen[str] | None = None,
) -> dict[str, Any]:
    start = time.time()
    last_error: Exception | None = None

    while time.time() - start < timeout_seconds:
        if proc is not None and proc.poll() is not None:
            raise RuntimeError(f"Backend process exited before OpenAPI became available (exit code {proc.returncode})")

        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status == 200:
                    return json.loads(response.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            last_error = exc
        time.sleep(2)

    raise TimeoutError(f"Timed out waiting for backend OpenAPI: {url}. Last error: {last_error}")


def stop_process(proc: subprocess.Popen[str]) -> None:
    if proc.poll() is not None:
        return

    proc.terminate()
    try:
        proc.wait(timeout=15)
        return
    except subprocess.TimeoutExpired:
        pass

    if os.name == "nt":
        proc.kill()
    else:
        proc.send_signal(signal.SIGKILL)
    proc.wait(timeout=5)


def export_backend(output_file: Path) -> None:
    env = os.environ.copy()
    env.setdefault("NUMERA_OPENAPI_EXPORT_MODE", "true")
    env.setdefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/numera")
    env.setdefault("SPRING_DATASOURCE_USERNAME", "numera")
    env.setdefault("SPRING_DATASOURCE_PASSWORD", "numera")
    env.setdefault("SPRING_DATA_REDIS_HOST", "localhost")
    env.setdefault("SPRING_DATA_REDIS_PORT", "6379")

    cmd = [*resolve_gradle_command(), "-p", str(ROOT / "backend"), "bootRun", "--no-daemon"]
    proc = subprocess.Popen(  # noqa: S603
        cmd,
        cwd=str(ROOT),
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.STDOUT,
        text=True,
    )

    try:
        spec = wait_for_backend_openapi("http://localhost:8080/v3/api-docs", proc=proc)
        write_json(output_file, spec)
    finally:
        stop_process(proc)


def resolve_gradle_command() -> list[str]:
    if os.name == "nt":
        bundled = ROOT / "backend" / ".gradle-dist" / "gradle-8.10.2" / "bin" / "gradle.bat"
    else:
        bundled = ROOT / "backend" / ".gradle-dist" / "gradle-8.10.2" / "bin" / "gradle"

    if bundled.exists():
        return [str(bundled)]

    gradle = shutil.which("gradle")
    if gradle:
        return [gradle]

    raise FileNotFoundError("Gradle executable not found. Install Gradle or provide backend/.gradle-dist.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export canonical OpenAPI contracts")
    parser.add_argument(
        "--output-dir",
        default=str(ROOT / "docs" / "contracts"),
        help="Directory where contract snapshots will be written",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    export_backend(output_dir / "backend-openapi.json")
    export_fastapi(ROOT / "ml-service", output_dir / "ml-service-openapi.json")
    export_fastapi(ROOT / "ocr-service", output_dir / "ocr-service-openapi.json")

    print(f"Exported OpenAPI contracts to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
