[English](../en/security.md) · [Deutsch](../de/security.md) · [Documentation](index.md)

# Sicherheitsmodell und Prüfgrenzen

Schutzmechanismen sind im Quellcode vorhanden. Diese Auslieferung hat jedoch weder einen
unabhängigen Penetrationstest noch einen vollständigen Spring-Boot-Laufzeittest bestanden.
Siehe [Tests](testing.md). Eine funktionierende Oberfläche ist keine Freigabe für das öffentliche Internet.

## Vertrauensgrenzen

Der Browser gilt als nicht vertrauenswürdig. Java prüft Rechte und Eingaben unabhängig von
Schaltflächen und Frontend-Prüfungen. Paketanfragen benötigen explizite Zugangsdaten oder bewusst
freigegebenen anonymen Lesezugriff. Repository-Rechte und Rollen werden im Kern durchgesetzt.
Tokens werden einmal angezeigt und können widerrufen werden; sie gehören nicht in URLs,
Local Storage oder Protokolle.

Browsersitzungen verwenden ein HttpOnly-Cookie, CSRF-Prüfungen und Origin-Validierung. Bei HTTPS
müssen `GR_COOKIE_SECURE=true` und die exakte externe `GR_PUBLIC_URL` gesetzt sein. Standardsprache
und Sprachverhandlung beeinflussen die Darstellung, nicht die Berechtigung.

Übersetzungen werden als JSON vom eigenen Ursprung geladen. Dynamische Inhalte werden für HTML
maskiert. Diagnosen können technische Kennungen enthalten; private Paketnamen dürfen nicht
ungeprüft veröffentlicht werden. Die Beispielgenerierung maskiert Shell-, XML- und Groovy-Werte,
macht beliebige Befehle aber nicht automatisch sicher.

## Upstream- und Dateisystemschutz

HTTP und Upstreams im privaten Netz sind ohne ausdrückliche Freigabe gesperrt. Ausgehende
Netzwerkregeln sind zusätzlich zu prüfen: URL-Kontrollen ersetzen weder Egress-Filter noch einen
umfassenden DNS-Rebinding-Audit. Ein Repository-Dienst sollte keinen Zugriff auf Cloud-Metadaten
oder interne Steuerungssysteme erhalten.

Kontrollierte Pfade und Hash-adressierte Dateien bilden den Speicher. Der Prozess braucht nur
minimale Dateisystemrechte. Persistente Daten bleiben außerhalb des Quellbaums. Backups enthalten
vertrauliche Paket- und Kontoinformationen, auch wenn Passwörter und Tokens nicht im Klartext vorliegen.

Das Abschalten von Upload-, JSON- und Parallelitätswächtern entfernt Ressourcenschutz.
Diese Wächter sind keine Lizenzbeschränkung; ihre Aktivierung erzeugt kein kommerzielles Kontingent.

## Vor externem Betrieb

Vollständige JDK-25-/Spring-Boot-Tests und echte Clients ausführen; auch verbotene Operationen
prüfen. Geheimnisse, TLS, Passwortregeln, Benutzerverwaltung, fehlerhafte Archive, große Anfragen,
Upstream-Isolation und Wiederherstellung untersuchen. Actuator-Zugriff begrenzen.
Last- und Ausfalltests müssen realistische Daten verwenden. Ein einzelner Prozess mit Metadaten
im RAM ist keine Hochverfügbarkeitsarchitektur.

Sicherheitsprobleme sind nach [SECURITY.de.md](../../SECURITY.de.md) vertraulich zu melden,
nicht als öffentliche Issues mit Exploit-Details oder echten Zugangsdaten.

## Vertrauensgrenzen bei Releases

Die Freigabe verlangt einen anderen Kontonamen, nicht den Nachweis zweier unabhängiger Personen. Administratoren können Identitäten anlegen; Betriebssystemadministratoren können lokalen Zustand ändern. Verkettete Ereignishashes erkennen unbeabsichtigte Änderungen, sind aber kein extern verankertes, unveränderliches Audit-System. Ein Root-Betreiber mit Signierschlüssel kann neue Nachweise erzeugen. Vertrauen in den Schlüssel separat herstellen und Hostzugriffe begrenzen.

Nachweise beschreiben historische Daten, keine aktuelle Widerrufsantwort oder Schwachstellenprüfung. Das CI-Gate gilt zum Prüfzeitpunkt; normale Paket-URLs sind nicht freigabepflichtig. Manuelle Sperren verhindern spätere binäre Lesezugriffe, holen aber keine bereits geladenen Dateien zurück, stoppen keine laufenden Streams und unterdrücken nicht zwingend Metadaten.

Tiefe Prüfungen lesen Blobs synchron unter der Datenspeichersperre. Der Endpunkt ist potenziell teuer. Nicht vertrauenswürdigen Konten keinen unnötig großen Datenumfang zugänglich machen. Parallelitätsgrenzen und Proxy-Timeouts bleiben relevant; neue Lizenzquoten gibt es nicht.

`evidence-key.json` und Backups schützen. Automatische Rotation, Hardware-Schlüsselverwahrung, externe Audit-Verankerung und Mandantentrennung fehlen. Siehe [Vertrauensmodell](release-assurance.md).
