#!/usr/bin/env python3
"""Detect breaking OpenAPI contract changes between baseline and candidate specs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

SERVICES = ("backend", "ml-service", "ocr-service")


def load_spec(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def compare_operations(base: dict[str, Any], candidate: dict[str, Any], service: str) -> list[str]:
    issues: list[str] = []
    base_paths = base.get("paths", {})
    candidate_paths = candidate.get("paths", {})

    for path, base_methods in base_paths.items():
        if path not in candidate_paths:
            issues.append(f"[{service}] removed endpoint path: {path}")
            continue

        candidate_methods = candidate_paths[path]
        for method, base_operation in base_methods.items():
            if method not in candidate_methods:
                issues.append(f"[{service}] removed method: {method.upper()} {path}")
                continue

            base_responses = base_operation.get("responses", {})
            candidate_responses = candidate_methods[method].get("responses", {})
            for status_code in base_responses.keys():
                if status_code not in candidate_responses:
                    issues.append(
                        f"[{service}] removed response status {status_code}: {method.upper()} {path}"
                    )

    return issues


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check OpenAPI breaking changes")
    parser.add_argument("--baseline-dir", required=True, help="Directory with baseline contract snapshots")
    parser.add_argument("--candidate-dir", required=True, help="Directory with candidate contract snapshots")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    baseline_dir = Path(args.baseline_dir)
    candidate_dir = Path(args.candidate_dir)

    failures: list[str] = []
    for service in SERVICES:
        baseline = baseline_dir / f"{service}-openapi.json"
        candidate = candidate_dir / f"{service}-openapi.json"

        if not baseline.exists() or not candidate.exists():
            failures.append(f"[{service}] missing contract file(s): baseline={baseline.exists()} candidate={candidate.exists()}")
            continue

        failures.extend(compare_operations(load_spec(baseline), load_spec(candidate), service))

    if failures:
        print("Breaking OpenAPI changes detected:")
        for issue in failures:
            print(f" - {issue}")
        return 1

    print("No breaking OpenAPI changes detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
