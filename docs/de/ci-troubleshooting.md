# CI-Hotfix: CSP-kompatible Browser-Assertions und Maven-Diagnose

[English](../en/ci-troubleshooting.md) | [Prüfbericht](testing.md).

## Umfang

Der Patch behandelt den gemeldeten Browserfehler für Commit `c25472be9f32415f199cdedd0664105d09bd6676` im Lauf `37298864318`.
Grundlage ist das zuvor gelieferte Quellcode-ZIP. Der Assistent hat keine Änderungen zu GitHub gepusht.
Im bereitgestellten Ausschnitt fehlt die eigentliche Java-Fehlermeldung. **Eine Behebung der Java-Jobs wird nicht behauptet.**
Produktiver Java-Code, Spring-Boot-Version, CSRF-Regeln und CSP bleiben unverändert.

## Bestätigter Browserfehler

Der Traceback stammt aus `Page.wait_for_function`, nicht aus der Übersetzungslogik.
Playwright 1.57.0 wertet den Polling-Ausdruck mit `globalThis.eval` im Seitenkontext aus.
Die Richtlinie `script-src 'self'` erlaubt dies nicht.
Eine Arrow-Funktion als Argument reicht nicht aus, da dieselbe Auswertung verwendet wird.

Alle sieben Aufrufstellen in `tests/ui_test.py` verwenden jetzt automatisch wiederholte Locator-Assertions:

```python
from playwright.sync_api import expect

expect(page.locator("html")).to_have_attribute("lang", "de")
expect(page.locator("#content h1")).to_have_text(expected_heading)
expect(page.locator("#loginError")).not_to_have_text("")
expect(page.locator(".release-lane")).to_have_count(1)
expect(page.locator("#dialog")).not_to_be_visible()
```

Die produktive CSP wird nicht gelockert. Der Workflow aktiviert weder `bypass_csp` noch `unsafe-eval` und überspringt keine fehlgeschlagenen Tests.
Native Navigation wird in CI nicht durch den Bridge-Modus ersetzt.
Die Suite speichert einen Browser-Trace und bei Fehlern einen Screenshot sowie den Traceback, bevor der Browser geschlossen wird.
Hochgeladen werden frische Testnachweise statt Marketingbilder oder historischer Erfolgsmeldungen.

## Zusätzliche Regressionstests

```bash
python tests/csp_assertions_test.py
```

Acht Tests laufen in Chromium mit `page.set_content` und restriktiver Meta-CSP.
Nur das genaue Fixture-Skript ist durch einen SHA-256-Hash zugelassen; eval bleibt verboten.
Die eval-Prüfung wird durch einen echten Klick der Seite ausgelöst und nicht durch CDP-Auswertung, deren CSP-Verhalten abweichen kann.
Die Tests prüfen asynchrone Sprach-, Überschriften-, Fehlermeldungs-, Lane- und Dialogzustände, einen korrekt fehlschlagenden Sprachvergleich sowie den Schutz vor einer Lockerung der CSP.
Dies ist **kein** Test von Spring Boot, nativer HTTP-Navigation oder HTTP-Cookies.

## Maven-Diagnose

Der vollständige Reactor wird weiterhin mit Java 21 und Java 25 gebaut.
Der Workflow protokolliert die Java-/Maven-Versionen und verwendet `-e` sowie `-DtrimStackTrace=false`.
Standardausgabe und Fehlerausgabe werden in `maven-java-21.log` bzw. `maven-java-25.log` gespeichert.
Die Dateien liegen außerhalb von `target`, damit `mvn clean` sie nicht löscht.
`set -euo pipefail` erhält den Fehlerstatus durch `tee` hindurch.
Bei einem Maven-Fehler werden das Protokollende und die Surefire-Berichte ausgegeben. Das vollständige Protokoll und die Modulberichte stehen als Artefakte zur Verfügung.
Ohne konkrete Fehlermeldung wurde keine Abhängigkeitsversion auf Verdacht geändert.

Das bereits fehlgeschlagene Jobprotokoll lässt sich mit der angemeldeten GitHub CLI abrufen:

```powershell
gh run view 37298864318 --repo AhadPorkar/graph-atlas --job 111726530739 --log-failed |
    Set-Content -Encoding utf8 "$HOME\Downloads\graph-atlas-java21-failed.txt"
```

Für alle fehlgeschlagenen Schritte `--job 111726530739` weglassen.
Protokolle vor dem Teilen auf Zugangsdaten prüfen. Ein Ausschnitt mit `PASS`-Zeilen identifiziert weder einen Compiler- noch einen Abhängigkeits-, Start- oder Testfehler.

## Workflow-Pflege

Als Runner ist `ubuntu-24.04` statt des wechselnden Labels `ubuntu-latest` konfiguriert.
Verifizierte Node-24-basierte Action-Majors: checkout v5, setup-node v5, setup-python v6, setup-java v5 und upload-artifact v6.
Diese Action-Laufzeit ist von den Node-/Java-Versionen der Anwendung unabhängig.
Die Deprecation-Warnungen des ursprünglichen Laufs belegen nicht die Ursache des Maven-Fehlers.
Für strengere Supply-Chain-Kontrolle sollten Action-Commit-SHAs separat geprüft und fixiert werden.

## Durchgeführte Prüfungen

- Java-21-Kern: 216 Prüfungen bestanden; die Kernimplementierung blieb unverändert.
- Frontend: 31 Tests bestanden.
- Neue Chromium-CSP-Regression: 8 Tests bestanden, keine übersprungen.
- Bestehende UI-Suite mit ausdrücklicher DOM/HTTP-Bridge: 116 Prüfungen bestanden.
- Release-Gate: 14 Tests; Restore: 12; Signatur-CLI: 4 bestanden.
- Workflow-YAML/Bash sowie simulierter Maven-Exit-Status und Protokollierung: 7 Prüfungen bestanden. Kein Maven-Build.

Native HTTP-Navigation wurde versucht und durch die Umgebung mit `ERR_BLOCKED_BY_ADMINISTRATOR` blockiert.
Maven und JDK 25 sind hier nicht installiert; Maven Central ist nicht auflösbar.
**Ein erfolgreicher nativer Browserlauf, Maven-Reactor, Java-25-Lauf oder GitHub-Lauf wird nicht behauptet.**
Siehe [maschinellen Patchbericht](../qa/ci-hotfix-validation.json).

## Referenzen

- [Playwright Locator-Assertions](https://playwright.dev/python/docs/api/class-locatorassertions).
- [Playwright-1.57.0-Polling-Implementierung](https://github.com/microsoft/playwright/blob/v1.57.0/packages/playwright-core/src/server/frames.ts).
- [Maven-Surefire-Parameter](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html).
- [GitHub-CLI-Jobprotokolle](https://cli.github.com/manual/gh_run_view).
