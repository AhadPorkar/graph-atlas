[English](../en/portfolio.md) · [Deutsch](../de/portfolio.md) · [Documentation](index.md)

# Portfolio walkthrough

Use the repository as an inspectable engineering case study, not as an assertion of production
experience or authorship you cannot substantiate. Customize your profile and describe your actual
contributions, including review and validation of generated code where applicable.

## Suggested project summary

> An independent Java/Spring Boot package repository prototype with a framework-neutral storage
> core, six package families, repository-level access control, and an English/German/Persian console.
> The frontend defaults to English, preserves form state during language changes, and mirrors its
> layout for Persian. The repository includes bilingual documentation and layered test definitions.

The words **prototype** and the [verification boundaries](testing.md) matter. Do not claim certified
client compatibility, production deployments, millions of requests or a passed security audit.

## A five-part demonstration

1. Explain the core/server split and content-addressed storage using [Architecture](architecture.md).
2. Show English login, switch to German, then Persian to demonstrate mirrored layout and unchanged
   package identifiers. Explain language fallback rather than only showing translated labels.
3. Open a repository form, enter values and change language without losing state. Show that a
   selected upload file and issued token are not recreated during translation.
4. Discuss the distinction between hosted, proxy and group repositories and why OCI proxy/group
   is explicitly excluded. Show least-privileged user grants instead of an all-powerful demo account.
5. Run the available tests and describe what they do **not** prove. Review the roadmap and one
   architectural trade-off rather than presenting unmeasured performance claims.

The included screenshots use synthetic data. They can illustrate UI work but cannot prove a running
Spring Boot server. Record a real runtime demo only after building and verifying the application.

## Discussion topics

Useful interview topics include atomic metadata updates, streaming uploads versus memory bounds,
SSRF boundaries for proxies, CSRF versus token-authenticated clients, token lifecycle, directional
isolation for technical strings, and why JSON catalogs are independent of package protocol payloads.

[Decision records](decisions.md) capture current trade-offs. [Roadmap](roadmap.md) prioritizes runtime
verification before new features. Add measured evidence and a factual contribution history as work
continues. Do not invent maintainers, customers, stars, benchmarks or CI results.
