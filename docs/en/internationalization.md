[English](../en/internationalization.md) · [Deutsch](../de/internationalization.md) · [Documentation](index.md)

# Internationalization

## Selection and persistence

The shipped document starts as `<html lang="en" dir="ltr">`. The application first checks a
supported `?lang=en|de|fa` query value, then `graph.repository.language` in localStorage,
then defaults to English. Browser locale and Java's system locale never determine the default.

The login screen, header and dialogs contain the same language selector. Selection changes
`html.lang`, `html.dir`, page titles, navigation, labels, validation messages, status labels,
dates, numeric formatting and client-guide comments. All selectors remain synchronized.

localStorage access is guarded; private browsing or denied storage does not break the console.
A failed optional catalog load preserves a working language. Missing individual translations
fall back to English. No password, CSRF value or access token is placed in localStorage.

## Files

```text
repository-server/src/main/resources/static/locales/en.json
repository-server/src/main/resources/static/locales/de.json
repository-server/src/main/resources/static/locales/fa.json
repository-server/src/main/resources/static/i18n.js
repository-server/src/main/resources/i18n/messages.properties
repository-server/src/main/resources/i18n/messages_de.properties
repository-server/src/main/resources/i18n/messages_fa.properties
repository-core/src/main/java/ir/graph/repo/core/i18n/SupportedLanguages.java
```

The three UI catalogs currently contain **381 identical keys**, including plural forms and
47 error-code translations. Backend bundles include these error codes and HTTP status fallbacks.
English is the unqualified `messages.properties` bundle.

## API contract

The console sends `Accept-Language: en`, `de` or `fa` on administrative requests.
The server accepts regional tags such as `de-DE` and `fa-IR` and weighted lists.
Missing, unsupported, malformed or oversized headers fall back to English.

Administrative error responses include `Content-Language` and `Vary: Accept-Language`.
Spring message-bundle fallback to the host locale is disabled. Package-client routes retain
English diagnostic messages and unmodified machine fields.

```json
{
  "error": "UNAUTHORIZED",
  "message": "Melden Sie sich an oder verwenden Sie ein gültiges Zugriffstoken.",
  "detail": "Authentication is required",
  "requestId": "example-only"
}
```

`detail` is a technical diagnostic, not a second translated UI paragraph. API property names,
HTTP status codes, package metadata, permission tokens and user-entered labels remain unchanged.

## Formatting and RTL

Numbers and display dates use `Intl` with `en-US`, `de-DE` or `fa-IR`.
Persian date presentation follows the browser's Persian locale/calendar behavior.
Stored timestamps and request values remain ISO timestamps. HTML `datetime-local` fields keep
their browser-defined calendar and are converted to ISO on submission.

CSS uses `margin-inline-start`, `inset-inline-start` and other logical properties.
Navigation, dialogs and tables mirror with the document direction. Technical content uses
`dir="ltr"` or `<bdi>` where appropriate. Mobile tables scroll within their container.

## Adding or changing translations

Update the same key in all three catalogs and preserve `{placeholder}` names.
Add backend error translations to all three properties bundles when introducing an error code.
Keep user content separate from translation strings and escape dynamic text before rendering.

Run `npm test`, `python scripts/check_repository.py`, the Java locale checks and the browser
fixture suite. Test dialogs with unsaved fields, selected files and one-time tokens; changing
language must not resubmit a request.

Adding a fourth language also requires updating `LANGUAGES`, the HTML selectors,
`SupportedLanguages`, Spring resource bundles, the static-resource whitelist and test matrices.
