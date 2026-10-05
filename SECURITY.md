# Security policy

[Deutsch](SECURITY.de.md)

Graph Atlas is a development preview. There is no published stable support window or
independent security certification. Do not assume that older versions receive fixes.

## Report a suspected vulnerability

Use GitHub's private vulnerability reporting on the repository **when its owner has enabled it**.
If it is not enabled, ask the owner to establish a private channel without disclosing exploit
details in a public issue. This source template intentionally does not invent an email address.

Provide the affected commit/version, environment, impact and a minimal sanitized reproduction.
Do not include real tokens, passwords, private packages, customer data or a working public exploit.
Keep details private while maintainers investigate; no guaranteed response deadline is advertised.

## Before making this repository public

The owner should enable private reporting, configure a real contact route and review dependency
alerts and [deployment security](docs/en/security.md). CI definitions and a polished README are
not proof that the application is safe for production.
