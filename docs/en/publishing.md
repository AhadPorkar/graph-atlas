[English](../en/publishing.md) · [Deutsch](../de/publishing.md) · [Documentation](index.md)

# Publish the project on GitHub

The archive is a source repository, not a pre-created GitHub repository. It contains no `.git`
history, remote URL, real credentials or fictional contributor identity. Upload the **contents of
the `graph-repository` folder** as the repository root so README and workflows are discovered.

## Review before the first push

Read the verification caveat, the MIT license and third-party notice. Confirm you have permission
to publish any code/assets you add. Remove private configuration, package data and screenshots
of real systems. `.gitignore` is a safeguard, not a secret scanner.

```bash
python scripts/check_repository.py
git init -b main
git add .
git status --short
git diff --cached --stat
git diff --cached --check
git commit -m "Add multilingual self-hosted package repository"
```

Create an **empty** repository named `graph-repository` in your GitHub account. Do not initialize
another README, license or `.gitignore` over this source. Then use your actual account:

```bash
git remote add origin https://github.com/YOUR_USERNAME/graph-atlas.git
git push -u origin main
```

`YOUR_USERNAME` is an intentional setup placeholder, not a required source-code change.
Alternatively use GitHub CLI after authentication; neither this archive nor its scripts uploads
anything or authenticates on your behalf.

## Repository presentation

Suggested description:

> Self-hosted Java/Spring Boot package repository with English, German and Persian UI, content-addressed storage and repository-level permissions.

Suggested topics: `java`, `spring-boot`, `package-registry`, `maven`, `npm`, `nuget`, `pypi`,
`docker-registry`, `i18n`, `rtl`, `self-hosted`.

The English README is the default, with a visible German link. Fixture screenshots and architecture
diagrams are already included. Do not present fixture counts as real usage or throughput.
Only add CI badges after the remote exists and the corresponding job actually runs.

## Repository maintenance

Enable GitHub Actions and review the first CI results. Add branch protection and required checks
only after the workflows are operational. Configure private vulnerability reporting and a real
maintainer contact before inviting external users; no fictitious address is embedded here.
Issue and pull-request templates are available in English and German.

The source ZIP includes CI configuration, not a successful remote run. Address build/test failures
before creating a stable release. Start with a clearly labelled prerelease if verification remains
incomplete. Never remove the runtime caveat solely to make a portfolio look more mature.

## Rebuild a clean source archive

```bash
python scripts/package_source.py --output /safe/location/graph-atlas-source.zip
```

The packager excludes build outputs, local data, secrets and Git internals and writes a SHA-256
file plus an internal source manifest. Inspect the contents before distribution. The source packager
does not compile the application or fetch dependencies.
