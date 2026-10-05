# Drittanbieterhinweise und Quellenangaben

[English](THIRD-PARTY-NOTICES.md)

Der ursprüngliche MIT-Hinweis bleibt in [LICENSE](LICENSE) erhalten. Änderungen dieses Quellpakets
stehen unter derselben Projektlizenz. Vorhandene Namens- und Lizenzhinweise dürfen bei
Veröffentlichung oder Weitergabe nicht entfernt werden.

Der vollständige Projektquellcode ist enthalten; externe Abhängigkeiten werden beim Build aufgelöst.
Spring Boot, Spring Framework, Spring Security, Tomcat, Micrometer, JUnit und Testwerkzeuge
behalten ihre eigenen Lizenzen und Hinweise. Auch JDK und Container-Basisimages haben eigene
Bedingungen. Die MIT-Lizenz des Projekts lizenziert diese Komponenten nicht neu.

Das Paket enthält keine fremden Schriftdateien, Nexus-Binärdatei, externe JavaScript-Laufzeitbibliothek
oder privaten Paketdaten. CSS, SVG-Zeichen und Screenshots sind Projektressourcen.
Screenshots zeigen synthetische Fixtures und keine Kundeninstallationen.

Vor Weitergabe einer gebauten Anwendung oder eines Images sind tatsächlich aufgelöste
Abhängigkeiten, Lizenzen und Hinweise zu prüfen. Diese Datei ist weder eine vollständige erzeugte
SBOM noch eine rechtliche Freigabe:

```bash
mvn -B -ntp dependency:tree
```

Namen wie Sonatype Nexus, npm, NuGet, Python, Maven, Gradle, Docker, Java, Spring und GitHub
bezeichnen Produkte oder Kompatibilitätsziele. Sie bedeuten weder Unterstützung noch Empfehlung
oder Markenrechte. Das Projekt umgeht keine kommerzielle Lizenz eines anderen Produkts.
