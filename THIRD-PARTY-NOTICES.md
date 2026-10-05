# Third-party notices and attribution

[Deutsch](THIRD-PARTY-NOTICES.de.md)

The original project MIT notice is preserved in [LICENSE](LICENSE). Changes in this source
distribution are provided under that project license. Do not remove existing attribution when
publishing or redistributing it.

The complete project source is included, but external dependencies are resolved at build time.
Spring Boot, Spring Framework, Spring Security, Tomcat, Micrometer, JUnit and test tooling retain
their respective licenses and notices. JDK and container base images also have their own terms.
The project MIT license does not relicense those components.

No third-party font files, bundled Nexus binary, external runtime JavaScript library or private
package data is included. The CSS, SVG mark and screenshots are project assets; screenshots use
synthetic fixtures and are not evidence of customer deployments.

Before redistributing a built application or image, inspect its actual resolved dependency set,
licenses and notices. This file is not a complete generated SBOM or a legal clearance report:

```bash
mvn -B -ntp dependency:tree
```

Sonatype Nexus, npm, NuGet, Python, Maven, Gradle, Docker, Java, Spring and GitHub names identify
products or compatibility targets. They do not imply sponsorship, endorsement or trademark rights.
The project does not remove or bypass another product's commercial licensing.
