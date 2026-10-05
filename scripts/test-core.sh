#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
BUILD="$ROOT/.build/core-checks"
mkdir -p "$BUILD/classes" "$ROOT/docs/qa"
find repository-core/src/main/java -name '*.java' -print > "$BUILD/sources.txt"
printf '%s\n' repository-core/src/test/java/ir/graph/repo/core/SelfTest.java repository-core/src/test/java/ir/graph/repo/core/CoreContractTestMain.java repository-core/src/test/java/ir/graph/repo/core/LocalizationTestMain.java repository-core/src/test/java/ir/graph/repo/core/ReleaseContractTestMain.java >> "$BUILD/sources.txt"
javac --release 21 -encoding UTF-8 -d "$BUILD/classes" @"$BUILD/sources.txt"
cp -R repository-core/src/test/resources/. "$BUILD/classes/"
java -cp "$BUILD/classes" ir.graph.repo.core.SelfTest | tee docs/qa/core-self-tests.txt
java -cp "$BUILD/classes" ir.graph.repo.core.CoreContractTestMain | tee docs/qa/core-contract-tests.txt
java -cp "$BUILD/classes" ir.graph.repo.core.LocalizationTestMain | tee docs/qa/localization-java.txt
java -cp "$BUILD/classes" ir.graph.repo.core.ReleaseContractTestMain | tee docs/qa/release-contract-tests.txt
printf '\nCore-only tests completed. This does NOT compile, start or validate Spring Boot.\n'
