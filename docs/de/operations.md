[English](../en/operations.md) · [Deutsch](../de/operations.md) · [Documentation](index.md)

# Betrieb und Bereitstellung

Dies ist eine Einzelknoten-Entwicklungsvorschau. Bis Spring-Boot-Build, Client-Abnahme,
Wiederherstellung und Sicherheitsprüfung in Ihrer Umgebung bestanden sind, bleibt der Dienst
in einem kontrollierten Netzwerk.

## Voraussetzungen für den Build

Benötigt werden ein vollständiges JDK 25 und Maven ab 3.6.3. `JAVA_HOME` muss zu dem von Maven
verwendeten JDK passen. `bash scripts/build.sh` prüft den Maven-Reaktor und erstellt
`dist/graph-repository.jar` erst nach erfolgreicher Verifikation.
`JAVA_RELEASE=21 bash scripts/build.sh` wählt das Kompatibilitätsziel.
Das Quellarchiv enthält beide Module, statische Dateien, Tests und Deployment-Vorlagen,
aber keine externen Maven-Abhängigkeiten, JDK-Binärdateien oder verifizierte Spring-Boot-JAR.

Node ab Version 22 ist nur für Frontend-Tests nötig; es gibt weder Frontend-Bundler noch
npm-Laufzeitabhängigkeiten. Python und Playwright sind Entwicklungs- und Testwerkzeuge.

## Konfiguration

Variablen werden in der Prozessumgebung oder im Dienstmanager gesetzt. `scripts/run.sh` liest
`.env` **nicht** automatisch ein. Docker Compose liest die projektbezogene `.env`;
`.env.example` dient als Vorlage.

| Variable | Standard | Bedeutung |
| --- | --- | --- |
| `GR_HOME` | `data` | Persistentes Datenverzeichnis; nur ein Prozess. |
| `GR_BIND` / `GR_PORT` | `127.0.0.1` / `8081` | Netzwerkbindung. |
| `GR_PUBLIC_URL` | `http://localhost:8081` | Externe Basis-URL und erwarteter Browser-Origin. |
| `GR_COOKIE_SECURE` | `false` | Für HTTPS aktivieren. |
| `GR_MAX_UPLOAD_BYTES` | `0` | Größenwächter je Upload; null bedeutet kein Anwendungslimit. |
| `GR_MAX_JSON_BYTES` | `67108864` | JSON-Speicherschutz; null deaktiviert ihn. |
| `GR_CONCURRENT_REQUESTS` | `0` | Parallelitätswächter; null deaktiviert ihn. |
| `GR_MAX_CONNECTIONS` | `-1` | Tomcat-Verbindungsgrenze; `-1` ist unbegrenzt. |
| `GR_ALLOW_PRIVATE_UPSTREAM` | `false` | Private Upstream-Adressen ausdrücklich erlauben. |
| `GR_ALLOW_HTTP_UPSTREAM` | `false` | Unverschlüsseltes Upstream-HTTP ausdrücklich erlauben. |
| `GR_LOGIN_FAILURE_LIMIT` | `10` | Fehlanmeldungen pro Zeitfenster; kein Lizenzlimit. |
| `GR_LOGIN_FAILURE_WINDOW` | `60s` | Zeitfenster für Fehlanmeldungen. |
| `GR_BOOTSTRAP_PASSWORD` | leer | Optionales Passwort nur für einen neuen Speicher. |

Diese Einstellungen schützen den Betrieb und sind keine kommerziellen Kontingente.
Unbegrenzte Konfiguration erzeugt keinen unbegrenzten Arbeitsspeicher, Plattenplatz, Dateideskriptoren
oder Netzwerkdurchsatz. Der Metadatenindex liegt derzeit im RAM.

## Erster Start und Zugangsdaten

```bash
export GR_HOME="$PWD/data"
export GR_PUBLIC_URL=http://localhost:8081
bash scripts/run.sh
cat data/admin.password
```

Mit `admin` anmelden, das erzeugte Passwort sicher hinterlegen, in der Oberfläche ändern und
die Bootstrap-Datei entfernen. Ein vorgegebenes Bootstrap-Passwort gilt nur beim ersten Anlegen
des Speichers und setzt nicht bei jedem Neustart das Passwort zurück. Daten, Backups und echte
Zugangsdaten dürfen nicht veröffentlicht werden.

## HTTPS und systemd

[`deploy/nginx.conf`](../../deploy/nginx.conf) und
[`deploy/graph-repository.service`](../../deploy/graph-repository.service) sind anpassbare Vorlagen.
Vor der Installation Hostnamen, Zertifikatspfade, JDK-Pfade und Dienstkonten ersetzen.
Für TLS-Terminierung gelten `GR_PUBLIC_URL=https://repo.example.com` und `GR_COOKIE_SECURE=true`.
Der Java-Listener bleibt auf Loopback. Kodierte npm-Pfade dürfen nicht umgeschrieben werden.

Die systemd-Vorlage erwartet eine geschützte Umgebungsdatei unter
`/etc/graph-repository/repository.env`, die Anwendung unter `/opt/graph-repository` und Daten
unter `/var/lib/graph-repository`. Ein Dienstkonto ohne interaktive Anmeldung erhält nur die
erforderlichen Rechte am Datenverzeichnis. Metriken und Administration sind zusätzlich auf
Netzwerkebene zu beschränken.

## Docker

```bash
cp .env.example .env
# .env prüfen und den extern sichtbaren Origin festlegen.
docker compose up -d --build
```

Das Dockerfile baut aus diesem Quellcode und lädt Maven-Abhängigkeiten. Es handelt sich nicht
um ein bereits geprüftes Image. Compose bindet den lokalen Port an Loopback und verwendet ein
persistentes Datenvolume. Bindungen und bestehende Volumes nicht ungeprüft verändern.

## Offline-Sicherung und Wiederherstellung

Den Server zuerst anhalten. `GR_HOME` muss auf die echten Daten zeigen:

```bash
export GR_HOME=/var/lib/graph-repository
java -jar dist/graph-repository.jar --verify
java -jar dist/graph-repository.jar --backup /safe/location/graph-backup.zip
python scripts/restore.py /safe/location/graph-backup.zip /safe/location/restored-data
GR_HOME=/safe/location/restored-data java -jar dist/graph-repository.jar --verify
```

Das Zielverzeichnis darf noch nicht existieren. Das Wiederherstellungswerkzeug weist unsichere
Archivpfade und symbolische Links zurück und übernimmt weder Prozesssperre noch Bootstrap-
Passwortdatei. Es ist kein Malware-Scanner. Sicherungen enthalten Paketdaten sowie Konto- und
Token-Datensätze und müssen geschützt werden. Erst Blob-Prüfung und ein echter Anwendungstest
mit Clients belegen eine erfolgreiche Wiederherstellung.

Weitere Offline-Befehle sind `--compact` und `--reset-admin-password`. Sie dürfen nicht parallel
zum laufenden Speicher ausgeführt werden. Online-Prüfung, Komprimierung und Bereinigung stehen
in der Oberfläche bereit; eine Bereinigungsvorschau ersetzt kein Backup.

## Fehlerdiagnose

Ein fehlgeschlagener Maven-Download beweist keinen Quellcodefehler; Proxy, Mirror und TLS prüfen.
`mvn -version` zeigt JDK-Abweichungen. Bei Origin-Fehlern stimmen meist Schema, Host oder Port
nicht mit `GR_PUBLIC_URL` überein. Nach Änderungen neu anmelden. Bei Sprachproblemen sind
`/locales/en.json`, die gespeicherte Sprachwahl und `Accept-Language` zu prüfen.
Korrelationskennungen erleichtern die Diagnose; Tokens und private Paketnamen gehören nicht
in öffentliche Fehlerberichte.

## Neue Release-Daten

Vor dem Einsatz von Capsules [Migration](migration.md) lesen. Jeder Zustand bindet Dateien. Backups enthalten den privaten Signierschlüssel `evidence-key.json`; nicht in Git aufnehmen und Backups schützen. Passenden Schlüssel und Metadaten gemeinsam wiederherstellen. Tiefe Gates können Schreibzugriffe verzögern.
