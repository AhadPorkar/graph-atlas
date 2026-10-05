# Mitwirken

[English](CONTRIBUTING.md)

Vielen Dank für die Prüfung von Graph Atlas. Dies ist eine Entwicklungsvorschau;
Änderungen müssen implementiertes Verhalten und geprüfte Laufzeitkompatibilität klar trennen.

## Entwicklungsablauf

JDK 25, Maven und Node ab Version 22 verwenden. Ein gezieltes Issue beschreibt zunächst das
beobachtete Problem. Sicherheitslücken nach [SECURITY.de.md](SECURITY.de.md) melden, nicht öffentlich.
Einen eigenen Branch erstellen, Änderungen begrenzen und Regressionstests ergänzen.

```bash
bash scripts/test-core.sh
npm test
python tests/restore_test.py
python scripts/check_repository.py
mvn -B -ntp clean verify
```

Bei Änderungen an Oberfläche oder Transport auch native Browser- und echte HTTP-/Client-Tests
ausführen. Nicht ausgeführte Tests ausdrücklich nennen und nicht als bestanden markieren.
Prüfungen nicht für einen grünen Build abschwächen. [Tests](docs/de/testing.md) erklärt die Ebenen.

## Code und Dokumentation

Domänenlogik gehört nach `repository-core`, Spring- und HTTP-Logik nach `repository-server`.
Dateien streamen statt unbegrenzt im Speicher puffern. Rechte serverseitig prüfen und Geheimnisse
aus Protokollen und Diagnosen fernhalten. Lesbare Namen und explizite Fehlerbehandlung verwenden.
Bestehende Lizenzhinweise erhalten.

Oberflächentexte gehören in die drei JSON-Kataloge. Schlüssel und Platzhalter müssen übereinstimmen;
`en`, `de` und `fa` sind zu prüfen. Logische CSS-Eigenschaften verwenden und Paketkennungen nicht
übersetzen. `docs/en` und `docs/de` gemeinsam pflegen; Englisch bleibt die Standard-README.
Keine externen Schriftdateien, Tracker, CDN-Pflicht oder erfundene Produktionskennzahlen hinzufügen.

## Pull-Request-Prüfung

Grund, Umsetzung und Migrationseinfluss beschreiben. Tatsächliche Testbefehle und Ergebnisse,
Screenshots der betroffenen Richtungen und ungeprüftes Verhalten nennen. Daten, Zugangsdaten,
Binärdateien und unbeabsichtigte Änderungen vor dem Push ausschließen. Beiträge auf Englisch und
Deutsch sind willkommen. Änderungen können angefordert werden; eine Antwortfrist oder SLA besteht nicht.
