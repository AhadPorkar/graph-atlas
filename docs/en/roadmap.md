[English](../en/roadmap.md) · [Deutsch](../de/roadmap.md) · [Documentation](index.md)

# Roadmap

Priorities are ordered by risk reduction, not by a promised release date.

## First: verify the delivered runtime

Run the full JDK 25 Maven reactor in GitHub Actions. Resolve any Spring API or dependency
integration issue before tagging a binary release. Run native browser/CSP/cookie tests,
real npm/pip/NuGet/Maven/Gradle/OCI clients and the restore rehearsal.

## Next: strengthen operations

Add representative load and recovery measurements. Review metadata memory growth,
upstream failure behavior, structured error codes and lifecycle shutdown.
Add dependency and image scanning with reviewed remediation policies.

## Then: expand compatibility deliberately

Complete Docker proxy/group if required. Add other adapters one at a time with protocol
fixtures, real client acceptance and clear coverage documentation.
Design a database/object-store backend before considering multi-node operation.

## Ongoing quality

Keep all three UI catalogs and both documentation languages aligned.
Add keyboard and assistive-technology testing, expand cross-browser coverage and refine
German/Persian text through native-speaker review.

No unchecked item is presented as an available feature or a delivery commitment.

## Release-governance follow-up

The preview now includes captures, independent-account approval, evidence and containment. Next work should validate the real runtime, design safe signing-key rotation, asynchronous integrity jobs and explicit capsule retention. Scanner integrations require authentic results and their own trust model; none is simulated as a product capability.
