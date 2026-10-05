#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f "${GR_TEST_JAR:-repository-server/target/graph-repository.jar}" ]] || { echo "Run scripts/build.sh first." >&2; exit 2; }
# Use an isolated venv; dependencies are listed in tests/requirements.txt.
python3 tests/integration.py
