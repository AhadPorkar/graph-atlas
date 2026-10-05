[English](../en/api.md) · [Deutsch](../de/api.md) · [Documentation](index.md)

# Administration API

The administration API is under `/api`. Package clients use `/repository/{name}/…` or `/v2/…`;
they do not use the UI session as a substitute for explicit package credentials.

## Authentication and browser requests

A browser starts with `POST /api/login`, sending a JSON `username` and `password` and an `Origin`
matching `GR_PUBLIC_URL`. `GET /api/session` returns session information and the CSRF token.
Subsequent session-authenticated changes send `X-CSRF-Token` and the matching origin. The shipped
console does this automatically. Logging out invalidates the session.

Non-browser callers can authenticate with a Basic credential or a personal Bearer token.
Use HTTPS, a dedicated least-privileged user and a secret manager. Do not store tokens in source
code, command histories, URLs or screenshots. Read access to metadata does not grant publish rights.

```bash
export GR_BASE=https://repo.example.com
# Set GR_TOKEN in your secret manager or protected shell environment.
curl --fail --silent --show-error \
  -H "Authorization: Bearer $GR_TOKEN" \
  -H "Accept-Language: de" \
  "$GR_BASE/api/repos"
```

## Main operations

| Method | Path | Purpose |
| --- | --- | --- |
| POST / GET / POST | `/api/login`, `/api/session`, `/api/logout` | Browser authentication lifecycle. |
| GET | `/api/stats`, `/api/system`, `/api/audit` | Summary, configuration and audit information. |
| GET / POST | `/api/repos` | List or create repositories. |
| GET / PUT / DELETE | `/api/repos/{name}` | Read, change or delete a repository. |
| GET | `/api/assets?repo=…&q=…&offset=…&limit=…` | Search and page through assets. |
| GET | `/api/download?repo=…&path=…` | Download an authorized asset. |
| PUT | `/api/upload?repo=…&path=…` | Stream a Raw or Maven file to a hosted repository. |
| DELETE | `/api/asset` | Delete an asset using a JSON body. |
| POST | `/api/pypi/yank` | Change a Python release's yanked state. |
| GET / POST / PUT / DELETE | `/api/users`, `/api/users/{name}` | User administration. |
| GET / POST / DELETE | `/api/tokens`, `/api/tokens/{id}` | List, issue and revoke access tokens. |
| POST | `/api/password` | Rotate the current user's password. |
| POST | `/api/maintenance` | Verify, compact or garbage-collect storage with explicit confirmation. |
| GET | `/api/openapi` | Read the administrative OpenAPI document. |
| GET | `/healthz`, `/actuator/health` | Health endpoints; control network exposure. |

The authoritative route definitions are in
[`web/`](../../repository-server/src/main/java/ir/graph/repo/server/web/).
The bundled [OpenAPI document](../../repository-server/src/main/resources/openapi/graph-repository.openapi.json)
is an administrative reference, not a complete specification of npm, NuGet, Python or OCI.

## Language negotiation

Send `Accept-Language: en`, `de`, `fa`, or a valid preference list such as `de-DE,de;q=0.9,en;q=0.8`.
The service responds to administrative errors with `Content-Language` and `Vary: Accept-Language`.
No supported preference means English; server operating-system locale is not a fallback.

An error envelope contains:

```json
{
  "error": "FORBIDDEN",
  "message": "Localized human-readable explanation.",
  "detail": "English technical diagnostic.",
  "requestId": "correlation-id"
}
```

The example is schematic. `error` is a stable machine identifier; `message` is localized;
`detail` intentionally retains the original technical diagnostic. Never parse a translated message
to decide client behavior. Package protocol responses and identifiers remain technical English.
Unexpected failures do not expose a stack trace through the standard envelope.

## Destructive and expensive operations

The console asks for confirmation before removal and maintenance. Automation must still provide
the confirmation fields expected by the controller; do not remove those protections for convenience.
A dry-run garbage-collection result is not a backup. Check repository grants, keep an offline backup
and validate a restore before destructive operational work.

Public deployment needs [security review](security.md) and [runtime verification](testing.md).

## Release and storage APIs added in 0.5

| Method and path | Purpose |
| --- | --- |
| `GET /api/releases` | Permission-scoped listing; state/offset/limit. |
| `POST /api/releases` | Capture explicit file references. |
| `GET /api/releases/{id}` | Frozen manifest, history and caller's allowed actions. |
| `POST /api/releases/{id}/transitions/{action}` | Revision-checked submit/approve/reject/release/revoke. |
| `GET /api/releases/{id}/gate?deep=true` | Current state, deep hashes, holds and signature result. |
| `GET /api/releases/{id}/evidence` | Historical signed JSON attachment. |
| `GET /api/releases/{id}/assets/{index}` | Frozen file download for a currently released capsule. |
| `GET /api/release-signing-key` | Public signing identity; administrator only. |
| `GET /api/quarantine` / `POST /api/quarantine` | List or mutate digest holds; administrator only. |
| `GET /api/insights/storage` | Permission-scoped accounting; host capacity for administrators only. |

The new `approve` grant is separate from `write`. Holds require `sha256`, boolean `active`, nonempty `reason` and `expectedRevision` (0 when creating). Decisions require `note` and `expectedRevision`.

New stable codes include `SELF_APPROVAL`, `STALE_RELEASE`, `RELEASE_INTEGRITY`, `NO_EVIDENCE`, `RELEASE_NOT_AVAILABLE`, `CONTENT_HELD`, `STALE_HOLD` and `RELEASE_REFERENCE`. Held binary requests receive HTTP 423. Errors in the admin API follow the selected language; technical codes and package payloads stay stable. See [release semantics](release-assurance.md) and the updated OpenAPI source.
