[English](../en/clients.md) · [Deutsch](../de/clients.md) · [Documentation](index.md)

# Package client examples

Create a repository in the console before using it. The **Client setup** page generates commands
for that repository and does not offer publication commands for proxy or group repositories.
Use a dedicated user with only the required repository grants and a revocable token.

These are connection recipes, not evidence that every command was executed against this Spring
Boot delivery. Current test boundaries are in [Testing](testing.md). Replace example hostnames,
repository names, coordinates and file paths.

## Maven and Gradle

A Maven-format repository is also the Gradle Maven repository endpoint:

```text
https://repo.example.com/repository/maven-releases/
```

Use matching `<server><id>` and `repositoryId` values. Keep secrets outside your project:

```xml
<!-- ~/.m2/settings.xml -->
<settings>
  <servers>
    <server>
      <id>graph</id>
      <username>build-agent</username>
      <password>${env.GR_TOKEN}</password>
    </server>
  </servers>
</settings>
```

```bash
mvn deploy:deploy-file -DrepositoryId=graph \
  -Durl=https://repo.example.com/repository/maven-releases/ \
  -DgroupId=com.example -DartifactId=demo -Dversion=1.0.0 \
  -Dpackaging=jar -Dfile=demo.jar
```

```groovy
repositories {
  maven {
    url = uri('https://repo.example.com/repository/maven-public/')
    credentials {
      username = 'build-agent'
      password = System.getenv('GR_TOKEN')
    }
  }
}
```

Use the appropriate release/snapshot repository policy. Gradle publication also needs its normal
`maven-publish` configuration; the read example above does not configure publication.

## npm

```bash
npm config set registry https://repo.example.com/repository/npm-hosted/
npm config set '//repo.example.com/repository/npm-hosted/:_authToken' "${GR_TOKEN}"
npm publish
npm install your-package
```

The authentication scope must match the selected registry path. npm configuration may persist
the expanded token; use an isolated CI home or a protected configuration file and remove it after
the job. Do not commit a populated `.npmrc`.

## Python / pip / Twine

```bash
export TWINE_USERNAME=build-agent
export TWINE_PASSWORD="$GR_TOKEN"
python -m twine upload \
  --repository-url https://repo.example.com/repository/pypi-hosted/ dist/*
python -m pip install \
  --index-url https://repo.example.com/repository/pypi-public/simple/ your-package
```

Twine needs to be installed separately. For authenticated pip reads, configure a protected
`.netrc`/credential provider for `repo.example.com`; do not place the token in the index URL.
Use `chmod 600 ~/.netrc` on Unix. Decide explicitly which packages may come from which upstream;
adding an unrestricted extra index can undermine that policy.

## NuGet

```bash
dotnet nuget add source \
  https://repo.example.com/repository/nuget-hosted/v3/index.json \
  --name graph --username build-agent --password "$GR_TOKEN" \
  --store-password-in-clear-text
dotnet nuget push ./Your.Package.1.0.0.nupkg --source graph --api-key "$GR_TOKEN"
dotnet restore --source https://repo.example.com/repository/nuget-hosted/v3/index.json
```

**The example persists an unencrypted credential in the local NuGet configuration.** Use it only
in a controlled, disposable environment with restrictive file permissions. Prefer your platform's
credential provider or protected CI configuration. Do not commit the resulting `NuGet.Config`.
NuGet.org parity, every authentication flow and every client version are not claimed.

## Raw

```bash
curl --fail -H "Authorization: Bearer $GR_TOKEN" \
  --upload-file ./app.zip \
  https://repo.example.com/repository/raw-releases/releases/app.zip
curl --fail -H "Authorization: Bearer $GR_TOKEN" \
  -o app.zip https://repo.example.com/repository/raw-releases/releases/app.zip
```

## Docker / OCI

Only hosted Docker/OCI is implemented. The first image-path segment selects the configured
repository, for example a hosted repository named `containers`:

```bash
printf '%s' "$GR_TOKEN" | docker login repo.example.com \
  --username build-agent --password-stdin
docker tag my-image:latest repo.example.com/containers/my-image:latest
docker push repo.example.com/containers/my-image:latest
docker pull repo.example.com/containers/my-image:latest
docker logout repo.example.com
```

Configure a valid HTTPS certificate. Do not use an “insecure registry” as the default deployment.
A schema or API-level test is not a real Docker-client test; complete client acceptance before use.
