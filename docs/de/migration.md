# Migration zu Graph Atlas 0.5

[English](../en/migration.md) · [Dokumentation](index.md).

Der Arbeitsname lautet Graph Atlas. Aus Kompatibilitätsgründen bleiben `graph-repository.jar`, der Maven-Parent `graph-repository-parent`, Java-Pakete unter `ir.graph.repo` und `GR_`-Variablen bestehen. Englisch bleibt die Ausgangssprache; gespeicherte deutsche oder persische Einstellungen bleiben nutzbar.

## Vorwärtskompatibilität bedeutet keinen sicheren Downgrade

Der Datenspeicher ergänzt die Tabellen `releases` und `holds`. Neue Software kann den bisherigen Tabellensatz lesen. Sobald neue Tabellen in WAL oder Snapshot geschrieben wurden, **darf eine alte Anwendung nicht auf diesem geänderten Verzeichnis gestartet werden**. Frühere Versionen kennen diese Tabellennamen nicht. Rollback bedeutet Wiederherstellung des unveränderten Vorab-Backups mit der alten Binärdatei, nicht nur Austausch des JAR.

Die vorherige Lieferung enthielt einen Legacy-Kompatibilitätstest. Hier wurden Persistenz und Neustart des Kerns geprüft, nicht sämtliche historischen Spring-Laufzeitmigrationen.

## Sicheres Vorgehen

Alten Dienst stoppen und außerhalb des Datenverzeichnisses offline sichern. Dieses Backup getrennt aufbewahren.

```bash
# Alter Dienst gestoppt; altes GR_HOME und alte Anwendung.
java -jar /path/to/old-application.jar --backup /secure-backups/before-atlas.zip

python scripts/restore.py /secure-backups/before-atlas.zip ./data-atlas-test
export GR_HOME="$PWD/data-atlas-test"

# Vor dem Test der Kopie muss der Build erfolgreich sein.
bash scripts/build.sh
java -jar dist/graph-repository.jar --verify
```

Neue Version gegen die Kopie starten, echte Clients und Freigaben testen und anschließend ein Backup der neuen Version wiederherstellen. Niemals zwei Prozesse auf demselben Datenverzeichnis betreiben. TLS, sichere Cookies, `GR_PUBLIC_URL`, Dateirechte und Backup-Regeln erneut prüfen.

## Signieridentität gehört zum Backup

Die Identität entsteht bei der ersten Verwendung. `GR_HOME/evidence-key.json` enthält einen privaten Schlüssel. Die Datei gehört zum Datenbackup, nicht ins Quellrepository. Backup und Restore erhalten sie mit restriktiven Rechten, soweit unterstützt.

Backups extern verschlüsseln und getrennt schützen. Nach Verlust des Schlüssels werden signierte Operationen nicht durch eine automatisch erzeugte Ersatzidentität fortgesetzt. Rotation ist nicht implementiert. Zur Wiederherstellung passenden Schlüssel und Daten gemeinsam zurückspielen, Fingerabdruck bestätigen und vorhandene Nachweise prüfen.

Alle Capsule-Zustände behalten Dateien. Ein Widerruf beseitigt weder Historie noch Inhalte. Kapazität planen; automatische Ablaufregeln oder Capsule-Bereinigung existieren nicht.

Vor dem Produktionswechsel ist eine tatsächliche Laufzeitabnahme nötig, nicht nur Syntaxanalyse.
