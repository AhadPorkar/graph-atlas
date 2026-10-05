#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ "$#" -ne 2 ]; then
  echo 'Usage: bash scripts/verify-evidence.sh ENVELOPE.json TRUSTED_PUBLIC_KEY_SHA256' >&2
  exit 2
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v javac >/dev/null || { echo 'A full JDK 21+ is required.' >&2; exit 2; }
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/atlas-evidence.XXXXXX")"
trap 'rm -rf "$BUILD"' EXIT
SRC="$ROOT/repository-core/src/main/java/ir/graph/repo/core"
javac --release 21 -encoding UTF-8 -d "$BUILD" "$SRC/util/Json.java" "$SRC/release/EvidenceEnvelope.java" "$SRC/release/EvidenceVerifierMain.java"
java -cp "$BUILD" ir.graph.repo.core.release.EvidenceVerifierMain "$1" "$2"
