#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
JAR="${GR_JAR:-$ROOT/dist/graph-repository.jar}"
[[ -f "$JAR" ]] || JAR="$ROOT/repository-server/target/graph-repository.jar"
[[ -f "$JAR" ]] || { echo "Build first: bash scripts/build.sh. No prebuilt Spring Boot JAR is included." >&2; exit 2; }
# Use JAVA_TOOL_OPTIONS for JVM flags; no eval or shell expansion of arbitrary option strings.
exec java -jar "$JAR" "$@"
