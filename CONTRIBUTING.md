# Contributing

[Deutsch](CONTRIBUTING.de.md)

Thank you for reviewing Graph Atlas. This is a development preview; changes must preserve
the distinction between implemented behavior and verified runtime compatibility.

## Development workflow

Use JDK 25, Maven and Node 22+. Start with a focused issue describing the observed problem.
For vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
Create a feature branch, keep changes narrow, and add regression tests for changed behavior.

```bash
bash scripts/test-core.sh
npm test
python tests/restore_test.py
python scripts/check_repository.py
mvn -B -ntp clean verify
```

Run the native browser and real HTTP/client suites when changing the UI or transport.
Report tests you could not run; do not mark them as passed. Never weaken checks solely to obtain a
green build. [Testing](docs/en/testing.md) explains the layers.

## Code and documentation

Keep domain logic in `repository-core`; Spring and HTTP concerns belong in `repository-server`.
Stream artifacts rather than silently buffering arbitrary files in memory. Validate authorization
server-side and avoid leaking secrets into logs or diagnostics. Use readable names and explicit
error handling. Preserve existing license attribution.

All UI strings belong in the three JSON catalogs. Keep keys and placeholders identical, test
`en`, `de` and `fa`, and use logical CSS properties. Package identifiers must not be translated.
Update both `docs/en` and `docs/de`; the English README remains the default.
Do not add external fonts, tracking, a CDN dependency or fake production metrics.

## Pull request checklist

Describe the reason, implementation and migration impact. Include actual test commands/results,
screenshots for all affected directions, and any unverified behavior. Check for data, credentials,
generated binaries and unintended changes before pushing. English or German contributions are
welcome. Maintainers may request changes; no response-time or support SLA is promised.
