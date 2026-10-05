# Vermarktungsplan

[English](../en/commercialization.md) · [Produktkonzept](product.md).

## Was lässt sich anbieten, und unter welchen Voraussetzungen?

Dieses Repository ist eine Entwicklungsvorschau, kein fertiger Enterprise-Dienst. Es enthält Betriebshilfen und funktionale Kerntests. Die Bereitstellungsumgebung hat Spring Boot jedoch nicht kompiliert oder gestartet. Vor einem Verkauf für produktiven Betrieb sind die unten aufgeführten Abnahmen und eine tatsächlich leistbare Betreuung erforderlich.

Als erstes Angebot wird eine **bezahlte, klar begrenzte Evaluierung und Integration** vorgeschlagen: Kundentoolchains anbinden, Freigaben mit getrennten Konten einrichten, das CI-Gate ausdrücklich integrieren, Restore erproben und zweisprachige Betriebsunterlagen übergeben. Kunden müssen erfahren, welche Funktionen existieren und welche Laufzeittests noch fehlen.

Spätere Angebote könnten unterstützter Eigenbetrieb oder eine dedizierte verwaltete Instanz sein. Das sind Vorschläge, keine vorhandenen Produktpakete. Die Architektur ist nicht mandantenfähig. Gegenseitig nicht vertrauenswürdige Kunden dürfen nicht so in einer gemeinsamen Instanz betrieben werden, als existiere eine Mandantentrennung.

## Angebotsstruktur ohne erfundene Preise

| Angebot | Leistung | Sichtbare Ausschlüsse |
| --- | --- | --- |
| Evaluierung / Integration | Isolierte Testinstallation, vereinbarte Protokolle, CI-Beispiel und gemessene Abnahmeergebnisse. | Keine Produktionsverfügbarkeit oder Sicherheitszertifizierung. |
| Unterstützter Eigenbetrieb | Kundeninstanz, Installationshilfe, Updates und Restore-Unterstützung nach Vertrag. | HA, Scanner und SSO fehlen. SLAs benötigen echte Personal- und Monitoringkapazität. |
| Dedizierter Betrieb | Getrennte Instanz je Kunde, Backup- und Störungsprozesse. | Keine gemeinsame Mandantengrenze; Region, Schlüsselverwahrung und Datenverarbeitung müssen vereinbart werden. |

Es werden keine Marktpreise, Margen oder Kostenvorteile gegenüber Wettbewerbern behauptet. Konditionen sollten aus realem Supportaufwand, Infrastrukturkosten, Kundenanforderungen und Pilotdaten entstehen. Der Kern erhält keine Lizenzzähler.

## Abnahme vor einem Produktionsvertrag

| Prüftor | Benötigter Nachweis |
| --- | --- |
| Reproduzierbarer Laufzeit-Build | `mvn clean verify` mit dem vorgesehenen JDK; Abhängigkeiten und Ergebnisse dokumentieren. |
| Echte Paketclients | Veröffentlichen und konsumieren mit Maven/Gradle, npm, NuGet, pip und OCI im Kundenablauf. Fixtures reichen nicht. |
| Freigaberegeln | Getrennte Konten, veraltete Revisionen, gesperrte Digests, geänderte Quellpfade und Widerruf testen. |
| Schlüssel und Restore | Externe Backup-Verschlüsselung, eingeschränkte Schlüsselrechte, wiederhergestellte Signieridentität und geprüfte Bytes. |
| Sicherheitsprüfung | Anmeldung, CSRF, Rechte, Pfade, Protokolle, Uploads und Abhängigkeiten qualifiziert prüfen lassen. |
| Kapazität | Vereinbarte Dateianzahlen, Größen, Parallelität, tiefe Prüfzeiten, Speicher- und Plattenlast auf Zielhardware messen. |
| Browser und Betrieb | Echtes CSP-/Cookie-Verhalten, drei Sprachen, TLS, Reverse Proxy und tatsächlicher Container-/systemd-Betrieb. |
| Geschäftliche Zusagen | Geprüfter Name, Lizenzhinweise, Störungskontakte, Updatepolitik und realistische Betreuung. |

Diese Tabelle ist eine Abnahmeliste, keine Behauptung bereits bestandener Prüfungen.

## Positionierung validieren

Die Aussage „lokaler Übergabearbeitsbereich mit Prüfung konkreter Bytes und unabhängig verifizierbaren Aufzeichnungen“ mit Teams testen, die heute Freigabeschritte über mehrere Werkzeuge verteilen. Den vorhandenen Ablauf zeigen lassen, statt nur eine Oberfläche bewerten zu lassen. Im Pilot Einrichtungszeit, Entscheidungsklarheit und Wiederherstellung messen.

Promotion, Signaturen und Quarantäne gibt es bereits bei etablierten Anbietern. Die vorgeschlagene Differenzierung ist ein begrenzter lokaler Ablauf mit mehrsprachiger Betreuung, keine neue kryptografische Erfindung. Vergleiche wie „besser als jede Registry“ benötigen belastbare Messungen und aktuelle Wettbewerbsdaten.

Fehlende Lizenzquoten bedeuten keine unbegrenzte Kapazität. Offline-Signaturprüfung ist keine aktuelle Widerrufskontrolle. Eine manuelle Sperre ist kein automatischer Schwachstellenscanner.

## Lizenz, Herkunft und Name

Die vorhandene MIT-Lizenz bleibt erhalten. Sie erlaubt kommerzielle Nutzung und Verkauf unter Beibehaltung von Urheber- und Lizenzhinweis. Empfänger dürfen den MIT-Code ebenfalls weitergeben; eine öffentliche Veröffentlichung schafft keine Exklusivität. Für Binärdistributionen müssen die tatsächlichen Abhängigkeitslizenzen geprüft werden. Ein Supportvertrag hebt bestehende Quellcoderechte nicht auf.

Es wurde keine fremde Lizenzkontrolle entfernt. Die Anwendung benötigt keine proprietäre Repository-Engine. Vorhandene Urheberangaben in `LICENSE` dürfen nicht zur Verschleierung der Herkunft entfernt werden.

„Graph Atlas“ ist ein Arbeitsname. Markenregistrierung, Domainverfügbarkeit und eine rechtliche Freigabe werden nicht behauptet. Vor kommerzieller Veröffentlichung sind eine passende Namens-/Markenprüfung für die Zielmärkte und die Rechteklärung zusätzlicher Beiträge und Ressourcen erforderlich.

Am 05.10.2026 geprüfte Primärquellen: [MIT-Lizenz](https://opensource.org/license/mit) und [Cloudsmith Artifact Management](https://docs.cloudsmith.com/artifact-management). Letztere dokumentiert vorhandene Promotion- und Quarantänefunktionen, keine Gleichwertigkeit der Atlas-Abdeckung.
