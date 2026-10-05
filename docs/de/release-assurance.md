# Release-Nachweise und Freigaben

[English](../en/release-assurance.md) · [Dokumentation](index.md).

## Eine Capsule, genau definierte Bytes

Eine Capsule enthält explizite Referenzen aus `repo` und `path`. Beim Erstellen ermittelt der Server Format, SHA-256, Dateilänge und Inhaltstyp. Das sortierte Manifest und sein Digest werden gespeichert. Dateien werden nicht kopiert; die inhaltsadressierten Blobs bleiben erhalten.

Die Auswahl ist ausdrücklich dateibezogen. Sie ermittelt keine transitiven Abhängigkeiten, setzt keine Docker-Images zusammen, ordnet nicht automatisch npm-Metadaten einem Tarball zu und ergänzt keine Maven-POM- oder Prüfsummendateien. Für ein vollständiges Deployment müssen alle erforderlichen Dateien aufgenommen oder ein vorbereitetes Deployment-Archiv bereitgestellt werden. Das ist Dateifreigabe, keine formatspezifische Repository-Promotion.

Name und Version sind technische ASCII-Kennungen; Notizen dürfen mehrsprachig sein. Die Kombination aus Name und Version ist eindeutig. Der Ersteller benötigt `read` und `write` auf jeder Quell-Registry.

## Zustände und Rechte

```text
DRAFT ----submit----> IN_REVIEW ----approve----> APPROVED ----release----> RELEASED
                         |                                                  |
                       reject                                             revoke
                         |                                                  |
                         v                                                  v
                      REJECTED                                           REVOKED
```

Eine neue Capsule beginnt mit Revision 1. Jede Änderung verlangt die aktuelle `expectedRevision` und eine nicht leere Begründung. Ein veralteter Änderungsstand führt zu HTTP 409 und überschreibt keine parallele Entscheidung.

Ersteller oder Administratoren mit Schreibrechten auf den Quellen dürfen einreichen, veröffentlichen und widerrufen. Freigabe und Ablehnung erfordern `read` sowie `approve` auf sämtlichen Quellen und einen anderen Benutzernamen als den des Erstellers. `write` enthält nicht automatisch `approve`. Auch Administratoren dürfen ihre eigene Capsule nicht freigeben. Getrennt werden Konten, nicht nachweislich verschiedene Menschen. Kontoadministratoren bleiben vertrauenswürdige Stellen.

Beispiel für die Rechte eines Prüfers:

```json
{
  "raw-hosted": ["read", "approve"],
  "maven-releases": ["read", "approve"]
}
```

`REJECTED` und `REVOKED` sind Endzustände. Für einen neuen Ablauf wird eine neue Version erstellt. Das Löschen von Capsules ist nicht implementiert.

## API-Ablauf

Dateien zuerst über normale Paketprotokolle oder einen lokalen Raw-/Maven-Endpunkt veröffentlichen. Danach eine Capsule erstellen:

```http
POST /api/releases
Content-Type: application/json
Authorization: Bearer <author-token>
```

```json
{
  "name": "checkout-service",
  "version": "2026.10.0",
  "note": "Deployment-Kandidat für die Abnahmeumgebung.",
  "assets": [
    {"repo": "raw-hosted", "path": "releases/checkout.zip"},
    {"repo": "raw-hosted", "path": "releases/config.json"}
  ]
}
```

Die Antwort liefert eine UUID. Folgende Endpunkte werden mit den jeweils berechtigten Ersteller- und Prüferkonten aufgerufen:

```text
POST /api/releases/{id}/transitions/submit
POST /api/releases/{id}/transitions/approve
POST /api/releases/{id}/transitions/release
```

Der Body enthält Revision und Begründung. Im Betrieb immer die zuletzt gelesene Revision verwenden, keine fest einprogrammierte Folge:

```json
{"expectedRevision": 1, "note": "Bitte das feste Manifest prüfen."}
```

Liste und Details berücksichtigen Berechtigungen. Bei mehreren Quell-Registries müssen alle lesbar sein. Die Liste unterstützt `state`, `offset` und `limit`; maximal 200 Einträge je Seite sind eine Seitengröße, keine kommerzielle Quote.

## Gate und feste Downloads

`GET /api/releases/{id}/gate?deep=true` liest die erfassten Dateien und prüft Hashwerte, Größen, Quarantäne, Ereigniskette und Signatur. Nur eine veröffentlichte Capsule ohne entsprechende Probleme erhält `allowed:true`. `sourceDrift` zählt geänderte oder entfernte ursprüngliche Pfade. Das ist ein Hinweis, kein Fehler, weil feste Downloads den erfassten Digest verwenden.

Der Python-Client prüft Kennung, boolesche Entscheidung, tiefe Prüfung, Status, Signatur, Integrität und Problemliste. Weiterleitungen und entferntes HTTP werden abgelehnt. Zeitüberschreitung, fehlende Anmeldung, falsche Kennung, ungültige Antwort oder widersprüchliche Entscheidung führen nicht zur Freigabe. `GR_TOKEN` gehört in den Secret-Speicher der CI. Eine [Workflow-Vorlage](../../examples/ci/atlas-release-gate.yml) ist enthalten.

**Das Gate muss ausdrücklich in die Pipeline eingebaut werden.** Normale Registry-Endpunkte verlangen keine Capsule-Freigabe. Das Gate führt kein Deployment aus und hält den Status nicht während der restlichen Pipeline gesperrt.

`GET /api/releases/{id}/assets/{index}` liefert nach der Veröffentlichung die feste Datei. Änderungen am ursprünglichen Pfad verändern diesen Download nicht. Der Client sollte den Hash gegen einen vertrauenswürdigen Nachweis prüfen. Widerruf oder Quarantäne verhindern spätere feste Downloads, können aber bereits übertragene oder gerade gestreamte Bytes nicht zurückholen.

## Signierter Nachweis und externe Vertrauensbasis

`GET /api/releases/{id}/evidence` liefert einen Atlas-spezifischen JSON-Umschlag. Signiert werden die exakten UTF-8-Bytes der Nutzdaten mit Manifest und Entscheidungen bis zur Veröffentlichung. Das Format verwendet Ed25519, Base64 und einen X.509-kodierten öffentlichen Schlüssel. `keyId` ist dessen SHA-256.

Dies ist **kein DSSE-, SLSA- oder Sigstore-Nachweis, kein Transparenzprotokoll und kein zertifizierter Zeitstempel**. Das Schema lautet `graph-atlas.evidence/v1`, der Payload-Typ `application/vnd.graph-atlas.release.v1+json`. Objektschlüssel werden für diese Implementierung rekursiv sortiert; eine RFC-8785-Konformität wird nicht behauptet.

Administratoren erhalten die öffentliche Identität über `GET /api/release-signing-key`. Der Fingerabdruck muss über einen separat authentifizierten Kanal bestätigt und fest hinterlegt werden. Der im untrusted Nachweis mitgelieferte Schlüssel darf nicht automatisch die Vertrauensbasis bestimmen.

```bash
bash scripts/verify-evidence.sh evidence.json TRUSTED_PUBLIC_KEY_SHA256
```

Die Prüfung benötigt JDK 21+, aber weder Maven noch externe Bibliotheken oder einen laufenden Server. Für Windows existiert `verify-evidence.ps1`. Das Ergebnis nennt ausdrücklich `currentRevocationChecked:false` und `artifactBytesChecked:false`.

## Schlüssel, Aufbewahrung und Ressourcen

Der Instanzschlüssel wird bei Bedarf atomar in `GR_HOME/evidence-key.json` angelegt und erhält, soweit unterstützt, reine Eigentümerrechte. Unter Windows muss der Betreiber passende ACLs setzen. Backups enthalten den privaten Schlüssel und müssen extern verschlüsselt sowie zugriffsgeschützt werden. Fehlt der Schlüssel trotz vorhandener signierter Capsules, erfolgt keine automatische Neuerzeugung. Löschen ist keine unterstützte Rotation. Automatische Rotation, HSM-Anbindung und Wiederherstellung ohne Backup fehlen.

Alle Zustände binden ihre Dateien, auch Entwürfe, Ablehnungen und Widerrufe. Die Speicherbereinigung darf diese Blobs nicht entfernen. Referenzierte Registries können nicht gelöscht werden. Ablaufregeln oder eine Capsule-Bereinigung sind in dieser Vorschau nicht enthalten.

Tiefe Verifikation erfolgt synchron unter der Datenspeichersperre. Große Datenmengen können Schreibzugriffe verzögern. Realistische Abnahmetests und betriebliche Timeouts sind nötig; es wird keine Durchsatzgarantie gegeben.
