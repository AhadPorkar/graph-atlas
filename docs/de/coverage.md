# Funktionsgrenzen

[English](../en/coverage.md) · [Dokumentation](index.md).

Die Vorschau besitzt eine eigenständige lokale Engine. Neue Funktionen sind kein Nachweis einer zertifizierten Universalplattform oder vollständiger Gleichwertigkeit mit allen etablierten Registry-Produkten.

## Implementierter Quellcode

Maven/Gradle, npm, NuGet V3, PyPI und Raw unterstützen lokale, Proxy- und Gruppenabläufe. OCI/Docker ist ausschließlich lokal gehostet. Der Protokollumfang entspricht dem bisherigen Kern; siehe [Clientbeispiele](clients.md). Die hier ausgeführten Protokolltests liegen auf Kernebene. Daraus folgt keine geprüfte Kompatibilität mit jeder öffentlichen Registry oder jeder CLI-Version auf Spring Boot.

Die Administration umfasst Registries, Dateien, Uploads, Benutzer, Rechte, Tokens, Audit, Offline-Wartung und eine lokale dreisprachige UI. Englisch ist die Ausgangssprache. Deutsch und RTL-Persisch sind auswählbar.

Capsules erfassen dateibasierte Manifeste über mehrere Registries. Enthalten sind explizite Zustände, Freigabe durch ein anderes Konto, Revisionskontrolle, Ereignisketten, Ed25519-Nachweise, berechtigungsgesteuerte Listen und feste Downloads. Python-Gate und eigenständige Signaturprüfung liegen bei.

Manuelle Quarantäne gilt für einen SHA-256-Digest und wird in der binären Antwortschicht durchgesetzt. Die Speicheranalyse zeigt sichtbare aktuelle, eindeutige und gebundene Bytes sowie Konfigurationshinweise. Seitengrößen sind keine Lizenzquoten.

## Nicht enthalten

| Bereich | Nicht implementiert |
| --- | --- |
| Paketprotokolle | OCI-Proxy/Gruppen; spezielle APT-, YUM-, Composer-, Conan-, Conda-, RubyGems-, Cargo- und weitere Adapter. Raw ersetzt kein Protokoll. |
| Release-Automation | Transitive Abhängigkeitsauflösung, automatische Metadaten-Promotion, automatische CI-Einrichtung, Capsule-Löschung, Ablaufregeln und automatische Historienbereinigung. |
| Sicherheitsdienste | CVE-/Malware-Scans, Lizenzklassifizierung, SBOM-Erzeugung, externe Attestierungen, HSM, Schlüsselrotation und Zertifizierungen. |
| Enterprise-Architektur | HA, Mehrknotenkonsistenz, Mandantenfähigkeit, SSO/MFA/LDAP, S3/MinIO und Replikation. |
| Kommerzieller Betrieb | Abrechnung, automatischer Berechtigungsverkauf, SLA-Personal, Kunden-Onboarding und freigegebene Marke. |
| Verifikation | Vollständiger Boot-Build/-Betrieb, Java 25, Sicherheitsaudit und Lastzertifizierung in dieser Umgebung. |

Unveränderlich sind das erfasste Manifest und seine Blob-Referenzen im Anwendungsmodell. Es handelt sich nicht um WORM-Speicherung gegen Betriebssystemadministratoren. Zwei Benutzernamen beweisen keine unterschiedlichen Personen. Nachweise sind historisch, Gates Momentaufnahmen. Normale Paket-URLs benötigen nicht automatisch eine Release-Freigabe.

Alle Capsule-Zustände behalten Dateien, auch Ablehnung und Widerruf. Das ist konservative Aufbewahrung, keine automatische Speicherersparnis. Tiefe Prüfungen sind synchron, der Metadatenindex liegt auf einem Einzelknoten im Arbeitsspeicher.

Siehe [Release-Semantik](release-assurance.md), [Tests](testing.md), [Sicherheit](security.md) und [Roadmap](roadmap.md).
