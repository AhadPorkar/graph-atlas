# Produktkonzept

[English](../en/product.md) · [Dokumentation](index.md).

## Positionierung als Hypothese

**Graph Atlas ist ein selbst gehosteter Übergabearbeitsbereich für Entwicklungsteams, die nachvollziehen möchten, welche konkreten Artefakte geprüft und freigegeben wurden.** Bestehende Paketwerkzeuge werden mit einer kleinen, sichtbaren Freigabeschicht verbunden.

Das ist eine Positionierungshypothese, kein Beleg für Product-Market-Fit. Als erste Zielgruppe kommen Teams infrage, die einen Einzelserver akzeptieren, Java-, JavaScript-, .NET- oder Python-Builds betreiben und lokale Kontrolle mit englischer, deutscher oder persischer Administration bevorzugen. Für Käufer mit Pflichtanforderungen an HA, SSO, Zertifizierungen oder integrierte Schwachstelleninformationen ist die Vorschau nicht geeignet.

## Ein Ablauf statt einer Einstellungsübersicht

Der Leitstand beginnt mit Release-Entscheidungen statt Serverkonfiguration. Die Phasen zeigen erfasste, eingereichte, freigegebene und veröffentlichte Capsules. Entwickler wählen explizite Dateien im Artefakt-Explorer. Ein anderes berechtigtes Konto prüft die feste Auswahl. Der Ersteller veröffentlicht und exportiert einen signierten Nachweis. Die Pipeline prüft den aktuellen Zustand und lädt festgelegte Bytes.

Die Speicheranalyse zeigt Deduplizierung anhand von Referenzen und historisch gebundene Dateien. Die Quarantäne ermöglicht eine begründete Reaktion auf einen bekannten Digest über mehrere Pfade hinweg. Keine dieser Ansichten enthält eine erfundene Sicherheitsnote. Konfigurationshinweise beschreiben vorhandene Einstellungen, keine vermutete Rechtskonformität.

Die vorgeschlagene Differenzierung liegt in **Kombination und Bedienung**: lokaler Betrieb, kompakter Freigabeprozess, extern prüfbare Nachweise, nachvollziehbare Speicherzahlen und drei Sprachen. Signaturen, Promotion und Quarantäne gibt es bereits bei etablierten Anbietern; sie werden nicht als Erfindungen oder exklusive Merkmale bezeichnet.

## Demonstration in acht Minuten

Zwei Testkonten und nicht vertrauliche Artefakte verwenden. Zwei Dateien hochladen, eine Capsule erfassen und einreichen. Die verweigerte Selbstfreigabe zeigen. Mit dem Prüferkonto freigeben und mit dem Erstellerkonto veröffentlichen. Einen veränderbaren Quellpfad ersetzen und nachweisen, dass der feste Download weiter die erfassten Bytes liefert. Digest sperren, abgewiesenes Gate beziehungsweise Download zeigen, Sperre lösen und einen Nachweis gegen einen separat bestätigten Schlüssel prüfen.

Die Demo muss normale Paket-URLs und feste Release-URLs unterscheiden. Quellenabweichung und historischer Charakter der Nachweise gehören zur Erklärung. Die Bilder unter `docs/assets` sind synthetische Vorschauen, keine Kundeninstallationen oder Benchmark-Ergebnisse.

## Nicht behaupten

Die Vorschau ist keine universelle Paketplattform, Zero-Trust-Lösung, Malware-Erkennung, zertifizierte Lieferkettenplattform oder vollständig isolierte SaaS. Aus fehlenden kommerziellen Quoten folgt keine unbegrenzte Kapazität. Eine Signatur beweist weder Codequalität noch Ungefährlichkeit. Eine Produktionsfreigabe ist nicht nachgewiesen.

Ein möglicher Geschäftsvorteil liegt in einer fokussierten Dienstleistung und guten Bedienbarkeit. Er muss mit Kunden und einer betriebsreifen Implementierung validiert werden; ein neues Logo reicht dafür nicht.

## Pilotkriterien

Einrichtung einer Toolchain, echte Veröffentlichung und Installation, Prüfdauer, Erkennung veralteter Entscheidungen, beschädigter Dateien und gesperrter Downloads sowie Wiederherstellung und Bedienverständnis messen. Latenzen und Ressourcen werden am gemeinsam vereinbarten Kundenlastprofil ermittelt. Für diese Lieferung gibt es keine gemessene Durchsatz- oder Verfügbarkeitszusage.

Weitere Details: [Vermarktung](commercialization.md), [Release-Nachweise](release-assurance.md), [Funktionsabdeckung](coverage.md).
