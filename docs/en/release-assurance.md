# Release assurance

[Deutsch](../de/release-assurance.md) · [Documentation](index.md).

## One capsule, exact bytes

A capsule is a manifest of explicit `repo` and `path` references captured at creation. The server resolves each to format, SHA-256, length and content type, then stores the sorted manifest and its digest. Files are not copied: their content-addressed blobs are retained.

The selection is deliberately explicit. It does not discover all transitive dependencies, assemble a Docker image, infer which npm tarball matches metadata, or automatically include Maven POM/checksum sidecars. For a complete deployment, include every required file or provide a separately prepared deployment archive. This is file-level release governance, not format-level repository promotion.

Names and versions are ASCII technical identifiers; notes can be multilingual. The name/version pair is unique. The author needs both `read` and `write` on every source registry.

## State and authority

```text
DRAFT ----submit----> IN_REVIEW ----approve----> APPROVED ----release----> RELEASED
                         |                                                  |
                       reject                                             revoke
                         |                                                  |
                         v                                                  v
                      REJECTED                                           REVOKED
```

A capture begins at revision 1. Each transition requires the current `expectedRevision` and a nonempty decision note. A stale request receives HTTP 409 rather than overwriting another decision.

The author or an administrator with source-write access may submit, release and revoke. Approval or rejection needs `read` plus `approve` on every source registry and a username different from the author's. A `write` grant does not imply `approve`. Administrator status does not bypass self-approval denial. The implementation separates accounts, not verified human identities; administrators who can create accounts remain trusted.

A reviewer can be configured through the Users page with:

```json
{
  "raw-hosted": ["read", "approve"],
  "maven-releases": ["read", "approve"]
}
```

The terminal `REJECTED` and `REVOKED` states cannot be reset. Create a new version for a new review. Capsule deletion is not implemented.

## API walkthrough

Publish files through their normal package-manager protocols or a hosted Raw/Maven endpoint. Then create a capsule:

```http
POST /api/releases
Content-Type: application/json
Authorization: Bearer <author-token>
```

```json
{
  "name": "checkout-service",
  "version": "2026.10.0",
  "note": "Deployment candidate for the acceptance environment.",
  "assets": [
    {"repo": "raw-hosted", "path": "releases/checkout.zip"},
    {"repo": "raw-hosted", "path": "releases/config.json"}
  ]
}
```

The returned UUID identifies the capsule. Call these endpoints in order with the appropriate author/reviewer credentials:

```text
POST /api/releases/{id}/transitions/submit
POST /api/releases/{id}/transitions/approve
POST /api/releases/{id}/transitions/release
```

Each request body has this shape; use the revision returned by the preceding response, not a hardcoded sequence in production:

```json
{"expectedRevision": 1, "note": "Please review the frozen manifest."}
```

Listing and detail are permission-scoped. A caller must read every source registry to see a multi-registry capsule. Listing supports `state`, `offset` and `limit` (maximum page size 200, not a commercial quota).

## Gate and pinned delivery

`GET /api/releases/{id}/gate?deep=true` reads the frozen files, verifies SHA-256 and sizes, examines holds and the event chain, and verifies the release signature. `allowed` is true only for a released capsule that passes these checks. `sourceDrift` records replaced or removed live paths; it is informational because frozen delivery still uses the captured digest.

The Python CI client checks `releaseId`, a boolean `allowed`, `deep=true`, state, signature validity, integrity and problems. It refuses redirects and remote HTTP. A timeout, unauthenticated request, wrong identifier, malformed response or inconsistent decision fails closed. Set `GR_TOKEN` via your CI secret store. The example workflow is [here](../../examples/ci/atlas-release-gate.yml).

**Add this gate explicitly to your pipeline.** Ordinary registry endpoints continue to serve permitted packages without mandatory capsule approval. The gate does not deploy software and does not lock state for the remainder of the pipeline.

Once released, `GET /api/releases/{id}/assets/{index}` serves the pinned file. A later change to its original repository path cannot change those bytes. Use a trusted receipt to verify each downloaded digest on the client. A revoke or active hold denies subsequent frozen downloads, but cannot retract files already downloaded or bytes already streaming.

## Evidence and external trust

`GET /api/releases/{id}/evidence` returns an Atlas-specific JSON envelope. The signature covers the exact UTF-8 payload bytes. The payload contains the frozen manifest and recorded decisions through release. The envelope uses Ed25519, base64-encoded payload/signature and an X.509-encoded public key. `keyId` is the SHA-256 of that encoded public key.

This is **not DSSE, SLSA, Sigstore, a transparency log or a certified timestamp**. The schema is `graph-atlas.evidence/v1`; payload type is `application/vnd.graph-atlas.release.v1+json`. Canonicalization recursively orders JSON object keys for this implementation; it is not advertised as RFC 8785.

An administrator obtains public identity information from `GET /api/release-signing-key`. Establish its fingerprint through a separately authenticated channel and pin it in the verifier's configuration. Do not automatically trust the key supplied inside the same untrusted evidence file.

```bash
bash scripts/verify-evidence.sh evidence.json TRUSTED_PUBLIC_KEY_SHA256
```

The verifier requires a full JDK 21+ but no Maven, external library or live server. Windows has `verify-evidence.ps1`. Successful output explicitly contains `currentRevocationChecked:false` and `artifactBytesChecked:false`: a valid historical signature does not establish those facts.

## Key custody, retention and cost

The instance key is created lazily at `GR_HOME/evidence-key.json`, persisted atomically and given owner-only permissions where supported. Windows ACLs must be configured by the operator. Backups contain this private key and must be encrypted and access-controlled externally. A missing key with existing signed capsules fails closed; deleting it is not a supported rotation mechanism. Automated rotation, HSM integration and key recovery without a backup are not provided.

All capsule manifests pin their blobs, including drafts, rejected and revoked capsules. Garbage collection cannot reclaim those referenced blobs. Removing a registry referenced by any capsule is refused. Plan capacity; there is no expiry or capsule-purge workflow in this preview.

Deep verification is synchronous and holds the single-node store lock while reading files. Large capsules can delay writes. Run acceptance benchmarks with representative sizes and set operational timeouts; no throughput guarantee is included.
