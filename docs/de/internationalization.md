[English](../en/internationalization.md) · [Deutsch](../de/internationalization.md) · [Documentation](index.md)

# Internationalisierung

## Auswahl und Speicherung

Das ausgelieferte Dokument beginnt mit `<html lang="en" dir="ltr">`. Die Anwendung prüft zuerst
einen unterstützten Query-Wert `?lang=en|de|fa`, dann `graph.repository.language` im localStorage
und verwendet andernfalls Englisch. Browser- und Java-Systemsprache bestimmen den Standard nicht.

Anmeldung, Kopfzeile und Dialoge bieten dieselbe Sprachauswahl. Sie ändert `html.lang`,
`html.dir`, Seitentitel, Navigation, Beschriftungen, Validierung, Statusanzeigen, Datums- und
Zahlenformate sowie Kommentare in Client-Beispielen. Alle Auswahlelemente bleiben synchron.

Der Zugriff auf localStorage ist abgesichert. Privates Browsen oder gesperrter Speicher legt die
Oberfläche nicht lahm. Schlägt das Laden eines optionalen Katalogs fehl, bleibt eine funktionsfähige
Sprache aktiv. Einzelne fehlende Übersetzungen fallen auf Englisch zurück.
Passwörter, CSRF-Werte und Zugriffstokens werden nicht im localStorage gespeichert.

## Dateien

```text
repository-server/src/main/resources/static/locales/en.json
repository-server/src/main/resources/static/locales/de.json
repository-server/src/main/resources/static/locales/fa.json
repository-server/src/main/resources/static/i18n.js
repository-server/src/main/resources/i18n/messages.properties
repository-server/src/main/resources/i18n/messages_de.properties
repository-server/src/main/resources/i18n/messages_fa.properties
repository-core/src/main/java/ir/graph/repo/core/i18n/SupportedLanguages.java
```

Die UI-Kataloge enthalten derzeit **381 identische Schlüssel**, einschließlich Pluralformen und
47 Fehlercodes. Die Backend-Bundles enthalten diese Codes und Fallbacks für HTTP-Statuswerte.
Englisch liegt im unqualifizierten Bundle `messages.properties`.

## API-Vertrag

Die Oberfläche sendet `Accept-Language: en`, `de` oder `fa` bei Verwaltungsanfragen.
Der Server akzeptiert regionale Kennungen wie `de-DE` und `fa-IR` sowie gewichtete Listen.
Fehlende, nicht unterstützte, ungültige oder zu lange Header führen zum englischen Fallback.

Verwaltungsfehler enthalten `Content-Language` und `Vary: Accept-Language`.
Der Spring-Fallback auf die Sprache des Hosts ist deaktiviert. Paketclient-Routen behalten
englische Diagnosemeldungen und unveränderte maschinelle Felder.

```json
{
  "error": "UNAUTHORIZED",
  "message": "Melden Sie sich an oder verwenden Sie ein gültiges Zugriffstoken.",
  "detail": "Authentication is required",
  "requestId": "example-only"
}
```

`detail` ist eine technische Diagnose, kein zweiter übersetzter UI-Absatz. API-Feldnamen,
HTTP-Statuscodes, Paketmetadaten, Berechtigungsschlüssel und Benutzereingaben bleiben unverändert.

## Formatierung und RTL

Zahlen und Datumsanzeigen verwenden `Intl` mit `en-US`, `de-DE` oder `fa-IR`.
Die persische Datumsdarstellung folgt dem persischen Sprach-/Kalenderverhalten des Browsers.
Gespeicherte Zeitstempel und Anfragedaten bleiben ISO-Zeitstempel. `datetime-local`-Felder verwenden
den vom Browser vorgegebenen Kalender und werden beim Absenden in ISO-Werte umgewandelt.

CSS verwendet logische Eigenschaften wie `margin-inline-start` und `inset-inline-start`.
Navigation, Dialoge und Tabellen spiegeln sich mit der Schreibrichtung.
Technische Inhalte verwenden bei Bedarf `dir="ltr"` oder `<bdi>`.
Mobile Tabellen scrollen innerhalb ihres Containers.

## Übersetzungen pflegen

Ändern Sie jeden Schlüssel in allen drei Katalogen und behalten Sie `{placeholder}`-Namen bei.
Neue Fehlercodes benötigen Übersetzungen in allen drei Backend-Bundles.
Benutzerinhalte bleiben von Übersetzungen getrennt; dynamische Texte werden vor dem Rendern maskiert.

Führen Sie `npm test`, `python scripts/check_repository.py`, die Java-Sprachtests und die
Browser-Fixture-Suite aus. Prüfen Sie Dialoge mit ungespeicherten Werten, ausgewählten Dateien
und einmalig angezeigten Tokens. Ein Sprachwechsel darf keine Anfrage erneut absenden.

Eine vierte Sprache erfordert außerdem Anpassungen an `LANGUAGES`, HTML-Auswahlfeldern,
`SupportedLanguages`, Spring-Bundles, der Freigabe statischer Ressourcen und den Testmatrizen.
