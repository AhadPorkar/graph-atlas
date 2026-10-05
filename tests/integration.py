#!/usr/bin/env python3
"""Black-box integration tests against the real Spring Boot server, with local upstream fixtures.
Real npm and pip clients are used when present. No Nexus service is involved.
"""
from __future__ import annotations
import base64, concurrent.futures, gzip, hashlib, io, json, os, shutil, subprocess, sys
import tarfile, tempfile, threading, time, zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlparse, parse_qs
import requests
from support import ROOT, Server, PASSWORD

CHECKS=[]
SKIPPED=[]

def check(value, name):
    if not value: raise AssertionError(name)
    CHECKS.append(name)
    print('PASS',name,flush=True)

def wheel(name='graph_demo',version='1.0.0'):
    files={f'{name}/__init__.py':b'VALUE = "installed-from-graph"\n',
        f'{name}-{version}.dist-info/METADATA':f'Metadata-Version: 2.1\nName: {name.replace("_","-")}\nVersion: {version}\nSummary: Integration fixture\n\n'.encode(),
        f'{name}-{version}.dist-info/WHEEL':b'Wheel-Version: 1.0\nGenerator: GraphRepositoryTests\nRoot-Is-Purelib: true\nTag: py3-none-any\n'}
    record=''.join(f'{p},sha256={base64.urlsafe_b64encode(hashlib.sha256(b).digest()).decode().rstrip("=")},{len(b)}\n' for p,b in files.items())
    record+=f'{name}-{version}.dist-info/RECORD,,\n'
    files[f'{name}-{version}.dist-info/RECORD']=record.encode()
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for p,b in files.items(): z.writestr(p,b)
    return output.getvalue()

def nupkg(name='Graph.Demo',version='1.0.0',dependency=False,unsafe=False):
    output=io.BytesIO()
    spec=(f'<?xml version="1.0"?>'+ ('<!DOCTYPE x [<!ENTITY leak SYSTEM "file:///etc/passwd">]>' if unsafe else '')+
        f'<package xmlns="http://schemas.microsoft.com/packaging/2013/05/nuspec.xsd"><metadata><id>{name}</id><version>{version}</version><authors>Graph</authors><description>'+
        ('&leak;' if unsafe else 'Test package')+'</description>'+ ('<dependencies><group targetFramework="net8.0"><dependency id="Graph.Dependency" version="[1.0.0, 2.0.0)"/></group></dependencies>' if dependency else '')+'</metadata></package>')
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr(name+'.nuspec',spec);z.writestr('lib/net8.0/_._','')
    return output.getvalue()

def tarball(name,version):
    output=io.BytesIO()
    with tarfile.open(fileobj=output,mode='w:gz') as tar:
        files={'package/package.json':json.dumps({'name':name,'version':version,'main':'index.js'}).encode(),'package/index.js':b'module.exports = "proxy-fixture";\n'}
        for path,data in files.items():
            info=tarfile.TarInfo(path);info.size=len(data);tar.addfile(info,io.BytesIO(data))
    return output.getvalue()

class Fixture(BaseHTTPRequestHandler):
    hits=[]
    failing=False
    url=''
    npm_bytes=tarball('graph-demo','9.0.0')
    python_bytes=wheel('graph_remote')
    nuget_bytes=nupkg('Graph.Remote','2.0.0')
    def log_message(self,*args): pass
    def do_GET(self):
        path=urlparse(self.path).path
        type(self).hits.append((path,self.headers.get('Authorization')))
        if type(self).failing:
            self.send_response(503);self.end_headers();return
        if path=='/raw/redirect.txt':
            self.send_response(302);self.send_header('Location',type(self).url.replace('127.0.0.1','localhost')+'/landing.txt');self.end_headers();return
        if path in ['/raw/hello.txt','/raw/redirect.txt','/landing.txt']:
            return self.respond(b'upstream-raw-content','application/octet-stream')
        if path.startswith('/maven/'):
            return self.respond(b'maven-proxy-content','application/octet-stream')
        if path=='/npm/graph-demo':
            return self.respond({'_id':'graph-demo','name':'graph-demo','dist-tags':{'latest':'9.0.0'},'versions':{'9.0.0':{'name':'graph-demo','version':'9.0.0','dist':{'tarball':self.url+'/npm/graph-demo/-/graph-demo-9.0.0.tgz','shasum':hashlib.sha1(self.npm_bytes).hexdigest()}}}})
        if path=='/npm/graph-demo/-/graph-demo-9.0.0.tgz': return self.respond(self.npm_bytes,'application/gzip')
        if path in ['/simple/graph-remote/','/htmlsimple/graph-remote/']:
            sha=hashlib.sha256(self.python_bytes).hexdigest();url=self.url+'/pythonfiles/graph_remote-1.0.0-py3-none-any.whl'
            if path.startswith('/html'):
                return self.respond(f'<html><body><a href="{url}#sha256={sha}" data-requires-python=">=3.8">graph_remote-1.0.0-py3-none-any.whl</a></body></html>'.encode(),'text/html')
            return self.respond({'meta':{'api-version':'1.0'},'name':'graph-remote','files':[{'filename':'graph_remote-1.0.0-py3-none-any.whl','url':url,'hashes':{'sha256':sha},'requires-python':'>=3.8','yanked':False}]},'application/vnd.pypi.simple.v1+json')
        if path=='/pythonfiles/graph_remote-1.0.0-py3-none-any.whl': return self.respond(self.python_bytes,'application/zip')
        if path=='/nuget/index.json':
            return self.respond({'version':'3.0.0','resources':[{'@id':self.url+'/nuget/flat/','@type':'PackageBaseAddress/3.0.0'},{'@id':self.url+'/nuget/reg/','@type':'RegistrationsBaseUrl/3.6.0'},{'@id':self.url+'/nuget/search','@type':'SearchQueryService/3.5.0'}]})
        if path=='/nuget/flat/graph.remote/index.json':return self.respond({'versions':['2.0.0']})
        if path=='/nuget/flat/graph.remote/2.0.0/graph.remote.2.0.0.nupkg':return self.respond(self.nuget_bytes,'application/octet-stream')
        if path=='/nuget/reg/graph.remote/index.json':
            return self.respond({'items':[{'items':[{'catalogEntry':{'id':'Graph.Remote','version':'2.0.0','description':'Proxy NuGet','authors':'Graph','listed':True,'dependencyGroups':[]}}]}]})
        if path=='/nuget/search':return self.respond({'totalHits':1,'data':[{'id':'Graph.Remote','version':'2.0.0'}]})
        self.send_response(404);self.end_headers()
    def respond(self,data,typ='application/json'):
        if isinstance(data,dict):data=json.dumps(data).encode()
        self.send_response(200);self.send_header('Content-Type',typ);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)

class Client:
    def __init__(self, server):
        self.server=server;self.session=requests.Session();self.token=None
    def call(self,method,path,expected=200,**kwargs):
        headers=kwargs.pop('headers',{})
        if self.token and 'Authorization' not in headers: headers['Authorization']='Bearer '+self.token
        response=self.session.request(method,self.server.url+path,headers=headers,timeout=30,**kwargs)
        if response.status_code!=expected:
            raise AssertionError(f'{method} {path}: expected {expected}, got {response.status_code}: {response.text[:700]}')
        return response
    def repo(self,name,fmt,typ='hosted',**kwargs):
        return self.call('POST','/api/repos',201,json={'name':name,'format':fmt,'type':typ,**kwargs}).json()
    def token_for(self,username,password):
        return requests.post(self.server.url+'/api/tokens',auth=(username,password),json={'label':'test-only'},timeout=30).json()['token']


def run():
    fixture=ThreadingHTTPServer(('127.0.0.1',0),Fixture);Fixture.url='http://127.0.0.1:'+str(fixture.server_port)
    thread=threading.Thread(target=fixture.serve_forever,daemon=True);thread.start()
    with tempfile.TemporaryDirectory(prefix='graph-repository-integration-') as temp:
        tmp=Path(temp);server=Server(tmp/'data',GR_ALLOW_PRIVATE_UPSTREAM='true',GR_ALLOW_HTTP_UPSTREAM='true',GR_TEST_UPSTREAM_AUTH='Basic dXBzdHJlYW06c2VjcmV0')
        try:
            server.start();c=Client(server)
            check(c.call('GET','/healthz').json()['engine']=='spring-boot','Spring Boot server starts independently of Nexus')
            c.call('GET','/api/stats',401);check(True,'Administrative API requires authentication')
            login=requests.post(server.url+'/api/login',json={'username':'admin','password':PASSWORD},timeout=30)
            check(login.status_code==403,'Browser login rejects missing Origin')
            browser=requests.Session();login=browser.post(server.url+'/api/login',headers={'Origin':server.url},json={'username':'admin','password':PASSWORD},timeout=30)
            check(login.status_code==200,'Browser login with valid origin succeeds')
            cookie=login.headers.get('Set-Cookie','');check('HttpOnly' in cookie and 'SameSite=Strict' in cookie and 'Path=/api' in cookie,'Session cookie is HttpOnly, same-site and API-scoped')
            csrf=login.json()['csrf']
            denied=browser.post(server.url+'/api/repos',headers={'Origin':server.url},json={'name':'csrf-denied','format':'raw'},timeout=30)
            check(denied.status_code==403,'Cookie-authenticated writes require CSRF token')
            denied=browser.post(server.url+'/api/repos',headers={'Origin':'https://attacker.invalid','X-CSRF-Token':csrf},json={'name':'csrf-denied','format':'raw'},timeout=30)
            check(denied.status_code==403,'Cross-origin write rejected even with CSRF token')
            created=browser.post(server.url+'/api/repos',headers={'Origin':server.url,'X-CSRF-Token':csrf},json={'name':'browser-created','format':'raw'},timeout=30)
            check(created.status_code==201,'Cookie-authenticated write with CSRF succeeds')
            c.token=c.token_for('admin',PASSWORD)
            check(c.token.startswith('gr_'),'API token is issued by the local Java engine')
            c.call('POST','/api/repos',415,data='{}',headers={'Content-Type':'text/plain'});check(True,'Administrative JSON endpoints reject incorrect Content-Type')
            stats=c.call('GET','/api/stats').json();check(stats['licenseQuotas'] is False,'No licensing quotas reported by the engine')
            c.call('PUT','/api/users/admin',409,json={'admin':True,'disabled':True});check(True,'Last administrator cannot be disabled')
            c.call('DELETE','/api/users/admin',409,json={'confirm':'admin'});check(True,'Last administrator cannot be deleted')
            c.call('POST','/api/users',201,json={'username':'reader','password':'reader-password-123','grants':{'raw-hosted':['read']}})
            reader=c.token_for('reader','reader-password-123')
            c.call('GET','/api/users',403,headers={'Authorization':'Bearer '+reader});check(True,'Repository reader cannot manage accounts')
            c.call('PUT','/repository/raw-hosted/forbidden.txt',403,headers={'Authorization':'Bearer '+reader},data=b'no');check(True,'Read-only account cannot publish artifacts')
            content=b'hello graph repository\x00\xff'
            uploaded=c.call('PUT','/repository/raw-hosted/releases/demo.bin',201,data=content).json()
            check(uploaded['sha256']==hashlib.sha256(content).hexdigest(),'Raw publication computes the actual SHA-256')
            download=c.call('GET','/repository/raw-hosted/releases/demo.bin',headers={'Authorization':'Bearer '+reader})
            check(download.content==content,'Raw bytes round-trip through authenticated storage')
            check('sandbox' in download.headers['Content-Security-Policy'],'Untrusted artifact responses are sandboxed')
            c.call('PUT','/repository/raw-hosted/releases/demo.bin',409,data=b'different');check(True,'Immutable hosted artifact cannot be silently overwritten')
            check(c.call('GET','/repository/raw-hosted/releases/demo.bin',206,headers={'Range':'bytes=2-6'}).content==content[2:7],'Byte-range GET returns the requested range')
            check(c.call('GET','/repository/raw-hosted/releases/demo.bin',206,headers={'Range':'bytes=-3'}).content==content[-3:],'Suffix byte-range GET works')
            check(int(c.call('HEAD','/repository/raw-hosted/releases/demo.bin').headers['Content-Length'])==len(content),'HEAD returns the actual content length')
            c.call('GET','/repository/raw-hosted/releases/demo.bin',304,headers={'If-None-Match':download.headers['ETag']});check(True,'ETag conditional GET returns 304')
            c.call('GET','/repository/raw-hosted/releases/demo.bin',416,headers={'Range':'bytes=9999-'});check(True,'Invalid byte range returns 416')
            c.call('PUT','/repository/raw-hosted/empty.bin',201,data=b'');check(c.call('GET','/repository/raw-hosted/empty.bin').content==b'','Zero-byte artifact is supported')
            for malicious in ['%2e%2e%2fsecret','a/%252e%252e/secret','a//b']:
                c.call('GET','/repository/raw-hosted/'+malicious,400)
            check(True,'Path traversal and double-encoding attempts are rejected')
            with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
                results=list(pool.map(lambda i:requests.put(server.url+'/repository/raw-hosted/concurrent.bin',headers={'Authorization':'Bearer '+c.token},data=('writer-'+str(i)).encode(),timeout=30),range(12)))
            check(sum(r.status_code==201 for r in results)==1 and all(r.status_code in (201,409) for r in results),'Concurrent immutable publication has one winner and no partial overwrite')
            c.repo('raw-public','raw',anonymous=True)
            c.call('PUT','/repository/raw-public/public.txt',201,data=b'public')
            check(requests.get(server.url+'/repository/raw-public/public.txt',timeout=10).content==b'public','Explicit anonymous read repository works')
            check(requests.get(server.url+'/repository/raw-public/public.txt',headers={'Authorization':'Bearer invalid'},timeout=10).status_code==401,'Invalid credentials do not silently fall back to anonymous access')
            revoked=c.token_for('reader','reader-password-123');tid=hashlib.sha256(revoked.encode()).hexdigest();c.call('DELETE','/api/tokens/'+tid)
            c.call('GET','/api/session',401,headers={'Authorization':'Bearer '+revoked});check(True,'Revoked API token is rejected immediately')
            c.call('PUT','/api/users/reader',json={'disabled':True,'admin':False,'grants':{'raw-hosted':['read']}})
            c.call('GET','/api/session',401,headers={'Authorization':'Bearer '+reader});check(True,'Disabling a user invalidates their API-token access')
            c.call('DELETE','/api/users/reader',json={'confirm':'reader'})
            c.call('POST','/api/users',201,json={'username':'reader','password':'reader-password-456','grants':{}})
            c.call('GET','/api/session',401,headers={'Authorization':'Bearer '+reader});check(True,'Recreating a username does not resurrect deleted-account tokens')
            maven='com/example/demo/1.0.0/demo-1.0.0.jar';c.call('PUT','/repository/maven-releases/'+maven,201,data=b'jar-bytes')
            c.call('PUT','/repository/maven-releases/com/example/demo/1.0.0/demo-1.0.0.pom',201,data=b'<project><modelVersion>4.0.0</modelVersion></project>')
            check(c.call('GET','/repository/maven-releases/'+maven+'.sha1').text==hashlib.sha1(b'jar-bytes').hexdigest(),'Maven layout serves generated SHA-1 checksums')
            check(c.call('GET','/repository/maven-releases/'+maven+'.sha256').text==hashlib.sha256(b'jar-bytes').hexdigest(),'Maven layout serves generated SHA-256 checksums')
            c.call('PUT','/repository/maven-releases/com/example/demo/1.0-SNAPSHOT/demo.jar',400,data=b'no');check(True,'Release-only Maven repository rejects snapshot path')
            c.call('PUT','/repository/maven-snapshots/com/example/demo/1.0.0/demo.jar',400,data=b'no');check(True,'Snapshot-only Maven repository rejects release path')
            for value in [b'<metadata>first</metadata>',b'<metadata>second</metadata>']:
                c.call('PUT','/repository/maven-releases/com/example/demo/maven-metadata.xml',201,data=value)
            check(c.call('GET','/repository/maven-releases/com/example/demo/maven-metadata.xml').content.endswith(b'second</metadata>'),'Maven repository metadata can be updated independently of immutable artifacts')
            c.repo('maven-group','maven','group',members=['maven-releases','maven-snapshots'])
            check(c.call('GET','/repository/maven-group/'+maven).content==b'jar-bytes','Maven group resolves member content')
            group=c.call('GET','/api/repos/maven-group').json();group['members']=['maven-group'];c.call('PUT','/api/repos/maven-group',400,json=group);check(True,'Repository group cycles are rejected')
            c.call('DELETE','/api/repos/maven-releases',409,json={'confirm':'maven-releases','purge':True});check(True,'Referenced group member cannot be deleted')
            # Real npm CLI: package and scoped package publication, install and dist-tags.
            if shutil.which('npm'):
                npm_dir=tmp/'npm-package';npm_dir.mkdir();npmrc=tmp/'test.npmrc';registry=server.url+'/repository/npm-hosted/'
                npmrc.write_text(f'//127.0.0.1:{server.port}/repository/npm-hosted/:_authToken={c.token}\n');npmrc.chmod(0o600)
                env={**os.environ,'npm_config_userconfig':str(npmrc),'npm_config_cache':str(tmp/'npm-cache'),'npm_config_audit':'false','npm_config_fund':'false','npm_config_update_notifier':'false'}
                def npm(*args,cwd=npm_dir):
                    proc=subprocess.run(['npm',*args],cwd=cwd,env=env,capture_output=True,text=True,timeout=90)
                    if proc.returncode:raise AssertionError(('npm '+str(args)+'\n'+proc.stdout+'\n'+proc.stderr).replace(c.token,'[REDACTED]'))
                    return proc
                (npm_dir/'index.js').write_text('module.exports = "installed-from-graph";\n')
                for name,version in [('graph-demo','1.0.0'),('graph-demo','1.1.0'),('@graph/scoped-demo','1.0.0')]:
                    (npm_dir/'package.json').write_text(json.dumps({'name':name,'version':version,'main':'index.js','description':'Local integration fixture'}));npm('publish','--registry='+registry,'--ignore-scripts')
                check(True,'Real npm CLI publishes unscoped versions and a scoped package')
                install=tmp/'npm-install';install.mkdir();(install/'package.json').write_text('{"name":"consumer","version":"1.0.0"}')
                npm('install','graph-demo@1.0.0','@graph/scoped-demo@1.0.0','--registry='+registry,'--ignore-scripts','--audit=false','--fund=false',cwd=install)
                resolved=subprocess.check_output(['node','-e','console.log(require("graph-demo")+" / "+require("@graph/scoped-demo"))'],cwd=install,text=True).strip()
                check(resolved=='installed-from-graph / installed-from-graph','Real npm CLI installs and Node resolves hosted and scoped packages')
                npm('dist-tag','add','graph-demo@1.0.0','stable','--registry='+registry)
                check(c.call('GET','/repository/npm-hosted/-/package/graph-demo/dist-tags').json()['stable']=='1.0.0','Real npm CLI updates distribution tags')
                check(c.call('GET','/repository/npm-hosted/-/v1/search?text=graph').json()['total']==2,'npm local package search finds both published package names')
            else: SKIPPED.append('npm CLI unavailable')
            # PyPI: multipart publication and actual pip installation.
            wheel_bytes=wheel();filename='graph_demo-1.0.0-py3-none-any.whl'
            c.call('POST','/repository/pypi-hosted/',data={':action':'file_upload','name':'graph-demo','version':'1.0.0','sha256_digest':hashlib.sha256(wheel_bytes).hexdigest(),'requires_python':'>=3.8'},files={'content':(filename,wheel_bytes,'application/octet-stream')})
            project=c.call('GET','/repository/pypi-hosted/simple/Graph_Demo/',headers={'Accept':'application/vnd.pypi.simple.v1+json'}).json()
            check(project['name']=='graph-demo' and project['files'][0]['hashes']['sha256']==hashlib.sha256(wheel_bytes).hexdigest(),'PyPI upload creates a normalized JSON Simple index with hashes')
            html=c.call('GET','/repository/pypi-hosted/simple/graph-demo/').text
            check('data-requires-python=' in html and '#sha256=' in html,'PyPI HTML Simple index advertises Python requirement and digest')
            c.call('POST','/repository/pypi-hosted/',400,data={'name':'bad-checksum','version':'1.0.0','sha256_digest':'0'*64},files={'content':(filename,wheel_bytes)})
            check(True,'Python upload checksum mismatch is rejected')
            netrc=tmp/'netrc';netrc.write_text(f'machine 127.0.0.1 login admin password {c.token}\n');netrc.chmod(0o600)
            env={**os.environ,'NETRC':str(netrc),'PIP_CONFIG_FILE':os.devnull,'PIP_DISABLE_PIP_VERSION_CHECK':'1','PIP_EXTRA_INDEX_URL':''}
            proc=subprocess.run([sys.executable,'-m','pip','install','--target',str(tmp/'pip-target'),'--no-deps','--no-cache-dir','--disable-pip-version-check','--trusted-host','127.0.0.1','--index-url',server.url+'/repository/pypi-hosted/simple/','graph-demo==1.0.0'],env=env,text=True,capture_output=True,timeout=90)
            if proc.returncode:raise AssertionError((proc.stdout+proc.stderr).replace(c.token,'[REDACTED]'))
            installed=subprocess.check_output([sys.executable,'-c','import graph_demo;print(graph_demo.VALUE)'],env={**os.environ,'PYTHONPATH':str(tmp/'pip-target')},text=True).strip()
            check(installed=='installed-from-graph','Real pip CLI installs the wheel and Python imports it')
            asset_path='files/graph-demo/'+filename;c.call('POST','/api/pypi/yank',json={'repo':'pypi-hosted','path':asset_path,'yanked':'test reason'})
            check('data-yanked="test reason"' in c.call('GET','/repository/pypi-hosted/simple/graph-demo/').text,'Python distributions can be yanked without deleting their bytes')
            c.call('POST','/api/pypi/yank',json={'repo':'pypi-hosted','path':asset_path,'yanked':False})
            # NuGet wire-protocol publication, restoration resources, dependencies and unlisting.
            package=nupkg(dependency=True);c.call('PUT','/repository/nuget-hosted/v2/package',201,files={'package':('Graph.Demo.1.0.0.nupkg',package,'application/octet-stream')})
            index=c.call('GET','/repository/nuget-hosted/v3/index.json').json();check(len(index['resources'])==5,'NuGet V3 service index advertises implemented resources')
            check(c.call('GET','/repository/nuget-hosted/v3/flatcontainer/graph.demo/index.json').json()['versions']==['1.0.0'],'NuGet flat container lists published versions')
            check(c.call('GET','/repository/nuget-hosted/v3/flatcontainer/graph.demo/1.0.0/graph.demo.1.0.0.nupkg').content==package,'NuGet flat container downloads exact package bytes')
            check('<id>Graph.Demo</id>' in c.call('GET','/repository/nuget-hosted/v3/flatcontainer/graph.demo/1.0.0/graph.demo.nuspec').text,'NuGet nuspec endpoint serves package metadata')
            reg=c.call('GET','/repository/nuget-hosted/v3/registration/graph.demo/index.json').json();catalog=reg['items'][0]['items'][0]['catalogEntry']
            check(catalog['dependencyGroups'][0]['dependencies'][0]['id']=='Graph.Dependency','NuGet registration preserves target framework dependency groups')
            check(c.call('GET','/repository/nuget-hosted/v3/search?q=graph').json()['totalHits']==1,'NuGet search finds listed packages')
            c.call('PUT','/repository/nuget-hosted/v2/package',409,files={'package':('same.nupkg',package)});check(True,'NuGet duplicate immutable version returns conflict')
            c.call('PUT','/repository/nuget-hosted/v2/package',400,files={'package':('unsafe.nupkg',nupkg(name='Unsafe',unsafe=True))});check(True,'Unsafe nuspec DOCTYPE/XXE is rejected')
            c.call('DELETE','/repository/nuget-hosted/v2/package/Graph.Demo/1.0.0')
            check(c.call('GET','/repository/nuget-hosted/v3/search?q=graph').json()['totalHits']==0,'NuGet unlisting removes packages from search')
            check(c.call('GET','/repository/nuget-hosted/v3/flatcontainer/graph.demo/1.0.0/graph.demo.1.0.0.nupkg').content==package,'NuGet unlisted package remains available for exact-version restore')
            # Hosted OCI: chunked/resumable push, digest verification, manifests, indexes and tags.
            check(c.call('GET','/v2/').headers['Docker-Distribution-Api-Version']=='registry/2.0','Docker Registry V2 discovery endpoint responds')
            image='docker-hosted/team/demo';root='/v2/'+image
            def blob_publish(body,chunked=False):
                started=c.call('POST',root+'/blobs/uploads/',202);location=urlparse(started.headers['Location']).path
                if chunked:
                    split=len(body)//2;c.call('PATCH',location,202,data=body[:split],headers={'Content-Range':f'0-{split-1}'})
                    c.call('PATCH',location,416,data=b'wrong',headers={'Content-Range':'0-4'})
                    check(c.call('GET',location,204).headers['Range']==f'0-{split-1}','OCI upload status preserves committed offset after rejected chunk')
                    suffix=body[split:]
                else:suffix=body
                digest='sha256:'+hashlib.sha256(body).hexdigest()
                result=c.call('PUT',location+'?digest='+digest,201,data=suffix)
                check(result.headers['Docker-Content-Digest']==digest,'OCI finalize verifies the SHA-256 digest')
                check(c.call('GET',root+'/blobs/'+digest).content==body,'OCI blob download preserves exact bytes')
                return {'mediaType':'application/vnd.oci.image.layer.v1.tar+gzip','digest':digest,'size':len(body)}
            config_body=b'{"architecture":"amd64","os":"linux","rootfs":{"type":"layers","diff_ids":[]}}'
            config_descriptor=blob_publish(config_body,True);config_descriptor['mediaType']='application/vnd.oci.image.config.v1+json'
            layer_descriptor=blob_publish(gzip.compress(b'layer-bytes',mtime=0))
            manifest=json.dumps({'schemaVersion':2,'mediaType':'application/vnd.oci.image.manifest.v1+json','config':config_descriptor,'layers':[layer_descriptor]},separators=(',',':')).encode()
            result=c.call('PUT',root+'/manifests/latest',201,data=manifest,headers={'Content-Type':'application/vnd.oci.image.manifest.v1+json'});manifest_digest=result.headers['Docker-Content-Digest']
            check(c.call('GET',root+'/manifests/latest').content==manifest,'OCI manifest is resolved by tag without reserializing bytes')
            check(c.call('HEAD',root+'/manifests/'+manifest_digest).headers['Docker-Content-Digest']==manifest_digest,'OCI manifest digest address supports HEAD')
            check(c.call('GET',root+'/tags/list').json()['tags']==['latest'],'OCI tag listing returns published tag')
            check(image in c.call('GET','/v2/_catalog').json()['repositories'],'Docker catalog includes an authorized hosted image')
            c.call('DELETE',root+'/blobs/'+config_descriptor['digest'],409);check(True,'Referenced OCI blobs cannot be deleted before their manifest')
            index_bytes=json.dumps({'schemaVersion':2,'mediaType':'application/vnd.oci.image.index.v1+json','manifests':[{'mediaType':'application/vnd.oci.image.manifest.v1+json','digest':manifest_digest,'size':len(manifest),'platform':{'architecture':'amd64','os':'linux'}}]},separators=(',',':')).encode()
            c.call('PUT',root+'/manifests/multi',201,data=index_bytes,headers={'Content-Type':'application/vnd.oci.image.index.v1+json'})
            check(c.call('GET',root+'/manifests/multi').content==index_bytes,'OCI multi-platform index can refer to an existing manifest')
            missing=dict(config_descriptor);missing['digest']='sha256:'+'0'*64
            bad_manifest=json.dumps({'schemaVersion':2,'config':missing,'layers':[]}).encode()
            c.call('PUT',root+'/manifests/broken',400,data=bad_manifest,headers={'Content-Type':'application/vnd.oci.image.manifest.v1+json'});check(True,'OCI manifest with missing referenced content is rejected')
            # Proxy and group repositories use an explicit local upstream fixture, not Nexus.
            c.repo('raw-proxy','raw','proxy',upstream=Fixture.url+'/raw/',cacheSeconds=3600)
            first=c.call('GET','/repository/raw-proxy/hello.txt').content;hits=len(Fixture.hits)
            check(c.call('GET','/repository/raw-proxy/hello.txt').content==first and len(Fixture.hits)==hits,'Raw proxy caches artifacts and avoids duplicate upstream fetch')
            check(all(auth is None for _,auth in Fixture.hits),'Client package credentials are not forwarded to upstreams')
            c.repo('auth-proxy','raw','proxy',upstream=Fixture.url+'/raw/',upstreamAuthEnv='GR_TEST_UPSTREAM_AUTH',allowedHosts=['localhost'])
            c.call('GET','/repository/auth-proxy/redirect.txt')
            check(Fixture.hits[-2][1]=='Basic dXBzdHJlYW06c2VjcmV0' and Fixture.hits[-1][1] is None,'Explicit upstream credentials are stripped on a cross-origin redirect')
            c.repo('maven-proxy','maven','proxy',upstream=Fixture.url+'/maven/')
            check(c.call('GET','/repository/maven-proxy/com/remote/demo/1.0/demo-1.0.jar').content==b'maven-proxy-content','Maven proxy retrieves and caches repository-layout content')
            c.repo('npm-proxy','npm','proxy',upstream=Fixture.url+'/npm/')
            pack=c.call('GET','/repository/npm-proxy/graph-demo').json();url=pack['versions']['9.0.0']['dist']['tarball']
            check(url.startswith(server.url+'/repository/npm-proxy/'),'npm proxy rewrites upstream tarball URLs to local storage routes')
            check(c.call('GET',urlparse(url).path).content==Fixture.npm_bytes,'npm proxy fetches tarball with metadata checksum verification')
            c.repo('npm-group','npm','group',members=['npm-hosted','npm-proxy'])
            versions=c.call('GET','/repository/npm-group/graph-demo').json()['versions']
            check('9.0.0' in versions and ('1.0.0' in versions if shutil.which('npm') else True),'npm group merges available hosted and proxy versions')
            c.call('POST','/api/users',201,json={'username':'group-only','password':'group-only-password','grants':{'npm-group':['read']}})
            group_token=c.token_for('group-only','group-only-password')
            c.call('GET','/repository/npm-group/graph-demo',404,headers={'Authorization':'Bearer '+group_token});check(True,'Group membership does not bypass member repository permissions')
            for name,path in [('pypi-proxy','/simple/'),('pypi-html-proxy','/htmlsimple/')]:
                c.repo(name,'pypi','proxy',upstream=Fixture.url+path)
                project=c.call('GET','/repository/'+name+'/simple/graph-remote/',headers={'Accept':'application/vnd.pypi.simple.v1+json'}).json();file=project['files'][0]
                check(file['url'].startswith(server.url+'/repository/'+name+'/files/'),'PyPI proxy rewrites file URL from '+('JSON' if name=='pypi-proxy' else 'HTML')+' index')
                check(c.call('GET',urlparse(file['url']).path).content==Fixture.python_bytes,'PyPI proxy validates and caches wheel from '+name)
            c.repo('pypi-group','pypi','group',members=['pypi-hosted','pypi-proxy'])
            check(c.call('GET','/repository/pypi-group/simple/graph-demo/').status_code==200 and c.call('GET','/repository/pypi-group/simple/graph-remote/').status_code==200,'PyPI group resolves local and upstream distributions')
            c.repo('nuget-proxy','nuget','proxy',upstream=Fixture.url+'/nuget/index.json')
            check(c.call('GET','/repository/nuget-proxy/v3/flatcontainer/graph.remote/index.json').json()['versions']==['2.0.0'],'NuGet proxy discovers upstream V3 resource endpoints')
            check(c.call('GET','/repository/nuget-proxy/v3/flatcontainer/graph.remote/2.0.0/graph.remote.2.0.0.nupkg').content==Fixture.nuget_bytes,'NuGet proxy caches package binary')
            remote_reg=c.call('GET','/repository/nuget-proxy/v3/registration/graph.remote/index.json').json()
            check(remote_reg['items'][0]['items'][0]['packageContent'].startswith(server.url),'NuGet proxy rewrites registration packageContent to local endpoint')
            c.repo('nuget-group','nuget','group',members=['nuget-hosted','nuget-proxy'])
            check(c.call('GET','/repository/nuget-group/v3/flatcontainer/graph.remote/index.json').json()['versions']==['2.0.0'],'NuGet group resolves proxy versions')
            rp=c.call('GET','/api/repos/raw-proxy').json();rp['offline']=True;c.call('PUT','/api/repos/raw-proxy',json=rp);Fixture.failing=True
            check(c.call('GET','/repository/raw-proxy/hello.txt').content==b'upstream-raw-content','Offline proxy serves previously cached artifacts without contacting the upstream')
            c.call('GET','/repository/raw-proxy/not-cached.bin',404);check(True,'Offline cache miss returns 404, not fabricated content');Fixture.failing=False
            c.call('POST','/api/repos',400,json={'name':'docker-proxy','format':'docker','type':'proxy','upstream':Fixture.url});check(True,'Unimplemented Docker proxy mode is rejected explicitly')
            # Asset browsing, audit and maintenance.
            listing=c.call('GET','/api/assets?repo=raw-hosted&limit=1').json();check(len(listing['items'])==1 and listing['total']>1,'Artifact browser has real pagination, not a total-count quota')
            audit=c.call('GET','/api/audit?limit=100').json()['items'];check(any(e['user']=='admin' and e['path']=='/api/repos' for e in audit),'Administrative mutation audit includes the authenticated username')
            c.call('POST','/api/maintenance',400,json={'action':'gc'});check(True,'Maintenance operations require explicit confirmation')
            check(c.call('POST','/api/maintenance',json={'action':'verify','confirm':'MAINTENANCE'}).json()['ok'],'All referenced artifact hashes verify after protocol operations')
            preview=c.call('POST','/api/maintenance',json={'action':'gc','dryRun':True,'confirm':'MAINTENANCE'}).json();check(preview['dryRun'] and preview['graceHours']==24,'Garbage collection supports dry-run and a safety grace period')
            blocked=server.command('--compact');check(blocked.returncode!=0,'Offline maintenance refuses to share a live data directory')
            c.call('POST','/api/maintenance',json={'action':'compact','confirm':'MAINTENANCE'})
            check(c.call('GET','/repository/raw-hosted/releases/demo.bin').content==content,'Metadata checkpoint preserves accessible artifacts')
            old_token=c.token;server.stop();server.start();c.session=requests.Session();c.token=old_token
            check(c.call('GET','/repository/raw-hosted/releases/demo.bin').content==content,'Raw artifact and API token survive full JVM restart')
            check(c.call('GET','/repository/pypi-hosted/simple/graph-demo/').status_code==200,'Python package index survives full JVM restart')
            check(c.call('GET',root+'/manifests/latest').content==manifest,'OCI tags and manifests survive full JVM restart')
            if shutil.which('npm'):check('1.0.0' in c.call('GET','/repository/npm-hosted/graph-demo').json()['versions'],'npm metadata survives full JVM restart')
            server.stop();backup=tmp/'backup.zip';result=server.command('--backup',str(backup));check(result.returncode==0 and backup.exists(),'Offline Java backup exports a locked, checkpointed data directory')
            restored=tmp/'restored';restored.mkdir()
            with zipfile.ZipFile(backup) as archive:archive.extractall(restored)
            restore=Server(restored,GR_ALLOW_PRIVATE_UPSTREAM='true',GR_ALLOW_HTTP_UPSTREAM='true')
            try:
                restore.start();restored_client=Client(restore);restored_client.token=old_token
                check(restored_client.call('GET','/repository/raw-hosted/releases/demo.bin').content==content,'Backup restored to a new server preserves artifacts and authentication')
            finally:restore.stop()
        finally:
            server.stop();fixture.shutdown();fixture.server_close()
    report={'passed':len(CHECKS),'checks':CHECKS,'skipped':SKIPPED,'engine':'real Spring Boot HTTP server','upstreams':'local HTTP fixtures','realClients':['npm','pip'],'notExecuted':['Maven CLI','Gradle CLI','.NET/NuGet CLI','Docker CLI','public upstream connectivity']}
    (ROOT/'docs/qa/integration-report.json').write_text(json.dumps(report,indent=2))
    print('INTEGRATION_TESTS_PASSED='+str(len(CHECKS)))
    if SKIPPED:print('SKIPPED='+str(SKIPPED))

if __name__=='__main__': run()
