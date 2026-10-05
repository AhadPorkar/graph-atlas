# Verification report — 0.5.0-preview

[Deutsch](../de/testing.md) · [Documentation](index.md).

Recorded 2026-10-05. Results below refer to the delivered source, not a customer installation. Machine-readable and console evidence lives in [the QA directory](../qa/).

## CI repair follow-up

The uploaded CI logs now establish the pre-fix Java failure: the server compiled
and started under both Java 21 and Java 25, with 19 of 20 server tests passing.
Scoped npm publication returned 400 instead of 201 because of incompatible
Tomcat servlet-path handling. The connector is corrected and additional path
regressions are included. See [CI diagnosis and current validation](ci-troubleshooting.md).
The new raw-URI suite adds 30 executed checks, bringing local core checks to 246.
The corrected full Maven/Tomcat run remains pending; the observed pre-fix CI
results must not be presented as post-fix validation. Historical results below
retain their original scope.


## Executed

| Suite | Result | Scope |
| --- | --- | --- |
| `SelfTest` | 47 checks passed. | JSON, hashing, storage, paths, locks and recovery. |
| `CoreContractTestMain` | 77 checks passed. | Existing protocol and service behavior. |
| `LocalizationTestMain` | 24 checks passed. | Language negotiation. |
| `ReleaseContractTestMain` | 68 checks passed. | Captures, rights, separate-account approval, revisions, signature trust and tampering, frozen data, holds, retention, concurrency and restart. |
| `node --test tests/frontend/*.test.mjs` | 31 tests passed. | Three catalogs, interpolation, client examples and capture parsing. |
| `python tests/release_gate_test.py` | 14 tests passed. | Client against a local synthetic HTTP server; redirects and ambiguous responses fail closed. |
| `python tests/evidence_cli_test.py` | 4 tests passed. | Delivered shell/Java verifier with ephemeral real Ed25519 keys. |
| `python tests/restore_test.py` | 12 tests passed. | Archive safety, non-overwrite restore and private signing-file preservation. |
| `python tests/ui_test.py --bridge` | 116 checks passed. | Real UI code, three languages, release lanes, forms, containment, responsive layout and synthetic HTTP responses. |
| `java scripts/ParseJava.java .` | 73 source files parsed. | Syntax only; dependencies are not resolved. |

Java checks ran on OpenJDK 21.0.11 and frontend checks on Node 22.16.0. The core checks compile actual core sources and execute actual storage, digest, signature and permission logic. The UI and CI-client fixture suites use synthetic backend data; they do not replace runtime integration tests.

Native Chromium navigation was attempted and blocked with `ERR_BLOCKED_BY_ADMINISTRATOR`. The bridge suite does not verify native cookie enforcement, native ES-module loading, CSP or the real Spring security chain. Screenshots show labelled synthetic data.

## Not executed or not established

Maven is unavailable and Maven Central was not resolvable from the delivery environment. The full Spring Boot 4.1.1 reactor was not compiled or run here. Java 25, actual Docker/systemd/Nginx/Windows deployment, production load, a penetration test and every real package client against the new runtime have not been verified.

`ReleaseMvcTest`, the existing MVC tests and `TomcatWireTest` are included for the Maven build, but are **written tests, not reported passes**. GitHub workflows are source configuration, not proof that hosted CI has run. A Java syntax parser is not a substitute for compiling Spring MVC/Security signatures.

## Reproduce

```bash
bash scripts/test-core.sh
npm test
python tests/release_gate_test.py
python tests/evidence_cli_test.py
python tests/restore_test.py
python scripts/check_repository.py

# Full runtime qualification; requires a full JDK and Maven dependencies.
bash scripts/build.sh
python tests/integration.py

# Chromium must be installed; fixture server, not Spring Boot.
python -m pip install -r tests/requirements.txt
python -m playwright install chromium
python tests/ui_test.py
```

Use bridge mode only where native navigation is blocked; record that limitation. The evidence verifier suite needs Bash and JDK 21+. PowerShell scripts are included but were not executed here.

## Release acceptance

The [commercialization checklist](commercialization.md) defines pre-production gates. In addition, run this scenario against the real server: create two different users, publish a file with a real client, capture and submit it, reject self-approval, approve independently, publish, verify evidence with an out-of-band key, replace the live file, compare frozen bytes, apply a hold, test GET/HEAD/range/cache conditions, resolve, revoke, back up and restore. Confirm the signing identity survives restore.

A fresh extraction must pass `python scripts/check_repository.py --manifest` before modification. The source packaging script produces a per-file SHA-256 manifest; it does not compile the application or authenticate the distributor.
