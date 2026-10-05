# Commercialization plan

[Deutsch](../de/commercialization.md) · [Product](product.md).

## What is available to sell, and when?

This repository is a development preview, not a finished enterprise service. The source includes operational tooling and functional core tests, but the delivered environment did not compile or run Spring Boot. Before selling production operation, complete the acceptance work below and define a support capability you can actually maintain.

A proposed first offer is a **paid, scoped evaluation and implementation service**: configure the customer's toolchains, establish two-account release review, integrate the explicit CI gate, rehearse restore and hand over bilingual operating documentation. A customer must be told which functionality is implemented and which deployment tests are still outstanding.

Potential later offers are supported on-premises deployment and a dedicated managed instance. They are proposals, not existing SKUs. The current architecture is not a multi-tenant service; never put mutually untrusted customers into one shared instance as though tenant isolation existed.

## Proposed packaging, not a price list

| Offer | Deliverable | Exclusions that must stay visible |
| --- | --- | --- |
| Evaluation / integration | Isolated test installation, agreed protocols, CI example, measured acceptance results. | No production uptime promise or security certification. |
| Supported self-hosting | Customer-operated instance, installation support, upgrades and restore assistance under an agreed contract. | HA, scanners and SSO are not in the code. Any SLA needs actual staffing and monitoring. |
| Dedicated operation | A separately deployed instance per customer with backup and incident procedures. | No shared tenant boundary; cloud region, key custody and data-processing responsibilities need agreement. |

No market price, gross-margin estimate or competitor cost saving is asserted. Set commercial terms from actual support effort, infrastructure cost, buyer requirements and pilot findings. License counters are not added to the core.

## Acceptance before a production contract

| Gate | Evidence required |
| --- | --- |
| Reproducible runtime build | `mvn clean verify` on the intended JDK; retain dependency resolution and test results. |
| Native-client compatibility | Publish and consume real Maven/Gradle, npm, NuGet, pip and OCI artifacts in the customer's workflow. Do not rely only on endpoint fixtures. |
| Release decisions | Separate author/reviewer accounts, stale revision handling, held digest denial, source-path drift and revocation tests. |
| Key and restore custody | Backup encryption outside the application, restricted key access, restored signing identity and verified pinned bytes. |
| Security review | Authentication, CSRF, authorization, path parsing, registry behavior, upload handling and dependency review by qualified reviewers. |
| Capacity | Agreed package counts, sizes, concurrency, deep-check time, memory and disk pressure measured on target hardware. |
| Browser and deployment | Native CSP/cookie behavior, three-language critical paths, TLS, reverse proxy and actual container/systemd deployment. |
| Business commitments | Cleared product name, license/dependency notices, incident contacts, upgrade policy and realistic support terms. |

This is a release checklist, not a claim that the gates have already passed.

## Positioning and discovery

Test the proposition “a local artifact handover workspace with exact-byte review and independently verifiable records” with teams that already have fragmented approval steps. Ask them to demonstrate their current release handover rather than only rating a mockup. Measure configuration time, decision clarity and restore confidence in a pilot.

Promotion, signing and quarantine already appear in established products. The proposed differentiator is a constrained, locally operated workflow plus multilingual service, not an assertion that these technologies are new. Avoid “better than every registry” comparisons without a reproducible evaluation and current competitor evidence.

Do not promise that lack of license quotas means infinite capacity. Do not describe offline signature validation as live revocation checking. Do not describe a manual hold as an automatic vulnerability scanner.

## Source license, attribution and name

The inherited MIT license is retained. It permits commercial use and sale subject to retaining the copyright and license notice. It also permits recipients to redistribute the MIT-covered code: publishing it publicly does not create exclusivity. Review actual dependency licenses for any binary distribution. A separate support agreement does not erase existing recipients' source-license rights.

No third-party license check has been removed. This project does not require a proprietary repository engine. Existing contributor attribution remains in `LICENSE`. Do not remove it to create a false impression of origin.

“Graph Atlas” is a working name. No trademark registration, domain availability or freedom-to-operate conclusion is provided. Before public commercial branding, obtain an appropriate name/trademark review for your intended markets and confirm ownership of all added assets and contributions.

Primary references reviewed 2026-10-05: [MIT license](https://opensource.org/license/mit) and [Cloudsmith artifact management](https://docs.cloudsmith.com/artifact-management). The latter documents existing promotion and quarantine capabilities; it is not a claim that Atlas has equivalent coverage.
