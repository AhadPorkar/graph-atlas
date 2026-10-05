[English](../en/references.md) · [Deutsch](../de/references.md) · [Documentation](index.md)

# References and provenance

Primary references consulted for the framework and repository setup, checked on 2026-10-05:

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- [Spring Boot internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html).
- [Java 25 Locale.LanguageRange](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.LanguageRange.html).
- [GitHub: creating a repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository).
- [GitHub: licensing a repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

Useful protocol references, **not certifications of implementation completeness**:

- [Maven repository layout](https://maven.apache.org/repositories/layout.html).
- [Python Simple Repository API](https://packaging.python.org/en/latest/specifications/simple-repository-api/).
- [NuGet server API](https://learn.microsoft.com/en-us/nuget/api/overview).
- [OCI Distribution Specification](https://github.com/opencontainers/distribution-spec).

Project behavior and limitations are documented from the shipped source and executed tests.
Framework pages may change; versions are explicitly recorded in `pom.xml` and
[`project.lock.json`](../../project.lock.json). No documentation page implies future compatibility.

This project derives from the preceding independent Graph Atlas prototype supplied in this
development thread. It is not a fork containing Sonatype's complete source or a translated official
Nexus distribution. The interface assets and test fixtures shipped here are project assets;
third-party dependencies remain under their respective licenses.
