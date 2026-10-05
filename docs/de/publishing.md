[English](../en/publishing.md) · [Deutsch](../de/publishing.md) · [Documentation](index.md)

# Projekt auf GitHub veröffentlichen

Das Archiv ist ein Quellrepository, kein bereits angelegtes GitHub-Repository. Es enthält weder
`.git`-Historie noch Remote-URL, echte Zugangsdaten oder erfundene Mitwirkendenprofile.
Der **Inhalt des Ordners `graph-repository`** gehört direkt in die Repository-Wurzel, damit README
und Workflows erkannt werden.

## Vor dem ersten Push prüfen

Verifikationshinweis, MIT-Lizenz und Drittanbieterhinweise lesen. Für hinzugefügten Code und
Grafiken müssen Veröffentlichungsrechte vorliegen. Private Konfiguration, Paketdaten und Bilder
echter Systeme entfernen. `.gitignore` ist eine Schutzmaßnahme, kein Geheimnisscanner.

```bash
python scripts/check_repository.py
git init -b main
git add .
git status --short
git diff --cached --stat
git diff --cached --check
git commit -m "Add multilingual self-hosted package repository"
```

Im eigenen GitHub-Konto ein **leeres** Repository namens `graph-repository` anlegen. Keine weitere
README, Lizenz oder `.gitignore` erzeugen lassen. Danach den tatsächlichen Kontonamen verwenden:

```bash
git remote add origin https://github.com/YOUR_USERNAME/graph-atlas.git
git push -u origin main
```

`YOUR_USERNAME` ist ein bewusster Einrichtungsplatzhalter, keine notwendige Änderung im Quellcode.
Alternativ kann nach eigener Anmeldung die GitHub CLI verwendet werden. Weder Archiv noch Skripte
laden selbstständig etwas hoch oder melden sich in Ihrem Namen an.

## Darstellung des Repositories

Vorschlag für die Beschreibung:

> Self-hosted Java/Spring Boot package repository with English, German and Persian UI, content-addressed storage and repository-level permissions.

Passende Themen: `java`, `spring-boot`, `package-registry`, `maven`, `npm`, `nuget`, `pypi`,
`docker-registry`, `i18n`, `rtl`, `self-hosted`.

Die englische README ist die Einstiegsseite und verlinkt sichtbar auf Deutsch. Fixture-Screenshots
und Architekturdiagramme sind enthalten. Testzahlen dürfen nicht als echte Nutzung oder Durchsatz
dargestellt werden. CI-Badges erst hinzufügen, wenn das Remote-Repository existiert und der
jeweilige Job tatsächlich ausgeführt wird.

## Pflege des Repositories

GitHub Actions aktivieren und die ersten Ergebnisse prüfen. Branch-Schutz und verpflichtende
Prüfungen erst nach funktionierender CI einrichten. Vor externer Nutzung vertrauliche
Sicherheitsmeldungen und einen echten Maintainer-Kontakt konfigurieren; eine erfundene Adresse
ist bewusst nicht hinterlegt. Issue- und Pull-Request-Vorlagen gibt es auf Englisch und Deutsch.

Die CI-Dateien sind kein Nachweis eines erfolgreichen Remote-Laufs. Build- und Testfehler vor
einem stabilen Release beheben. Solange Prüfungen fehlen, eine deutlich gekennzeichnete Vorabversion
verwenden. Laufzeithinweise nicht allein für eine vermeintlich bessere Portfoliowirkung entfernen.

## Sauberes Quellarchiv erneut erstellen

```bash
python scripts/package_source.py --output /safe/location/graph-atlas-source.zip
```

Das Werkzeug schließt Build-Ergebnisse, lokale Daten, Geheimnisse und Git-Interna aus und erzeugt
eine SHA-256-Datei sowie ein internes Quellmanifest. Vor Weitergabe den Inhalt kontrollieren.
Das Packen kompiliert die Anwendung nicht und lädt keine Abhängigkeiten.
