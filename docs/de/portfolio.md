[English](../en/portfolio.md) · [Deutsch](../de/portfolio.md) · [Documentation](index.md)

# Portfolio-Rundgang

Das Repository eignet sich als nachvollziehbare technische Fallstudie, nicht als Beleg für
unbelegte Produktionserfahrung oder fremde Autorenschaft. Profil und Beschreibung sollten die
tatsächlichen eigenen Beiträge nennen; gegebenenfalls gehört die Prüfung und Validierung
generierten Codes dazu.

## Vorschlag für die Projektbeschreibung

> Unabhängiger Java-/Spring-Boot-Prototyp für Paket-Repositories mit frameworkunabhängigem
> Speicherkern, sechs Paketfamilien, Repository-basierten Zugriffsrechten und einer englischen,
> deutschen sowie persischen Oberfläche. Englisch ist Standard; Sprachwechsel erhalten
> Formulareingaben und spiegeln das Layout für Persisch. Zweisprachige Dokumentation und
> mehrstufige Testdefinitionen sind enthalten.

**Prototyp** und [Prüfgrenzen](testing.md) sind wesentlich. Zertifizierte Client-Kompatibilität,
Produktivinstallationen, Millionen von Anfragen oder ein bestandener Sicherheitsaudit dürfen nicht
ohne entsprechende Nachweise behauptet werden.

## Demonstration in fünf Teilen

1. Kern-/Server-Trennung und Hash-adressierten Speicher anhand der [Architektur](architecture.md) erklären.
2. Englische Anmeldung zeigen, auf Deutsch und dann Persisch umschalten. Gespiegeltes Layout und
   unveränderte Paketkennungen erläutern; auch den Sprachrückfall erklären.
3. Werte in einem Repository-Dialog eingeben und ohne Verlust die Sprache wechseln. Zeigen, dass
   eine ausgewählte Datei und ein bereits ausgestelltes Token nicht neu erzeugt werden.
4. Hosted, Proxy und Gruppen erklären und den bewussten Ausschluss von OCI-Proxy/-Gruppen benennen.
   Minimale Benutzerrechte statt eines pauschal allmächtigen Demokontos zeigen.
5. Vorhandene Tests ausführen und ihre Grenzen erläutern. Einen Architekturkompromiss und die
   Roadmap diskutieren, statt unbelegte Leistungszahlen zu präsentieren.

Die Screenshots enthalten synthetische Daten. Sie zeigen Oberflächenarbeit, beweisen aber keinen
laufenden Spring-Boot-Server. Ein echtes Laufzeitvideo erst nach erfolgreichem Build und Prüfung aufnehmen.

## Fachliche Gesprächsthemen

Geeignet sind atomare Metadatenänderungen, Streaming versus Speichergrenzen, SSRF-Grenzen bei
Proxys, CSRF im Vergleich zu Token-Anmeldung, Token-Lebenszyklus, Richtungsisolation technischer
Zeichenfolgen und die Trennung von Übersetzungskatalogen und Paketprotokollen.

[Entscheidungsprotokolle](decisions.md) dokumentieren Kompromisse. Die [Roadmap](roadmap.md) priorisiert
Laufzeitverifikation vor neuen Funktionen. Messwerte und tatsächliche Beiträge können später ergänzt
werden. Maintainer, Kunden, Sterne, Benchmarks oder CI-Ergebnisse dürfen nicht erfunden werden.
