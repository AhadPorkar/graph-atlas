[English](../en/decisions.md) · [Deutsch](../de/decisions.md) · [Documentation](index.md)

# Architekturentscheidungen

## ADR-001: Framework-unabhängiger Core

**Entscheidung:** Domänendienste und Protokolllogik bleiben in einem normalen Java-Modul.
Spring MVC adaptiert Servlet-Anfragen an den Core-Protokollaustausch.

**Grund:** Speicher- und Paketverträge lassen sich ohne Container testen.
**Abwägung:** Eine Adapterschicht muss Fehler, Streams und HTTP-Semantik korrekt abbilden.
Core-Tests ersetzen keine Prüfungen der Servlet-Laufzeit.

## ADR-002: Inhaltsadressierter Speicher auf einem Knoten

**Entscheidung:** SHA-256-Blobs sowie ein lokales transaktionales Metadatenprotokoll mit Snapshots.
**Grund:** Überschaubare Persistenz und digestbasierte Deduplizierung.
**Abwägung:** Ein Index im RAM und ein einzelner Schreibprozess bieten keine Hochverfügbarkeit.
Ein Datenbank-/Objektspeicher-Backend benötigt ein eigenes Konsistenz- und Wiederherstellungskonzept.

## ADR-003: Englisch als expliziter Standard

**Entscheidung:** Nur eine gespeicherte oder ausdrücklich angeforderte Sprache überschreibt Englisch.
Die Sprache des Browsers oder Betriebssystems tut dies nicht.
**Grund:** Deterministisches Verhalten beim ersten Start und bei Demonstrationen.
**Abwägung:** Deutsch und Persisch müssen einmal aktiv ausgewählt werden.

## ADR-004: Übersetzung von Protokolldaten trennen

**Entscheidung:** Beschriftungen und Verwaltungsfehler werden übersetzt, Koordinaten und
maschinelle Schlüssel nicht.
**Grund:** Paketclients benötigen unabhängig von der Oberflächensprache stabile Verträge.
**Abwägung:** Technische Diagnosedetails bleiben Englisch.

## ADR-005: Entwürfe beim Sprachwechsel erhalten

**Entscheidung:** Dialoge werden neu dargestellt; Werte, ausgewählte Dateien und einmalig
angezeigte Tokens bleiben erhalten. Ein Sprachwechsel wiederholt keinen Schreibzugriff.
**Grund:** Übersetzungen dürfen weder Arbeit verwerfen noch zusätzliche Tokens erzeugen.
**Abwägung:** Abhängige Felder benötigen Regressionstests; reiner Textaustausch reicht nicht.

## ADR-006: Nur tatsächlich beobachtete Testergebnisse behaupten

**Entscheidung:** Ausführbare Testdefinitionen liefern und ausgeführte Prüfungen klar ausweisen.
**Grund:** Quellcode und Syntaxprüfung belegen noch keine funktionierende Spring-Boot-Auslieferung.
**Abwägung:** Das öffentliche Repository bleibt als Entwicklungsversion gekennzeichnet,
bis CI und reale Client-Abnahmen erfolgreich sind.
