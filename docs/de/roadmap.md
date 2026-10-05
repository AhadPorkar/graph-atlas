[English](../en/roadmap.md) · [Deutsch](../de/roadmap.md) · [Documentation](index.md)

# Roadmap

Die Reihenfolge richtet sich nach Risikoreduktion, nicht nach zugesagten Veröffentlichungsterminen.

## Zuerst: ausgelieferte Laufzeit verifizieren

Den vollständigen Maven-Reaktor mit JDK 25 in GitHub Actions ausführen.
Spring-API- oder Abhängigkeitsprobleme vor einer Binärveröffentlichung beheben.
Native Browser-/CSP-/Cookie-Tests, echte npm-/pip-/NuGet-/Maven-/Gradle-/OCI-Clients sowie
eine Wiederherstellungsprobe ausführen.

## Danach: Betrieb absichern

Repräsentative Last- und Wiederanlaufmessungen ergänzen.
Speicherwachstum des Metadatenindex, Upstream-Ausfälle, strukturierte Fehlercodes und
geordnetes Herunterfahren prüfen. Abhängigkeits- und Image-Scans mit einer geprüften
Behebungsstrategie einführen.

## Anschließend: Kompatibilität gezielt erweitern

Bei Bedarf Docker-Proxy/-Gruppen vervollständigen. Weitere Adapter einzeln mit Protokoll-Fixtures,
realen Client-Abnahmen und dokumentierten Grenzen hinzufügen.
Vor Mehrknotenbetrieb ein Datenbank-/Objektspeicher-Backend entwerfen.

## Laufende Qualität

Drei UI-Kataloge und beide Dokumentationssprachen synchron halten.
Tastatur- und Assistenztechnologie-Tests ergänzen, weitere Browser prüfen und deutsche sowie
persische Texte von Muttersprachlern überprüfen lassen.

Offene Punkte sind weder vorhandene Funktionen noch verbindliche Lieferzusagen.

## Nächste Schritte für Release-Regeln

Erfassung, Freigabe mit getrennten Konten, Nachweise und Quarantäne sind im Quellcode enthalten. Als Nächstes echte Laufzeit prüfen sowie sichere Schlüsselrotation, asynchrone Integritätsjobs und explizite Aufbewahrung entwerfen. Scanner-Anbindungen brauchen echte Ergebnisse und ein eigenes Vertrauensmodell; keine wird als fertige Funktion simuliert.
