[English](../en/api.md) · [Deutsch](../de/api.md) · [Documentation](index.md)

# Administrations-API

Die Administrations-API liegt unter `/api`. Paketwerkzeuge verwenden `/repository/{name}/…` oder
`/v2/…`; eine Browsersitzung ersetzt dort keine ausdrücklich übermittelten Paket-Zugangsdaten.

## Authentifizierung und Browseranfragen

Der Browser beginnt mit `POST /api/login`, einem JSON-Objekt mit `username` und `password` sowie
einem `Origin`, der zu `GR_PUBLIC_URL` passt. `GET /api/session` liefert Sitzungsinformationen und
das CSRF-Token. Spätere Änderungen mit Sitzungsanmeldung benötigen `X-CSRF-Token` und den passenden
Origin. Die mitgelieferte Oberfläche übernimmt dies. Abmelden beendet die Sitzung.

Automatisierte Aufrufer können Basic-Zugangsdaten oder ein persönliches Bearer-Token verwenden.
HTTPS, ein separates Konto mit minimalen Rechten und ein Secret-Manager werden empfohlen.
Tokens gehören nicht in Quellcode, Shell-Verläufe, URLs oder Screenshots. Leserechte auf Metadaten
erteilen keine Veröffentlichungsrechte.

```bash
export GR_BASE=https://repo.example.com
# GR_TOKEN im Secret-Manager oder in einer geschützten Shell-Umgebung setzen.
curl --fail --silent --show-error \
  -H "Authorization: Bearer $GR_TOKEN" \
  -H "Accept-Language: de" \
  "$GR_BASE/api/repos"
```

## Wichtigste Operationen

| Methode | Pfad | Zweck |
| --- | --- | --- |
| POST / GET / POST | `/api/login`, `/api/session`, `/api/logout` | Lebenszyklus der Browseranmeldung. |
| GET | `/api/stats`, `/api/system`, `/api/audit` | Übersicht, Konfiguration und Ereignisprotokoll. |
| GET / POST | `/api/repos` | Repositories auflisten oder anlegen. |
| GET / PUT / DELETE | `/api/repos/{name}` | Repository lesen, ändern oder löschen. |
| GET | `/api/assets?repo=…&q=…&offset=…&limit=…` | Dateien durchsuchen und seitenweise abrufen. |
| GET | `/api/download?repo=…&path=…` | Eine berechtigte Datei herunterladen. |
| PUT | `/api/upload?repo=…&path=…` | Raw- oder Maven-Datei in ein Hosted-Repository übertragen. |
| DELETE | `/api/asset` | Eine Datei anhand eines JSON-Auftrags löschen. |
| POST | `/api/pypi/yank` | Den Yanked-Status einer Python-Version ändern. |
| GET / POST / PUT / DELETE | `/api/users`, `/api/users/{name}` | Benutzer verwalten. |
| GET / POST / DELETE | `/api/tokens`, `/api/tokens/{id}` | Zugriffstokens auflisten, erstellen und widerrufen. |
| POST | `/api/password` | Eigenes Passwort ändern. |
| POST | `/api/maintenance` | Speicher prüfen, komprimieren oder mit Bestätigung bereinigen. |
| GET | `/api/openapi` | Administratives OpenAPI-Dokument abrufen. |
| GET | `/healthz`, `/actuator/health` | Zustand prüfen; Netzwerkzugriff begrenzen. |

Die maßgeblichen Routen stehen unter
[`web/`](../../repository-server/src/main/java/ir/graph/repo/server/web/).
Das mitgelieferte [OpenAPI-Dokument](../../repository-server/src/main/resources/openapi/graph-repository.openapi.json)
beschreibt die Administration, nicht sämtliche npm-, NuGet-, Python- oder OCI-Protokolle.

## Sprachauswahl

`Accept-Language: en`, `de`, `fa` oder eine gültige Präferenzliste wie `de-DE,de;q=0.9,en;q=0.8`
wird ausgewertet. Bei administrativen Fehlern liefert der Dienst `Content-Language` und
`Vary: Accept-Language`. Ohne unterstützte Präferenz gilt Englisch; die Betriebssystemsprache
ist kein Ersatzwert.

Ein Fehlerobjekt enthält:

```json
{
  "error": "FORBIDDEN",
  "message": "Lokalisierte Erklärung für den Benutzer.",
  "detail": "English technical diagnostic.",
  "requestId": "correlation-id"
}
```

Dieses Beispiel ist schematisch. `error` ist eine stabile maschinenlesbare Kennung, `message` ist
übersetzt und `detail` enthält bewusst die englische technische Diagnose. Programmlogik darf
nicht auf übersetzten Meldungen beruhen. Paketprotokolle und Kennungen bleiben technisch englisch.
Unerwartete Fehler veröffentlichen über dieses Standardformat keinen Stacktrace.

## Destruktive und aufwendige Operationen

Die Oberfläche verlangt vor Lösch- und Wartungsvorgängen eine Bestätigung. Automatisierung muss
dieselben vom Controller erwarteten Bestätigungsfelder liefern. Diese Schutzmaßnahmen sollten
nicht aus Bequemlichkeit entfernt werden. Ein Testlauf der Speicherbereinigung ist kein Backup.
Vor destruktiven Arbeiten sind Rechte zu prüfen, eine Offline-Sicherung anzulegen und deren
Wiederherstellung zu erproben.

Vor öffentlichem Betrieb sind [Sicherheitsprüfung](security.md) und [Laufzeittests](testing.md) nötig.

## Neue Release- und Speicher-APIs in 0.5

| Methode und Pfad | Zweck |
| --- | --- |
| `GET /api/releases` | Berechtigungsbezogene Liste mit state/offset/limit. |
| `POST /api/releases` | Explizite Dateireferenzen erfassen. |
| `GET /api/releases/{id}` | Festes Manifest, Historie und erlaubte Aktionen. |
| `POST /api/releases/{id}/transitions/{action}` | Revisionsgeprüftes submit/approve/reject/release/revoke. |
| `GET /api/releases/{id}/gate?deep=true` | Aktueller Zustand, Hashwerte, Quarantäne und Signatur. |
| `GET /api/releases/{id}/evidence` | Historischer signierter JSON-Anhang. |
| `GET /api/releases/{id}/assets/{index}` | Fester Download bei aktuell veröffentlichtem Zustand. |
| `GET /api/release-signing-key` | Öffentliche Signieridentität; nur Administratoren. |
| `GET /api/quarantine` / `POST /api/quarantine` | Digest-Sperren lesen oder ändern; nur Administratoren. |
| `GET /api/insights/storage` | Sichtbare Speicherzahlen; Hostkapazität nur für Administratoren. |

`approve` ist ein eigenes Recht neben `write`. Quarantäne benötigt `sha256`, boolesches `active`, eine nicht leere `reason` und `expectedRevision` (0 beim Anlegen). Entscheidungen verlangen `note` und `expectedRevision`.

Neue stabile Fehlercodes sind unter anderem `SELF_APPROVAL`, `STALE_RELEASE`, `RELEASE_INTEGRITY`, `NO_EVIDENCE`, `RELEASE_NOT_AVAILABLE`, `CONTENT_HELD`, `STALE_HOLD` und `RELEASE_REFERENCE`. Gesperrte binäre Antworten verwenden HTTP 423. Admin-Fehler folgen der gewählten Sprache; technische Codes und Paketdaten bleiben unverändert. Details: [Release-Semantik](release-assurance.md) und aktualisierte OpenAPI-Datei.
