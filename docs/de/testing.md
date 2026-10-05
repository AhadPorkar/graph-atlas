# Prüfbericht — 0.5.0-preview

[English](../en/testing.md) · [Dokumentation](index.md).

Stand 05.10.2026. Die Ergebnisse betreffen diesen Quellcode, keine Kundeninstallation. Konsolenausgaben und strukturierte Ergebnisse stehen im [QA-Verzeichnis](../qa/).

## CI-Hotfix-Nachtrag

Der erste gemeldete CI-Lauf scheiterte beim CSP-bezogenen Browser-Polling und in separaten Maven-Jobs. Die Browser-Assertions wurden korrigiert; zur Java-Ursache fehlt das Fehlerprotokoll. Siehe [CI-Diagnose und Patchprüfung](ci-troubleshooting.md). Historische Testergebnisse gelten weiterhin nur für ihren angegebenen Umfang.


## Durchgeführt

| Suite | Ergebnis | Umfang |
| --- | --- | --- |
| `SelfTest` | 47 Prüfungen erfolgreich. | JSON, Hashwerte, Speicherung, Pfade, Sperren und Recovery. |
| `CoreContractTestMain` | 77 Prüfungen erfolgreich. | Bestehende Protokolle und Dienste. |
| `LocalizationTestMain` | 24 Prüfungen erfolgreich. | Sprachauswahl. |
| `ReleaseContractTestMain` | 68 Prüfungen erfolgreich. | Capsules, Rechte, getrennte Konten, Revisionen, Signaturen, Manipulationen, feste Daten, Quarantäne, Aufbewahrung, Parallelität und Neustart. |
| `node --test tests/frontend/*.test.mjs` | 31 Tests erfolgreich. | Drei Sprachdateien, Platzhalter, Clientbeispiele und Referenzparser. |
| `python tests/release_gate_test.py` | 14 Tests erfolgreich. | Client gegen synthetischen HTTP-Server; Weiterleitungen und unklare Antworten führen nicht zur Freigabe. |
| `python tests/evidence_cli_test.py` | 4 Tests erfolgreich. | Ausgeliefertes Shell-/Java-Werkzeug mit temporären echten Ed25519-Schlüsseln. |
| `python tests/restore_test.py` | 12 Tests erfolgreich. | Archivsicherheit, Schutz vorhandener Ziele und Erhalt privater Schlüsseldateien. |
| `python tests/ui_test.py --bridge` | 116 Prüfungen erfolgreich. | Echte UI-Dateien, drei Sprachen, Phasen, Formulare, Quarantäne und responsive Darstellung mit synthetischen Antworten. |
| `java scripts/ParseJava.java .` | 73 Java-Dateien analysiert. | Nur Syntax, keine Abhängigkeitsauflösung. |

Java wurde unter OpenJDK 21.0.11, das Frontend unter Node 22.16.0 geprüft. Die Kerntests kompilieren und verwenden echte Speicher-, Signatur-, Digest- und Berechtigungslogik. UI- und CI-Client-Fixtures ersetzen keine Laufzeitintegration.

Die direkte Chromium-Navigation wurde versucht und mit `ERR_BLOCKED_BY_ADMINISTRATOR` blockiert. Die Brückentests prüfen weder native Cookie-Regeln noch natives Modulladen, CSP oder die Spring-Sicherheitskette. Screenshots sind als synthetische Vorschauen gekennzeichnet.

## Nicht ausgeführt oder nicht nachgewiesen

Maven fehlt; Maven Central war aus der Bereitstellungsumgebung nicht auflösbar. Der vollständige Spring-Boot-4.1.1-Reaktor wurde hier weder gebaut noch gestartet. Java 25, Docker, systemd, Nginx, Windows, produktive Last, Penetrationstest und sämtliche echten Paketclients auf der neuen Laufzeit sind unbestätigt.

`ReleaseMvcTest`, vorhandene MVC-Tests und `TomcatWireTest` liegen bei, wurden aber **nicht als erfolgreiche Tests gezählt**. GitHub-Workflows sind Konfiguration, kein Nachweis ausgeführter CI. Syntaxanalyse ersetzt keine Kompilierung gegen Spring-MVC- und Security-APIs.

## Wiederholen

```bash
bash scripts/test-core.sh
npm test
python tests/release_gate_test.py
python tests/evidence_cli_test.py
python tests/restore_test.py
python scripts/check_repository.py

# Vollständige Laufzeitprüfung: JDK und Maven-Abhängigkeiten erforderlich.
bash scripts/build.sh
python tests/integration.py

# Browser-Fixture, kein Spring-Server.
python -m pip install -r tests/requirements.txt
python -m playwright install chromium
python tests/ui_test.py
```

Brückenmodus nur bei gesperrter nativer Navigation verwenden und die Einschränkung dokumentieren. Die Nachweis-CLI-Tests benötigen Bash und JDK 21+. PowerShell-Skripte sind enthalten, wurden hier jedoch nicht ausgeführt.

## Abnahme

Der [Vermarktungsplan](commercialization.md) enthält Produktionsprüftore. Zusätzlich auf dem echten Server testen: getrennte Benutzer, echte Paketveröffentlichung, Erfassung, abgewiesene Selbstfreigabe, externe Freigabe, Veröffentlichung, Signaturprüfung mit separat bestätigtem Schlüssel, Austausch der Quelldatei, unveränderter fester Download, Quarantäne für GET/HEAD/Range/Cache, Auflösung, Widerruf sowie Backup und Restore. Die Signieridentität muss erhalten bleiben.

Eine frische Extraktion vor Änderungen mit `python scripts/check_repository.py --manifest` prüfen. Das Quellarchiv enthält Dateihashes. Diese ersetzen weder einen Build noch die Authentifizierung des Herausgebers.
