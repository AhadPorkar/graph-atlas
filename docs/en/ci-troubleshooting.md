# CI repair: scoped npm paths and CSP-safe browser tests

[Deutsch](../de/ci-troubleshooting.md) | [Verification report](testing.md).

## Evidence from the failed run

The uploaded archive `logs_101023669665.zip` contains all three job logs for
run `37298864318`, commit `c25472be9f32415f199cdedd0664105d09bd6676`.
Both Java 21 and Java 25 compiled the core and server and started Spring Boot
4.1.1 with Tomcat 11.0.24. Each server module ran 20 tests with one failure:
`TomcatWireTest.streamingRangesScopedPathsAndStatelessAuthentication:74`.
The request `PUT /repository/npm-hosted/@wire%2Fdemo` expected 201 and received
400, `BAD_PATH`, `Request rejected by the HTTP firewall`.
The core's four JUnit entry points passed. This is a runtime assertion failure,
not a dependency-download, compilation or application-context startup error.
See [the extracted diagnosis](../qa/ci-fix2-diagnosis.txt).

These logs concern the **unfixed** commit. They are not evidence that this
patch, later packaging steps, or the real-client integration suite has passed.

## Java repair

In `ServletContainerConfiguration.java`, change:

```java
connector.setEncodedSolidusHandling("passthrough");
```

to:

```java
connector.setEncodedSolidusHandling("decode");
connector.setEncodedReverseSolidusHandling("reject");
```

`passthrough` leaves `%2F` in the servlet mapping path. Spring Security's
strict firewall rejects residual `%` in that decoded-path view, independently
of the encoded-slash allowance. Tomcat must supply the decoded mapping path.

`HttpServletRequest.getRequestURI()` remains the original, encoded URI.
`RequestAuditFilter` applies `RequestPaths.decode()` to that original URI
before the security filter chains, not to the already-decoded servlet path.
These are two independently derived views of the original URI, not sequential
double decoding. The raw-URI guard still rejects an encoded separator outside
the package namespace, encoded repository names, traversal, repeated slashes,
percent-encoded percent signs, backslashes and other ambiguous forms.

Do not enable `setAllowUrlEncodedPercent(true)`, clear firewall blocklists,
replace the strict firewall, remove raw-URI validation, or ignore the failing
test. No such change is made by this patch.

## Browser repair retained

The uploaded browser log independently fails in `Page.wait_for_function`:
Playwright's polling evaluation conflicts with `script-src 'self'`.
All seven polling call sites use retrying locator assertions instead:

```python
from playwright.sync_api import expect
expect(page.locator("html")).to_have_attribute("lang", "de")
```

Neither `unsafe-eval` nor `bypass_csp` is enabled. The workflow still requires
native browser navigation; bridge mode is an explicit local-only alternative,
not a replacement for the CI test. Failure screenshots and traces are retained.

## Regression coverage

`RequestPathPolicyTestMain` adds 30 executed raw-URI checks, including accepted
scoped names, preservation of literal `+`, and rejection of ambiguous paths.
It runs through `CoreRegressionTest` in Maven and through the dependency-free
Linux and PowerShell core scripts.

`ServletPathFirewallTest` adds three Maven tests for residual percent rejection,
decoded servlet-path acceptance with an unchanged raw URI, and retained strict
blocklists. These tests have been written but not run in this environment.

`TomcatWireTest` retains the original failing publication assertion and adds
alternate encodings, byte-identical retrieval through encoded and literal
paths, and rejected-path checks with and without credentials. The new and old
wire assertions must be executed by the real Maven build; mock requests and
core-only tests do not prove connector behavior.

## Re-run on a JDK-equipped machine or GitHub Actions

```bash
# Full reactor: also tests real embedded Tomcat. No test skipping.
mvn -B -ntp -e -Djava.version=21 clean verify
# With JDK 25 active:
mvn -B -ntp -e -Djava.version=25 clean verify

# Package-manager and server integration after a successful Maven build:
python tests/integration.py

# Native browser fixture suite:
python tests/csp_assertions_test.py
python tests/ui_test.py
```

Maven diagnostics in CI save stdout/stderr, preserve failure codes through
`tee`, and upload reports from both modules even after failures. Inspect the
**new commit's run**, not a rerun of the old unpatched commit. Later failures
remain possible and should be diagnosed from their first actual error.

## Validation of this delivery

246 core checks passed on OpenJDK 21.0.11 (216 existing plus 30 new raw-URI
checks), as did 31 frontend tests, eight CSP tests, 14 gate-client tests,
12 restore tests and four evidence-verifier tests. The native browser attempt
was blocked by the environment with `ERR_BLOCKED_BY_ADMINISTRATOR`.
Maven and JDK 25 are unavailable locally; full post-fix Spring Boot/Tomcat
execution and hosted CI are **pending**, not reported as successful.
See [the current validation report](../qa/ci-fix2-validation.json).
The earlier [CSP-only report](../qa/ci-hotfix-validation.json) is historical.

## References

- [Tomcat connector: encodedSolidusHandling](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html).
- [Servlet API: raw request URI and servlet path](https://tomcat.apache.org/tomcat-11.0-doc/servletapi/jakarta/servlet/http/HttpServletRequest.html).
- [Spring Security StrictHttpFirewall](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/firewall/StrictHttpFirewall.html).
- [Spring Security implementation](https://github.com/spring-projects/spring-security/blob/main/web/src/main/java/org/springframework/security/web/firewall/StrictHttpFirewall.java).
- [Playwright locator assertions](https://playwright.dev/python/docs/api/class-locatorassertions).
