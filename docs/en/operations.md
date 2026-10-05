[English](../en/operations.md) · [Deutsch](../de/operations.md) · [Documentation](index.md)

# Operations and deployment

This is a single-node development preview. Keep it on a controlled network until the full Spring
Boot build, client acceptance, backup recovery and security review have passed in your environment.

## Build prerequisites

Use a full JDK 25 and Maven 3.6.3+. `JAVA_HOME` must reference the JDK used by Maven.
`bash scripts/build.sh` runs the Maven reactor and only creates `dist/graph-repository.jar` after
successful verification. `JAVA_RELEASE=21 bash scripts/build.sh` selects the compatibility target.
The source ZIP includes both modules, static assets, tests and deploy files, but not external
Maven dependencies, JDK binaries or a pre-verified Spring Boot JAR.

Node 22+ is only needed for frontend tests; there is no frontend bundler or runtime npm dependency.
Python and Playwright are test/development tools, not requirements for ordinary Java serving.

## Configuration

Set variables in the process environment or in your service manager. `scripts/run.sh` does **not**
source `.env`. Docker Compose reads its project `.env`; `.env.example` is a template.

| Variable | Default | Meaning |
| --- | --- | --- |
| `GR_HOME` | `data` | Persistent data directory; one process only. |
| `GR_BIND` / `GR_PORT` | `127.0.0.1` / `8081` | Listener. |
| `GR_PUBLIC_URL` | `http://localhost:8081` | External base URL and expected browser origin. |
| `GR_COOKIE_SECURE` | `false` | Enable for HTTPS deployments. |
| `GR_MAX_UPLOAD_BYTES` | `0` | Per-upload guard; zero means no application size ceiling. |
| `GR_MAX_JSON_BYTES` | `67108864` | JSON memory guard; zero disables it. |
| `GR_CONCURRENT_REQUESTS` | `0` | Application concurrency guard; zero disables it. |
| `GR_MAX_CONNECTIONS` | `-1` | Tomcat connection guard; `-1` is unlimited. |
| `GR_ALLOW_PRIVATE_UPSTREAM` | `false` | Explicitly opt in to private-address upstreams. |
| `GR_ALLOW_HTTP_UPSTREAM` | `false` | Explicitly opt in to unencrypted upstream HTTP. |
| `GR_LOGIN_FAILURE_LIMIT` | `10` | Failed-login window threshold, not a license limit. |
| `GR_LOGIN_FAILURE_WINDOW` | `60s` | Failed-login accounting window. |
| `GR_BOOTSTRAP_PASSWORD` | empty | Optional fresh-store password; prefer generated bootstrap secret. |

These are operational settings, not commercial quotas. Unlimited configuration cannot create
unlimited memory, disk, file descriptors or bandwidth. The metadata index currently resides in RAM.

## First start and credentials

```bash
export GR_HOME="$PWD/data"
export GR_PUBLIC_URL=http://localhost:8081
bash scripts/run.sh
cat data/admin.password
```

Use `admin`, save the generated password securely, rotate it in the console and remove the
bootstrap file. A supplied bootstrap password is only for initial store creation, not a reset
on every restart. Never publish data, backups or real credentials.

## HTTPS and systemd

[`deploy/nginx.conf`](../../deploy/nginx.conf) and
[`deploy/graph-repository.service`](../../deploy/graph-repository.service) are editable examples.
Replace hostnames, certificate paths, JDK paths and service accounts before installation.
For TLS termination set `GR_PUBLIC_URL=https://repo.example.com` and `GR_COOKIE_SECURE=true`.
Keep the Java listener on loopback. Do not normalize or rewrite encoded npm paths.

The systemd example expects a protected environment file at
`/etc/graph-repository/repository.env`, application files under `/opt/graph-repository` and data
under `/var/lib/graph-repository`. Create a non-login service user with ownership only of its data.
Restrict metrics and administration at the network boundary as well as in the application.

## Docker

```bash
cp .env.example .env
# Review .env and set the externally visible origin.
docker compose up -d --build
```

The Dockerfile builds from this source and downloads Maven dependencies. It is not an already
verified image. Compose publishes the local port on loopback and uses a persistent data volume.
Inspect configuration before changing bindings or replacing an existing volume.

## Offline backup and recovery

Stop the server first. Set `GR_HOME` to the real data location:

```bash
export GR_HOME=/var/lib/graph-repository
java -jar dist/graph-repository.jar --verify
java -jar dist/graph-repository.jar --backup /safe/location/graph-backup.zip
python scripts/restore.py /safe/location/graph-backup.zip /safe/location/restored-data
GR_HOME=/safe/location/restored-data java -jar dist/graph-repository.jar --verify
```

The restore destination must not already exist. The helper rejects unsafe archive paths and
symlinks and excludes process-lock/bootstrap-password files; it is not a malware scanner.
Protect backups because they contain repository data and account/token records. Verify blob
integrity and perform a real application/client restore drill before declaring recovery successful.

`--compact` and `--reset-admin-password` are additional offline commands. Do not run them against
a live store. Online verification/compaction/GC exist in the console; a garbage-collection preview
does not replace a backup.

## Troubleshooting

A failed Maven download is not evidence of a source error; inspect proxy/mirror and TLS settings.
A JDK mismatch is visible in `mvn -version`. A browser-origin error usually means that scheme,
host or port differs from `GR_PUBLIC_URL`. Log out and authenticate again after changing origin.
For locale problems, inspect `/locales/en.json`, the saved language preference and `Accept-Language`.
Use correlation IDs for errors without exposing tokens or private package names in public reports.

## New release data

Read [the migration guide](migration.md) before enabling capsules. Every release state pins content. Backups now contain the private signing file `evidence-key.json`; keep it out of Git and protect backup access. Restoring the key with its matching metadata is essential. Deep gates can delay writes.
