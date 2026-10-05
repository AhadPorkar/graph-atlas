#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
MAJOR="$(javac -version 2>&1 | awk '{print $2}' | cut -d. -f1)"
[[ "$MAJOR" == 25 ]] || { echo "This verification requires an actual JDK 25, not a compatibility guess." >&2; exit 2; }
java -version
JAVA_RELEASE=25 bash "$ROOT/scripts/build.sh"
printf '\nJava 25 Maven verification completed. Review Surefire reports for individual results.\n'
