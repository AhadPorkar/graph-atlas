#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null || { echo "A complete JDK is required." >&2; exit 2; }
command -v mvn >/dev/null || { echo "Maven 3.6.3+ is required. Maven is not bundled." >&2; exit 2; }
RELEASE="${JAVA_RELEASE:-25}"
[[ "$RELEASE" == 21 || "$RELEASE" == 25 ]] || { echo "JAVA_RELEASE must be 21 or 25." >&2; exit 2; }
MAJOR="$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)"
[[ "$MAJOR" =~ ^[0-9]+$ && "$MAJOR" -ge "$RELEASE" ]] || { echo "JDK $RELEASE or later is required for this target; current javac: $MAJOR." >&2; exit 2; }
mvn -B -ntp -Djava.version="$RELEASE" clean verify "$@"
JAR="repository-server/target/graph-repository.jar"
[[ -f "$JAR" ]] || { echo "Maven completed without producing the expected Spring Boot JAR." >&2; exit 1; }
mkdir -p dist
cp "$JAR" dist/graph-repository.jar
if command -v sha256sum >/dev/null; then (cd dist && sha256sum graph-repository.jar > graph-repository.jar.sha256); fi
printf '\nBuilt actual Spring Boot archive: %s/dist/graph-repository.jar\n' "$ROOT"
