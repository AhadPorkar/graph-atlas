# Capability boundary

[Deutsch](../de/coverage.md) · [Documentation](index.md).

This preview contains an independent local engine. New code does not make it a certified universal registry or a complete replacement for every established repository platform.

## Implemented source

Maven/Gradle, npm, NuGet V3, PyPI and Raw adapters support hosted, proxy and group workflows. OCI/Docker is hosted only. Coverage remains the previous engine's scope; see [client examples](clients.md). The current delivery's protocol tests are core-level. Do not extrapolate them to all public registries and every CLI/version on Spring Boot.

Administration includes repositories, file browsing, uploads, users, grants, tokens, audit, offline maintenance and a local three-language UI. The first-run language is English; German and RTL Persian are selectable.

Release capsules capture file-level manifests across registries. They have explicit states, independent-account approval, revision checks, per-release event chains, Ed25519 receipts, permission-scoped listing and pinned file delivery. A Python gate client and a standalone signature verifier are included.

Manual quarantine is SHA-256-wide and enforced in the binary response layer. Storage analysis reports visible live/unique/pinned bytes and configuration observations. Counting and page-size controls are not commercial quotas.

## Explicitly not included

| Area | Not implemented |
| --- | --- |
| Package coverage | OCI proxy/group; specialized APT, YUM, Composer, Conan, Conda, RubyGems, Cargo and other adapters. Raw storage is not protocol support. |
| Release automation | Dependency-closure discovery, automatic package metadata promotion, automatic CI installation, release deletion/expiry, automatic retention of capsule history. |
| Security services | CVE/malware scanning, license classification, SBOM generation, external attestation providers, HSM, key rotation and certified compliance. |
| Enterprise architecture | HA, multi-node consistency, multi-tenancy, SSO/MFA/LDAP, S3/MinIO and replication. |
| Commercial operations | Billing, automated entitlement service, SLA staffing, managed customer onboarding and a cleared trademark. |
| Verification | Full Boot build/runtime, Java 25, security audit and production performance certification in this delivery environment. |

The immutable object is a recorded manifest and its retained blob reference, not a WORM device that can defeat a root administrator. Two distinct usernames do not establish different human identities. A valid receipt is historical; a live gate is point-in-time. Normal registry URLs remain outside mandatory release approval unless your pipeline enforces it.

All release states pin content, including rejection and revocation. This is conservative data retention, not an automatic storage-saving claim. Deep verification is synchronous. Metadata is held in memory on a single node.

See [release semantics](release-assurance.md), [tests](testing.md), [security](security.md) and [roadmap](roadmap.md).
