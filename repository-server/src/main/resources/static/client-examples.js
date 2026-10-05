/**
 * Generate examples, never execute them. User-created identifiers stay unchanged.
 * Only hosted repositories accept publication; proxy/group examples are read-only.
 */
const shell = value => `'${String(value).replaceAll("'", "'\"'\"'")}'`;
const xml = value => String(value).replace(/[&<>"']/g, character =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[character]));
const groovy = value => String(value).replaceAll('\\', '\\\\').replaceAll("'", "\\'");

export function clientExample(repository, username, t, language = 'en') {
  const url = repository.url.endsWith('/') ? repository.url : `${repository.url}/`;
  const parsed = new URL(url);
  const hosted = repository.type === 'hosted';
  const docs = `docs/${language === 'de' ? 'de' : 'en'}/clients.md`;
  const heading = `# ${t('credentialComment')}\n`;
  let code = '';
  switch (repository.format) {
    case 'npm':
      code = `${heading}npm config set registry ${shell(url)}
npm config set ${shell(`//${parsed.host}${parsed.pathname}:_authToken`)} "\${GR_TOKEN}"
${hosted ? '\nnpm publish\n' : ''}
npm install your-package`;
      break;
    case 'nuget':
      code = `${heading}dotnet nuget add source ${shell(`${url}v3/index.json`)} \\
  --name graph --username ${shell(username)} --password "$GR_TOKEN" \\
  --store-password-in-clear-text
${hosted ? `\ndotnet nuget push ./Your.Package.1.0.0.nupkg \\
  --source graph --api-key "$GR_TOKEN"\n` : ''}
dotnet restore --source ${shell(`${url}v3/index.json`)}
# ${docs}`;
      break;
    case 'pypi':
      code = `${heading}${hosted ? `export TWINE_USERNAME=${shell(username)}
export TWINE_PASSWORD="$GR_TOKEN"
python -m twine upload --repository-url ${shell(url)} dist/*\n\n` : ''}# ${t('netrcComment')}
python -m pip install --index-url ${shell(`${url}simple/`)} your-package`;
      break;
    case 'maven':
      code = `<!-- ~/.m2/settings.xml -->
<settings>
  <servers><server>
    <id>graph</id>
    <username>${xml(username)}</username>
    <password>\${env.GR_TOKEN}</password>
  </server></servers>
</settings>
${hosted ? `
mvn deploy:deploy-file -DrepositoryId=graph \\
  -Durl=${shell(url)} \\
  -DgroupId=com.example -DartifactId=demo \\
  -Dversion=1.0.0 -Dpackaging=jar -Dfile=demo.jar
` : ''}
// Gradle build.gradle
repositories {
  maven {
    url = uri('${groovy(url)}')
    credentials {
      username = '${groovy(username)}'
      password = System.getenv('GR_TOKEN')
    }
  }
}`;
      break;
    case 'raw':
      code = `${heading}${hosted ? `curl --fail -H "Authorization: Bearer $GR_TOKEN" \\
  --upload-file ./app.zip ${shell(`${url}releases/app.zip`)}\n\n` : ''}curl --fail -H "Authorization: Bearer $GR_TOKEN" \\
  -o app.zip ${shell(`${url}releases/app.zip`)}`;
      break;
    case 'docker': {
      const image = `${parsed.host}/${repository.name}/my-image:latest`;
      code = `${heading}printf '%s' "$GR_TOKEN" | docker login ${shell(parsed.host)} \\
  --username ${shell(username)} --password-stdin

docker tag my-image:latest ${shell(image)}
docker push ${shell(image)}
docker pull ${shell(image)}`;
      break;
    }
    default:
      code = `# ${docs}`;
  }
  return { code, docs, hosted };
}
