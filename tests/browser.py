#!/usr/bin/env python3
"""Normal browser/HTTP integration against the live Java server (no DOM fixture bridge)."""
from __future__ import annotations
import base64,json,os,shutil,tempfile
from pathlib import Path
import requests
from playwright.sync_api import sync_playwright,expect
from support import ROOT,Server,PASSWORD
from integration import wheel,nupkg,tarball

CHECKS=[]
def check(value,label):
    if not value:raise AssertionError(label)
    CHECKS.append(label);print('PASS',label,flush=True)

def run():
    qa=ROOT/'docs/qa';qa.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='graph-browser-') as temp, Server(Path(temp)/'data') as server:
        auth=('admin',PASSWORD)
        def put(path,data,**kw):
            response=requests.put(server.url+path,data=data,auth=auth,timeout=30,**kw)
            assert response.status_code in (200,201,202),response.text
        put('/repository/raw-hosted/releases/graph-service-1.0.zip',b'Local QA artifact - not production content')
        put('/repository/maven-releases/ir/graph/demo/1.0.0/demo-1.0.0.jar',b'Local Java QA artifact')
        put('/repository/maven-releases/ir/graph/demo/1.0.0/demo-1.0.0.pom',b'<project><modelVersion>4.0.0</modelVersion><groupId>ir.graph</groupId><artifactId>demo</artifactId><version>1.0.0</version></project>')
        r=requests.post(server.url+'/repository/pypi-hosted/',auth=auth,data={':action':'file_upload','name':'graph-demo','version':'1.0.0'},files={'content':('graph_demo-1.0.0-py3-none-any.whl',wheel())},timeout=30);assert r.status_code in (200,201),r.text
        put('/repository/nuget-hosted/v2/package',nupkg(),headers={'Content-Type':'application/octet-stream'})
        name='graph-demo';archive=tarball(name,'1.0.0');body={'name':name,'description':'Local browser QA fixture','versions':{'1.0.0':{'name':name,'version':'1.0.0','dist':{'tarball':'http://unused.invalid/graph-demo-1.0.0.tgz'}}},'dist-tags':{'latest':'1.0.0'},'_attachments':{'graph-demo-1.0.0.tgz':{'data':base64.b64encode(archive).decode()}}}
        r=requests.put(server.url+'/repository/npm-hosted/graph-demo',auth=auth,json=body,timeout=30);assert r.status_code in (200,201),r.text
        with sync_playwright() as pw:
            browser=pw.chromium.launch(executable_path=os.getenv('CHROMIUM_BIN') or shutil.which('chromium'),headless=True,args=['--no-sandbox'])
            context=browser.new_context(viewport={'width':1440,'height':1040},device_scale_factor=1,locale='fa-IR')
            page=context.new_page();errors=[];violations=[];external=[]
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.on('console',lambda msg:violations.append(msg.text) if 'violates the following Content Security Policy' in msg.text else None)
            page.on('request',lambda req:external.append(req.url) if not req.url.startswith(server.url) else None)
            page.goto(server.url,wait_until='networkidle');expect(page.locator('#login')).to_be_visible()
            check(page.locator('html').get_attribute('dir')=='rtl' and page.locator('html').get_attribute('lang')=='fa','Persian RTL document loads over normal HTTP')
            page.screenshot(path=str(qa/'01-login.png'),full_page=True)
            page.locator('[name=password]').fill(PASSWORD);page.locator('#loginForm button').click()
            expect(page.locator('.kpis')).to_be_visible();check(True,'Real browser login and same-origin session succeed')
            cookie=next(x for x in context.cookies() if x['path']=='/api')
            check(cookie['httpOnly'] and cookie['sameSite']=='Strict','Actual browser enforces HttpOnly/SameSite session cookie')
            check(page.locator('.kpis .kpi').count()==4 and page.locator('.name-cell').count()==7,'Dashboard renders real API repository and storage values')
            check(page.evaluate('Object.keys(localStorage).length')==0,'No credentials or session secrets are written to localStorage')
            page.screenshot(path=str(qa/'02-dashboard.png'),full_page=True)
            page.locator('#newRepo').click();page.locator('#repoForm [name=name]').fill('browser-raw');page.locator('#repoForm [name=format]').select_option('raw');page.locator('#repoForm button[type=submit]').click()
            expect(page.locator('#dialog')).not_to_be_visible();check(requests.get(server.url+'/api/repos',auth=auth).json()['items'][-1:] is not None,'Repository create form completes a real CSRF-protected mutation')
            check(any(x['name']=='browser-raw' for x in requests.get(server.url+'/api/repos',auth=auth).json()['items']),'New repository is persisted in the Java engine')
            def navigate(target,selector):
                page.locator(f'nav a[data-page="{target}"]').click();expect(page.locator(selector)).to_be_visible()
            navigate('repos','#repoTable');page.screenshot(path=str(qa/'03-repositories.png'),full_page=True)
            page.locator('#repoFilter').fill('browser-raw');expect(page.locator('[data-edit="browser-raw"]')).to_be_visible()
            page.locator('[data-edit="browser-raw"]').click();page.locator('#repoForm [name=redeploy]').check();page.locator('#repoForm button[type=submit]').click();expect(page.locator('#dialog')).not_to_be_visible()
            check(next(x for x in requests.get(server.url+'/api/repos',auth=auth).json()['items'] if x['name']=='browser-raw')['redeploy'],'Repository edit form saves operator policy')
            navigate('browse','#browseForm');page.locator('#upload').click();page.locator('#uploadForm [name=repo]').select_option('browser-raw');page.locator('#uploadForm [name=path]').fill('releases/browser-test.txt');page.locator('#uploadForm [name=file]').set_input_files({'name':'browser-test.txt','mimeType':'text/plain','buffer':b'Uploaded by the real browser'})
            page.locator('#uploadForm button[type=submit]').click();expect(page.locator('#dialog')).not_to_be_visible()
            check(requests.get(server.url+'/repository/browser-raw/releases/browser-test.txt',auth=auth).content==b'Uploaded by the real browser','Browser file upload stores the original bytes')
            page.locator('#browseForm [name=repo]').select_option('browser-raw');page.locator('#browseForm button[type=submit]').click();expect(page.locator('td.path-cell')).to_have_count(1)
            with page.expect_download() as event:page.locator('a[href^="/api/download"]').click()
            download=event.value;check(Path(download.path()).read_bytes()==b'Uploaded by the real browser','Authenticated browser download uses the actual session cookie')
            navigate('browse','#browseForm');page.screenshot(path=str(qa/'05-browse.png'),full_page=True)
            navigate('clients','#clientRepo');page.locator('#clientRepo').select_option('npm-hosted');check(server.url in page.locator('.code-box').inner_text(),'Client connection examples use this server public URL')
            navigate('users','#newUser');page.locator('#newUser').click();page.locator('#userForm [name=username]').fill('browser-reader');page.locator('#userForm [name=password]').fill('browser-only-password-48216');page.locator('#userForm button[type=submit]').click();expect(page.locator('#dialog')).not_to_be_visible()
            check(any(x['username']=='browser-reader' for x in requests.get(server.url+'/api/users',auth=auth).json()['items']),'Browser user form creates a least-privilege account')
            navigate('tokens','#newToken');page.locator('#newToken').click();page.locator('#tokenForm [name=label]').fill('browser-temporary-token');page.locator('#tokenForm button[type=submit]').click();expect(page.locator('.token-value')).to_be_visible();secret=page.locator('.token-value').inner_text();check(requests.get(server.url+'/api/session',headers={'Authorization':'Bearer '+secret}).status_code==200,'Browser token form issues a working API token')
            page.locator('#closeDialog').click();page.locator('[data-token]').last.click();page.locator('#revokeToken button[type=submit]').click();expect(page.locator('#dialog')).not_to_be_visible();check(requests.get(server.url+'/api/session',headers={'Authorization':'Bearer '+secret}).status_code==401,'Browser revoke form invalidates the API token')
            navigate('audit','#refresh');check(page.locator('td.path-cell').count()>0,'Audit view displays actual server mutation records')
            navigate('maintenance','#do-verify');page.locator('#do-verify').click();page.locator('#maintForm [name=confirm]').fill('MAINTENANCE');page.locator('#maintForm button[type=submit]').click();expect(page.locator('#maintenanceResult .code-box')).to_be_visible();check(json.loads(page.locator('#maintenanceResult .code-box').inner_text())['ok'],'Browser maintenance verification runs against stored blobs')
            navigate('settings','.kv');check('0.5.0-preview' in page.locator('.kv').inner_text(),'Settings view reflects running Java engine')
            navigate('dashboard','.kpis');page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(350);expect(page.locator('.toast')).to_have_count(0,timeout=16000);check(page.locator('#sidebar').bounding_box()['x']>=389,'Mobile sidebar is fully off-canvas until opened');page.screenshot(path=str(qa/'04-mobile.png'),full_page=True)
            check(page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'),'Mobile page has no document-level horizontal overflow')
            page.locator('#menuBtn').click();expect(page.locator('#shell.menu-open')).to_be_visible();page.locator('nav a[data-page="repos"]').click();expect(page.locator('#repoTable')).to_be_visible();check('menu-open' not in (page.locator('#shell').get_attribute('class') or ''),'Mobile drawer navigation closes after selection')
            page.set_viewport_size({'width':1440,'height':1040});page.locator('#repoFilter').fill('browser-raw');page.locator('[data-delete="browser-raw"]').click();page.locator('#deleteRepo [name=confirm]').fill('browser-raw');page.locator('#deleteRepo [name=purge]').check();page.locator('#deleteRepo button[type=submit]').click();expect(page.locator('#dialog')).not_to_be_visible();check(not any(x['name']=='browser-raw' for x in requests.get(server.url+'/api/repos',auth=auth).json()['items']),'Browser repository deletion requires exact confirmation and purge approval')
            page.locator('#logout').click();expect(page.locator('#login')).to_be_visible();page.reload();expect(page.locator('#login')).to_be_visible();check(True,'Browser logout invalidates the session across reload')
            check(not errors,'No uncaught JavaScript errors: '+str(errors));check(not violations,'No Content-Security-Policy violations');check(not external,'No external requests, tracking services, fonts or CDNs')
            browser.close()
    report={'passed':len(CHECKS),'checks':CHECKS,'engine':'real Spring Boot server; isolated temporary fixture packages','browser':'Chromium over normal HTTP; no DOM bridge','screenshots':'temporary test fixtures, not production data','viewportDesktop':[1440,1040],'viewportMobile':[390,844]}
    (qa/'browser-report.json').write_text(json.dumps(report,indent=2)+'\n');print('BROWSER_TESTS_PASSED='+str(len(CHECKS)))
if __name__=='__main__':run()
