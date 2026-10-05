# Product concept

[Deutsch](../de/product.md) · [Documentation](index.md).

## Positioning hypothesis

**Graph Atlas is a self-hosted handover workspace for development teams that need to know which exact artifacts were reviewed and released.** It combines existing package-tool workflows with a small, visible release-governance layer.

This is a product hypothesis, not evidence of product-market fit. Proposed initial customers are teams that accept a single-server deployment, operate mixed Java/JavaScript/.NET/Python builds, and prefer local control with English, German or Persian administration. This preview is not suited to a buyer who requires HA, SSO, formal compliance certification or integrated vulnerability intelligence.

## Outcome-oriented tour

The mission control page leads with release decisions, not server settings. Release lanes show captured, in-review, approved and published capsules. An engineer captures explicit files in Artifact explorer. A different authorized account reviews the immutable selection. An author publishes it and exports the signed receipt. A pipeline checks current eligibility and downloads pinned bytes.

Storage intelligence explains reference-level deduplication and historical bytes retained by capsules. Containment provides a reasoned manual response to a known digest across aliases. Neither screen presents a synthetic security grade. Configuration signals describe actual settings rather than guessing whether an installation is compliant.

The proposed differentiation is the **combination and delivery experience**: locally operated storage, compact release workflow, externally verifiable receipts, honest accounting and three-language administration. Signing, promotion and quarantine already exist in established products; they are not claimed as inventions or unique capabilities.

## Demonstration: eight minutes

Use two distinct test accounts and non-sensitive artifacts. Publish two files, capture a capsule and submit it. Show that the author's approval is rejected. Approve from the reviewer account, then publish from the author account. Replace a mutable source path and demonstrate that the pinned download still contains the captured bytes. Apply a digest hold and show a failed gate/download. Resolve it and verify a receipt against a separately pinned key.

The demo must distinguish package endpoints from pinned release endpoints. Show source drift and the historical nature of receipts. Screenshots under `docs/assets` are synthetic previews, not customer installations or benchmark results.

## What not to claim

Do not describe the preview as a universal package platform, zero-trust solution, malware scanner, certified supply-chain platform, fully isolated SaaS, or globally novel technology. Do not promise unlimited capacity because no commercial quota exists. Do not say a signature proves code quality, an artifact is safe, or the application is production-qualified.

A potential commercial advantage is a focused service and usability proposition. It has to be tested with customers and a production-quality implementation, not inferred from a new logo.

## Evaluation criteria

For a pilot, measure time to configure one toolchain, successful native-client publish/install, time to review a capsule, stale-decision rejection, corrupt-file detection, blocked held downloads, restore success and operator comprehension. Record actual latency and resources at the customer's agreed workload. No reference throughput or uptime figure has been measured for this delivery.

See [commercialization](commercialization.md), [release assurance](release-assurance.md) and [coverage](coverage.md).
