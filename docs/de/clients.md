[English](../en/clients.md) · [Deutsch](../de/clients.md) · [Documentation](index.md)

# Beispiele für Paketwerkzeuge

Vor der Nutzung wird das Repository in der Oberfläche angelegt. Die Seite **Client-Einrichtung**
erzeugt passende Befehle und bietet für Proxy- und Gruppen-Repositories keine Veröffentlichungs-
befehle an. Verwenden Sie ein separates Konto mit minimalen Repository-Rechten und einem
widerrufbaren Token.

Diese Rezepte belegen nicht, dass jeder Befehl gegen diese Spring-Boot-Auslieferung ausgeführt
wurde. Den Prüfstand beschreibt [Tests](testing.md). Beispieladressen, Repository-Namen,
Paketkoordinaten und Dateipfade müssen angepasst werden.

## Maven und Gradle

Ein Maven-Repository ist zugleich der Maven-Endpunkt für Gradle:

```text
https://repo.example.com/repository/maven-releases/
```

`<server><id>` und `repositoryId` müssen übereinstimmen. Geheimnisse bleiben außerhalb des Projekts:

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

Die Repository-Richtlinie muss zu Releases beziehungsweise Snapshots passen. Für das Veröffentlichen
mit Gradle ist zusätzlich die übliche `maven-publish`-Konfiguration erforderlich; das Lesebeispiel
oben richtet keine Veröffentlichung ein.

## npm

```bash
npm config set registry https://repo.example.com/repository/npm-hosted/
npm config set '//repo.example.com/repository/npm-hosted/:_authToken' "${GR_TOKEN}"
npm publish
npm install your-package
```

Der Authentifizierungspfad muss zum Registry-Pfad passen. npm kann das expandierte Token dauerhaft
speichern. Nutzen Sie ein isoliertes CI-Benutzerverzeichnis oder eine geschützte Konfiguration und
entfernen Sie diese nach dem Job. Eine befüllte `.npmrc` darf nicht eingecheckt werden.

## Python / pip / Twine

```bash
export TWINE_USERNAME=build-agent
export TWINE_PASSWORD="$GR_TOKEN"
python -m twine upload \
  --repository-url https://repo.example.com/repository/pypi-hosted/ dist/*
python -m pip install \
  --index-url https://repo.example.com/repository/pypi-public/simple/ your-package
```

Twine muss separat installiert werden. Für geschützte pip-Downloads dient eine geschützte
`.netrc` oder ein Credential-Provider für `repo.example.com`; Tokens gehören nicht in die Index-URL.
Unter Unix gilt beispielsweise `chmod 600 ~/.netrc`. Legen Sie bewusst fest, welche Pakete aus
welchem Upstream kommen dürfen. Ein uneingeschränkter zusätzlicher Index kann diese Vorgabe unterlaufen.

## NuGet

```bash
dotnet nuget add source \
  https://repo.example.com/repository/nuget-hosted/v3/index.json \
  --name graph --username build-agent --password "$GR_TOKEN" \
  --store-password-in-clear-text
dotnet nuget push ./Your.Package.1.0.0.nupkg --source graph --api-key "$GR_TOKEN"
dotnet restore --source https://repo.example.com/repository/nuget-hosted/v3/index.json
```

**Dieses Beispiel speichert unverschlüsselte Zugangsdaten in der lokalen NuGet-Konfiguration.**
Es ist nur für kontrollierte, kurzlebige Umgebungen mit restriktiven Dateirechten gedacht.
Bevorzugen Sie einen passenden Credential-Provider oder eine geschützte CI-Konfiguration.
Die resultierende `NuGet.Config` darf nicht veröffentlicht werden. Vollständige NuGet.org-Parität,
alle Anmeldeverfahren und sämtliche Client-Versionen werden nicht zugesichert.

## Raw

```bash
curl --fail -H "Authorization: Bearer $GR_TOKEN" \
  --upload-file ./app.zip \
  https://repo.example.com/repository/raw-releases/releases/app.zip
curl --fail -H "Authorization: Bearer $GR_TOKEN" \
  -o app.zip https://repo.example.com/repository/raw-releases/releases/app.zip
```

## Docker / OCI

Nur Hosted-Docker/OCI ist implementiert. Das erste Segment des Image-Pfads bezeichnet das
konfigurierte Repository, hier beispielsweise `containers`:

```bash
printf '%s' "$GR_TOKEN" | docker login repo.example.com \
  --username build-agent --password-stdin
docker tag my-image:latest repo.example.com/containers/my-image:latest
docker push repo.example.com/containers/my-image:latest
docker pull repo.example.com/containers/my-image:latest
docker logout repo.example.com
```

Ein gültiges HTTPS-Zertifikat ist erforderlich. Eine „insecure registry“ ist keine empfohlene
Standardkonfiguration. Ein Schema- oder API-Test ersetzt keinen echten Docker-Client-Test;
vor dem Einsatz ist die Client-Abnahme durchzuführen.
