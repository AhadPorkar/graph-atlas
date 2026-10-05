[English](../en/references.md) · [Deutsch](../de/references.md) · [Documentation](index.md)

# Referenzen und Herkunft

Für Framework und Repository-Einrichtung herangezogene Primärquellen, geprüft am 2026-10-05:

- [Spring-Boot-Systemanforderungen](https://docs.spring.io/spring-boot/system-requirements.html).
- [Internationalisierung in Spring Boot](https://docs.spring.io/spring-boot/reference/features/internationalization.html).
- [Java 25 Locale.LanguageRange](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.LanguageRange.html).
- [GitHub: Repository anlegen](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository).
- [GitHub: Repository lizenzieren](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

Nützliche Protokollreferenzen, **keine Zertifizierung vollständiger Implementierung**:

- [Maven-Repository-Layout](https://maven.apache.org/repositories/layout.html).
- [Python Simple Repository API](https://packaging.python.org/en/latest/specifications/simple-repository-api/).
- [NuGet-Server-API](https://learn.microsoft.com/en-us/nuget/api/overview).
- [OCI Distribution Specification](https://github.com/opencontainers/distribution-spec).

Verhalten und Grenzen des Projekts werden aus dem ausgelieferten Code und tatsächlich ausgeführten
Tests beschrieben. Framework-Seiten können sich ändern; Versionen stehen ausdrücklich in
`pom.xml` und [`project.lock.json`](../../project.lock.json). Keine Dokumentationsseite verspricht
künftige Kompatibilität.

Grundlage ist der vorherige unabhängige Graph-Repository-Prototyp aus diesem Entwicklungsverlauf.
Das Projekt enthält weder den vollständigen Sonatype-Quellcode noch eine übersetzte offizielle
Nexus-Distribution. Die mitgelieferten Oberflächenressourcen und Testdaten gehören zum Projekt;
Drittanbieter-Abhängigkeiten behalten ihre jeweiligen Lizenzen.
