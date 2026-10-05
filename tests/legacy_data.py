"""Create data with an actual 0.2.0 JAR and inspect it with the new transport-neutral core.
Usage: GR_LEGACY_JAR=/path/to/old.jar python tests/legacy_data.py
This does not start the Spring Boot server and is not an end-to-end migration certification.
"""
from __future__ import annotations
import os, subprocess, tempfile, base64
from pathlib import Path
import requests
old = Path(os.environ.get('GR_LEGACY_JAR', 'missing-old-server.jar')).resolve()
if not old.is_file(): raise SystemExit('Set GR_LEGACY_JAR to the actual old 0.2.0 server JAR.')
os.environ['GR_TEST_JAR'] = str(old)
from support import ROOT, Server, PASSWORD

def run() -> None:
    output = ROOT/'.build/legacy-checks'
    output.mkdir(parents=True, exist_ok=True)
    sources = list((ROOT/'repository-core/src/main/java').rglob('*.java'))
    sources.append(ROOT/'repository-core/src/test/java/ir/graph/repo/core/LegacyDataCheckMain.java')
    argfile = output/'sources.txt'
    argfile.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources))
    jdk = Path(os.environ['JAVA_HOME'])/'bin' if os.getenv('JAVA_HOME') else None
    javac = str(jdk/'javac') if jdk else 'javac'
    java = str(jdk/'java') if jdk else 'java'
    subprocess.run([javac,'--release','21','-encoding','UTF-8','-d',str(output),'@'+str(argfile)], check=True)
    with tempfile.TemporaryDirectory(prefix='graph-legacy-migration-') as directory:
        home = Path(directory)/'data'
        server = Server(home)
        try:
            server.start(); auth = ('admin',PASSWORD)
            def call(method,path,**kw):
                response = requests.request(method,server.url+path,auth=auth,timeout=45,**kw)
                response.raise_for_status(); return response
            token = call('POST','/api/tokens',json={'label':'legacy-test'}).json()['token']
            call('PUT','/repository/raw-hosted/legacy/migration.bin',data=b'published-by-0.2.0')
            call('POST','/api/users',json={'username':'legacy-reader','password':'legacy-reader-test-947525','grants':{'raw-hosted':['read']}})
            archive = (ROOT/'repository-core/src/test/resources/fixtures/graph-demo.tgz').read_bytes()
            call('PUT','/repository/npm-hosted/legacy-demo',json={'name':'legacy-demo','versions':{'1.0.0':{'name':'legacy-demo','version':'1.0.0','dist':{'tarball':'https://unused.invalid/demo.tgz'}}},'dist-tags':{'latest':'1.0.0'},'_attachments':{'demo.tgz':{'data':base64.b64encode(archive).decode()}}})
            call('POST','/api/maintenance',json={'action':'compact','confirm':'MAINTENANCE'})
        finally: server.stop()
        result = subprocess.run([java,'-cp',str(output),'ir.graph.repo.core.LegacyDataCheckMain',str(home)],
            env={**os.environ,'LEGACY_TEST_PASSWORD':PASSWORD,'LEGACY_TEST_TOKEN':token},text=True,capture_output=True,check=True)
        print(result.stdout,end='')
        (ROOT/'docs/qa/legacy-data-compatibility.txt').write_text(result.stdout)

if __name__ == '__main__': run()
