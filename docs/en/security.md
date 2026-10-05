[English](../en/security.md) · [Deutsch](../de/security.md) · [Documentation](index.md)

# Security model and review boundaries

Security controls are implemented in source, but this delivery has not passed an independent
penetration test or a full Spring Boot runtime test. See [Testing](testing.md). Do not expose it
to the public Internet merely because the UI loads.

## Trust boundaries

The browser is untrusted. The Java server validates permissions and input independently of
buttons or client-side checks. Package requests require explicit credentials or explicitly
allowed anonymous read access. Repository grants and user roles are enforced in the core.
Tokens are displayed once and can be revoked; never place them in URLs, local storage or logs.

Browser sessions use an HttpOnly cookie, CSRF checks and origin validation. HTTPS deployments
must set `GR_COOKIE_SECURE=true` and the exact external `GR_PUBLIC_URL`. Default English and
language negotiation are presentation choices, not authorization decisions.

Static translations are same-origin JSON. Dynamic data is HTML-escaped. Diagnostics can include
technical identifiers; do not disclose private package names when reporting errors. Client example
generation escapes shell/XML/Groovy contexts but does not make an arbitrary command safe to run.

## Upstream and filesystem protection

HTTP and private-network upstreams are denied unless explicitly enabled. Review outbound network
policy independently: application URL checks are not a replacement for egress filtering or a
comprehensive DNS-rebinding audit. Avoid giving a repository service access to cloud metadata
addresses or internal control planes.

Storage uses controlled paths and digest-addressed files. The process should have the minimum
filesystem privileges. Keep persistent data outside the source tree. Backups contain confidential
package and account information even when tokens/passwords are not stored as plaintext.

Disabling upload, JSON or concurrency safeguards removes resource protections. It does not create
a license limitation, and retaining them does not create a commercial quota.

## Before external use

Run the complete JDK 25/Spring Boot tests and real clients; verify denied operations as well as
successful publication. Review secrets, TLS, password policy, account lifecycle, malformed archive
handling, large body behavior, upstream isolation and backup recovery. Restrict Actuator exposure.
Test load and failure recovery using representative datasets. A single-node in-memory metadata
index is not an HA design.

Report suspected vulnerabilities privately using the process in [SECURITY.md](../../SECURITY.md),
not a public issue containing exploit details or real credentials.

## Release trust boundary

Capsule approval enforces a different account name, not proof of two independent humans. Administrators can provision identities and operating-system administrators can alter local state. Hash-linked events make accidental modification detectable but are not an externally anchored, append-only audit service. A root operator with the signing key can forge new receipts. Establish key trust out of band and restrict host/key access.

The signed receipt covers historical release data. It is not a live revocation answer or vulnerability scan. The CI gate is current only at check time; normal package URLs are not approval-gated. A manual digest hold blocks subsequent binary reads but does not retract prior downloads, interrupt already-started streams, or necessarily suppress package metadata.

Deep checks synchronously read blobs under the store lock. Treat that endpoint as potentially expensive; do not provide untrusted users unrestricted access to large repository sets. Existing concurrency controls and reverse-proxy timeouts remain relevant. No new commercial quota is imposed.

Protect `evidence-key.json` and every backup. Automated rotation, hardware key custody, signed external audit anchoring and tenant isolation are absent. See [the release trust model](release-assurance.md).
