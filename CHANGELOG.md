# Changelog

[Deutsch](CHANGELOG.de.md)

## 0.5.0-preview — 2026-10-05

Independent Graph Atlas identity and release-oriented workspace. Added multi-registry file capsules, separate-account approval, revision-safe decisions, Ed25519 receipts, pinned downloads, digest quarantine, storage analysis, offline verifier and fail-closed CI client. Added product/commercialization documentation in English and German. All three UI catalogs contain 381 keys. New metadata tables require a backup-based rollback strategy. Full Spring Boot runtime remains unverified in the delivery environment.


## 0.4.0-SNAPSHOT — 2026-10-05

### Added
- English, German and Persian UI catalogs; English is the explicit fresh-session default.
- Language switching on login, in the header and inside dialogs, with persisted preferences.
- RTL Persian layout, directional isolation for technical strings and regional formatting.
- Form/file/one-time-token preservation across language changes.
- Localized administrative API errors with stable codes and language response headers.
- English/German technical guides, project policies, GitHub templates and source packaging.
- Locale, frontend and browser-fixture regression tests.

### Changed
- Project presentation and artifact name are now `graph-repository`.
- The administrative frontend was reorganized into readable ES modules and semantic markup.
- New users start without implicit repository grants.
- Client setup examples distinguish read-only proxy/group endpoints from hosted publication.

### Verification boundary
Core and frontend checks ran on the documented local toolchain. Full Spring Boot, Java 25,
Docker and real clients against this runtime remain unverified in the delivery environment.
No new protocol-family or production-readiness claim accompanies this UI/documentation release.

## 0.3.0-spring — predecessor

Introduced the Spring Boot MVC/Security transport and independent core/server modules.
This historical entry identifies the source baseline, not a certified public release.

## 0.2.0 — predecessor

Independent single-node repository engine and Persian console. Existing storage compatibility
is discussed in the migration guide; no automatic Nexus migration is provided.
