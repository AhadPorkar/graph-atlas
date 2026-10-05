# Änderungsprotokoll

[English](CHANGELOG.md)

## 0.5.0-preview — 2026-10-05

Eigenständige Graph-Atlas-Identität und releaseorientierter Arbeitsbereich. Neu: dateibasierte Capsules, getrennte Freigabekonten, Revisionskontrolle, Ed25519-Nachweise, feste Downloads, Digest-Quarantäne, Speicheranalyse, Offline-Prüfung und CI-Gate. Produkt- und Vermarktungsanleitungen in Englisch und Deutsch. Drei Sprachdateien mit je 381 Schlüsseln. Neue Tabellen erfordern Rollback über Backup. Vollständige Spring-Laufzeit bleibt in der Bereitstellungsumgebung unbestätigt.


## 0.4.0-SNAPSHOT — 2026-10-05

### Hinzugefügt
- Englische, deutsche und persische Kataloge; Englisch als ausdrücklicher Standard.
- Sprachwahl bei Anmeldung, im Kopfbereich und in Dialogen mit gespeicherter Präferenz.
- Persisches RTL-Layout, Richtungsisolation technischer Zeichenfolgen und regionale Formatierung.
- Erhalt von Formularen, ausgewählten Dateien und einmalig angezeigten Tokens beim Sprachwechsel.
- Übersetzte administrative API-Fehler mit stabilen Codes und Sprachheadern.
- Englische/deutsche Fachtexte, Projektrichtlinien, GitHub-Vorlagen und Quellarchivierung.
- Regressionstests für Sprachauswahl, Frontend und Browser-Fixtures.

### Geändert
- Projektauftritt und Artefaktname lauten jetzt `graph-repository`.
- Die Oberfläche verwendet lesbare ES-Module und semantisches Markup.
- Neue Benutzer erhalten keine impliziten Repository-Rechte.
- Client-Beispiele trennen lesende Proxy-/Gruppen-Endpunkte von Hosted-Veröffentlichung.

### Prüfgrenze
Kern- und Frontend-Prüfungen liefen mit der dokumentierten lokalen Werkzeugkette.
Vollständiges Spring Boot, Java 25, Docker und echte Clients gegen diesen Runtime bleiben
hier ungeprüft. Dieses UI-/Dokumentationsupdate erweitert weder die Protokollfamilien
noch behauptet es Produktionsreife.

## 0.3.0-spring — Vorgänger

Führte Spring-MVC-/Security-Transport und getrennte Kern-/Server-Module ein.
Der historische Eintrag kennzeichnet die Codebasis, kein zertifiziertes öffentliches Release.

## 0.2.0 — Vorgänger

Unabhängiger Einzelknoten-Repository-Kern mit persischer Oberfläche. Der Migrationsleitfaden
erläutert Datenkompatibilität; eine automatische Nexus-Migration ist nicht enthalten.
