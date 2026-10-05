# CI hotfix: CSP-safe browser waits and Maven diagnostics

[Deutsch](../de/ci-troubleshooting.md) | [Verification report](testing.md).

## Scope

This patch addresses the browser error reported for commit `c25472be9f32415f199cdedd0664105d09bd6676`, run `37298864318`.
It is based on the previously delivered source ZIP; it has not been pushed to GitHub by the assistant.
The complete Java failure log is not available in the supplied excerpt. **This patch does not claim to fix the Java jobs.**
Production Java, the Spring Boot version, CSRF rules and the application CSP are unchanged.

## Confirmed browser cause

The supplied traceback fails in `Page.wait_for_function`, not in application translation code.
Playwright 1.57.0 evaluates its polling expression with `globalThis.eval` in the page context.
The application's `script-src 'self'` does not allow that evaluation.
Adding an arrow function to the expression is not a sufficient fix: it uses the same polling implementation.

All seven such call sites in `tests/ui_test.py` now use retrying locator assertions:

```python
from playwright.sync_api import expect

expect(page.locator("html")).to_have_attribute("lang", "de")
expect(page.locator("#content h1")).to_have_text(expected_heading)
expect(page.locator("#loginError")).not_to_have_text("")
expect(page.locator(".release-lane")).to_have_count(1)
expect(page.locator("#dialog")).not_to_be_visible()
```

The production policy is not weakened. The workflow does not enable `bypass_csp`, add `unsafe-eval`, skip failing tests, or substitute bridge-mode UI tests for native navigation.
The browser suite now records a trace and, on failure, a screenshot and traceback while the browser is still open.
CI uploads fresh test evidence, not marketing screenshots or historical passing reports.

## Additional regression suite

```bash
python tests/csp_assertions_test.py
```

Eight tests execute in Chromium with `page.set_content` and a restrictive meta CSP.
Only the exact fixture script is allowed by a SHA-256 hash; eval remains prohibited.
The eval probe is triggered by a real page click, not CDP evaluation, which can have different CSP behavior.
Tests exercise asynchronous language, heading, error text, lane-count and dialog assertions, check that a wrong language fails, and guard against CSP weakening.
This is **not** a test of Spring Boot, native HTTP navigation or HTTP cookies.

## Maven diagnostics

The workflow still builds the complete reactor on Java 21 and Java 25.
It records the actual Java/Maven versions and runs Maven with `-e` and `-DtrimStackTrace=false`.
Both stdout and stderr are saved in `maven-java-21.log` or `maven-java-25.log`.
The log is outside `target`, so `mvn clean` cannot remove it.
`set -euo pipefail` preserves a failed build through `tee`.
A failing Maven step is followed by log-tail and Surefire-report output; artifacts include the full Maven log and module reports.
No dependency version was changed without the missing error evidence.

To retrieve the already failed run with your authenticated GitHub CLI:

```powershell
gh run view 37298864318 --repo AhadPorkar/graph-atlas --job 111726530739 --log-failed |
    Set-Content -Encoding utf8 "$HOME\Downloads\graph-atlas-java21-failed.txt"
```

For all failed steps, omit `--job 111726530739`.
Review diagnostic files for credentials before sharing them. An excerpt of `PASS` lines does not identify a compiler, dependency, context-startup or test failure.

## Workflow maintenance

The runner label is now `ubuntu-24.04`, rather than a migrating `ubuntu-latest`.
Actions were moved to verified Node 24-based majors: checkout v5, setup-node v5, setup-python v6, setup-java v5 and upload-artifact v6.
These action runtimes are separate from the application Node/Java versions.
The deprecation warnings in the original run are not evidence of the Maven failure cause.
For stricter supply-chain pinning, separately review and pin action commit SHAs.

## Validation performed for this patch

- Core-only Java 21 checks: 216 passed. The core implementation was not changed.
- Frontend unit tests: 31 passed.
- New real Chromium CSP regression tests: 8 passed, none skipped.
- Existing UI interactions through the explicit local DOM/HTTP bridge: 116 passed.
- Release gate client: 14 tests passed; restore helper: 12; signed-evidence CLI: 4.
- Workflow YAML/Bash and simulated Maven exit-code/logging behavior: 7 checks passed. This is not a Maven build.

Native HTTP navigation was attempted and blocked by the environment with `ERR_BLOCKED_BY_ADMINISTRATOR`.
Maven is not installed, Maven Central does not resolve here, and JDK 25 is not installed.
**The full native browser workflow, Maven reactor, Java 25 and GitHub-hosted run are not claimed as passed.**
See [the machine-readable patch report](../qa/ci-hotfix-validation.json).

## References

- [Playwright locator assertions](https://playwright.dev/python/docs/api/class-locatorassertions).
- [Playwright 1.57.0 polling implementation](https://github.com/microsoft/playwright/blob/v1.57.0/packages/playwright-core/src/server/frames.ts).
- [Maven Surefire test parameters](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html).
- [GitHub CLI run logs](https://cli.github.com/manual/gh_run_view).
