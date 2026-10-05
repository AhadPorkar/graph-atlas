[English](../en/decisions.md) · [Deutsch](../de/decisions.md) · [Documentation](index.md)

# Architecture decisions

## ADR-001: Keep the core independent of the web framework

**Decision:** domain services and protocol logic remain in a plain Java module.
Spring MVC adapts Servlet requests to the core protocol exchange.

**Reason:** storage and package contracts can be tested without starting a container.
**Trade-off:** a small adapter layer must map exceptions, streams and HTTP semantics correctly.
Full runtime tests are still necessary; core tests do not prove Servlet behavior.

## ADR-002: Use single-node content-addressed storage

**Decision:** SHA-256 blobs plus a local transactional metadata log and snapshots.
**Reason:** a small inspectable persistence layer and digest-based deduplication.
**Trade-off:** a memory-resident index and one-writer deployment are not HA.
A database/object-store backend would require explicit consistency and recovery design.

## ADR-003: Make English an explicit default

**Decision:** a stored or explicitly requested language overrides English; browser/OS language does not.
**Reason:** deployments and portfolio demonstrations have deterministic first-run behavior.
**Trade-off:** users must select German or Persian once instead of relying on browser detection.

## ADR-004: Separate translated UI from package protocol data

**Decision:** translate labels and administrative error messages, not coordinates or machine keys.
**Reason:** package clients must receive stable protocol contracts regardless of the console language.
**Trade-off:** developer-facing diagnostic details remain English.

## ADR-005: Preserve draft state during language changes

**Decision:** rebuild dialog presentation while restoring entered values, selected files and
locally held one-time token content. Do not repeat a write request during retranslation.
**Reason:** changing language must not discard work or create another token.
**Trade-off:** dependent controls need regression tests; this is not just text replacement.

## ADR-006: Do not claim runtime results that were not observed

**Decision:** ship executable test definitions and record what actually ran.
**Reason:** a source archive and a syntax parse are not proof of a working Spring Boot release.
**Trade-off:** the public repository initially has a clearly labeled development status until CI
and real-client acceptance succeed.
