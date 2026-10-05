# Produktidentität

[English](../en/brand.md) · [Produktkonzept](product.md).

## Arbeitsidentität

Name: **Graph Atlas**. Beschreibung: **Selbst gehosteter Arbeitsbereich für Artefakte und Releases**. Produktsatz: **Artefakte unter Kontrolle. Entscheidungen dokumentiert.**

Der Name ist ein ungeprüfter Arbeitsname. Das geometrische A liegt als SVG unter `repository-server/src/main/resources/static/logo.svg`. Es werden keine Wettbewerberlogos, kopierten Screenshots, fremden Markenpakete oder proprietären Schriftdateien verteilt. Bestehende Lizenzhinweise bleiben erhalten.

Die Informationsarchitektur folgt der Arbeit an Releases: Leitstand, Release-Phasen, Artefakt-Explorer, Anbindung und Speicheranalyse. Registry-Konfiguration, Quarantäne und Wartung bilden den Betrieb; Benutzer und Audit gehören zur Zugriffskontrolle. Geändert wird der Arbeitsablauf, nicht nur die Farbe einer Verwaltungsoberfläche.

## Gestaltung

Warmer neutraler Hintergrund, salbeifarbene Navigation, dunkelgrüner Hauptbereich und limettengrüne Aktionstasten bilden die Gestaltung. Das Hauptgrün ist `#183f38`, der Akzent `#d5ef83`. Verbindlich sind die Werte im CSS. Nummerierte Navigation, zurückhaltende Statusmarken, explizite Entscheidungen und Digest-Nachweise sind wiederkehrende Elemente.

Systemschriften vermeiden externe Downloads. SVG und CSS liegen im Quellcode. Englisch und Deutsch sind links-nach-rechts, Persisch rechts-nach-links mit logischen Abständen. Paketnamen, Befehle, Hashwerte und technische Kennungen bleiben unverändert.

## Sprache

Konkrete Begriffe verwenden: „erfasst“, „geprüft“, „signiert“, „gesperrt“, „geprüft am“. Historische und aktuelle Aussagen unterscheiden. Ohne Beleg nicht „unhackbar“, „unbegrenzt“, „zertifiziert“, „KI-gestützt“ oder „marktführend“ schreiben. Ein lokaler Nachweis ist keine unabhängige Drittzertifizierung.

Die Screenshots aus `tests/ui_test.py` sind als synthetischer Arbeitsbereich markiert. Diese Kennzeichnung in Vertriebsunterlagen erhalten, sofern die Abbildung nicht tatsächlich eine andere eindeutig beschriebene Umgebung zeigt.

## Sicher umbenennen

Nach einer Markenprüfung können sichtbare Namen in README, Anleitungen, Sprachdateien, HTML-Metadaten und SVG geändert werden. Auch sichtbare Java-Statusmeldungen und Projektmetadaten prüfen. Anschließend Sprach- und UI-Tests erneut ausführen.

API, Datenfelder, `GR_`-Variablen, Java-Namensraum, Signaturschema und JAR-Dateiname sind Kompatibilitätsverträge. Sie dürfen nicht durch blindes globales Ersetzen geändert werden. Bestehende Nachweise müssen prüfbar bleiben. Eine Umbenennung erlaubt nicht, Urheberhinweise zu entfernen.
