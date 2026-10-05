#!/usr/bin/env python3
"""Browser regression suite using an explicitly synthetic, local HTTP fixture.

This checks the delivered HTML/JS/CSS in Chromium with native navigation, cookies
and a restrictive CSP. It does NOT start or verify the Spring Boot server.
Requires playwright and Chromium. No npm packages, CDN, or external fixture server.
"""
from __future__ import annotations

import copy
import argparse
import base64
import re
import requests
import json
import mimetypes
import os
import shutil
import threading
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlsplit

from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "repository-server/src/main/resources/static"
REPORTS = ROOT / "docs/qa"
ASSETS = ROOT / "docs/assets"
PASSWORD = "fixture-password-only"
CSP = ("default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
       "connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
FIXTURE = json.loads((ROOT / "tests/fixtures/console-data.json").read_text(encoding="utf-8"))
CATALOGS = {language: json.loads((STATIC / f"locales/{language}.json").read_text(encoding="utf-8"))
            for language in ("en", "de", "fa")}
RESULTS: list[str] = []
REQUESTS: list[dict] = []


class Handler(BaseHTTPRequestHandler):
    """Intentionally tiny fixture, not an alternative production implementation."""
    def log_message(self, *_args):
        pass

    def respond(self, code, value, extra=None, content_type="application/json; charset=utf-8"):
        data = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Content-Security-Policy", CSP)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        for key, val in (extra or {}).items():
            self.send_header(key, val)
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlsplit(self.path).path
        if not path.startswith("/api/"):
            target = (STATIC / (unquote(path).lstrip("/") or "index.html")).resolve()
            if STATIC not in target.parents or not target.is_file():
                return self.respond(404, {})
            mime = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
            return self.respond(200, target.read_bytes(), content_type=mime)
        REQUESTS.append({"path": path, "language": self.headers.get("Accept-Language")})
        if "gr_session=fixture" not in self.headers.get("Cookie", ""):
            return self.respond(401, {"error": "UNAUTHORIZED", "message": "Fixture authentication required."})
        base = f"http://127.0.0.1:{self.server.server_port}"
        if path == "/api/session":
            return self.respond(200, {"username": "admin", "admin": True, "csrf": "fixture-csrf",
                                     "publicUrl": base, "version": "0.5.0-preview"})
        if path == "/api/repos":
            items = copy.deepcopy(FIXTURE["repos"])
            for item in items:
                item["url"] = base + "/repository/" + item["name"] + "/"
            return self.respond(200, {"items": items})
        if path == "/api/stats":
            return self.respond(200, FIXTURE["stats"])
        if path == "/api/insights/storage":
            return self.respond(200, FIXTURE["insights"])
        if path == "/api/quarantine":
            return self.respond(200, {"items": FIXTURE["quarantine"]})
        if path == "/api/releases":
            query = parse_qs(urlsplit(self.path).query)
            state = query.get("state", [""])[0]
            offset = int(query.get("offset", ["0"])[0]); limit = int(query.get("limit", ["100"])[0])
            items = [item for item in FIXTURE["releases"] if not state or item["state"] == state]
            return self.respond(200, {"items":items[offset:offset+limit], "total":len(items), "offset":offset, "limit":limit})
        if path.startswith("/api/releases/"):
            rid = path.split("/")[3]
            item = next((i for i in FIXTURE["releases"] if i["id"] == rid), None)
            if not item: return self.respond(404, {"error":"NOT_FOUND"})
            if path.endswith("/gate"):
                return self.respond(200, {"releaseId":rid,"state":item["state"],"allowed":item["state"]=="RELEASED", "deep":True,"checkedDigests":2,"sourceDrift":0,"checkedAt":"2026-10-05T09:00:00Z","problems":[],"syntheticFixture":True})
            return self.respond(200, item)
        if path == "/api/assets":
            query = parse_qs(urlsplit(self.path).query)
            repo = query.get("repo", [""])[0]
            term = query.get("q", [""])[0].lower()
            items = [asset for asset in FIXTURE["assets"] if (not repo or asset["repo"] == repo)
                     and term in asset["path"].lower()]
            offset = int(query.get("offset", ["0"])[0])
            limit = int(query.get("limit", ["25"])[0])
            return self.respond(200, {"items": items[offset:offset + limit], "offset": offset,
                                     "limit": limit, "total": len(items)})
        if path in ("/api/users", "/api/tokens", "/api/audit"):
            return self.respond(200, {"items": FIXTURE[path.removeprefix("/api/")]})
        if path == "/api/system":
            return self.respond(200, {"version": "0.5.0-preview", "framework": "Spring Boot",
                "springBoot": "4.1.1", "springFramework": "7.x (fixture value)", "java": "25 (fixture value)",
                "dataDirectory": "/var/lib/graph-repository", "publicUrl": base,
                "maxUploadBytes": 0, "maxJsonBytes": 67108864, "concurrentRequests": 0,
                "licenseQuotas": False, "clustered": False, "allowPrivateUpstream": False,
                "allowHttpUpstream": False, "formats": ["maven", "npm", "nuget", "pypi", "raw", "docker"],
                "defaultLanguage": "en", "supportedLanguages": ["en", "de", "fa"]})
        return self.respond(404, {"error": "NOT_FOUND"})

    def do_POST(self):
        path = urlsplit(self.path).path
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        value = json.loads(body or "{}")
        REQUESTS.append({"path": path, "language": self.headers.get("Accept-Language"), "body": value})
        if path == "/api/login":
            if value.get("password") != PASSWORD:
                return self.respond(401, {"error": "UNAUTHORIZED", "requestId": "fixture-error"})
            return self.respond(200, {"username": "admin", "admin": True, "csrf": "fixture-csrf"},
                {"Set-Cookie": "gr_session=fixture; Path=/api; HttpOnly; SameSite=Strict"})
        if path == "/api/logout":
            return self.respond(200, {"ok": True},
                {"Set-Cookie": "gr_session=; Path=/api; Max-Age=0; HttpOnly; SameSite=Strict"})
        if path == "/api/tokens":
            return self.respond(201, {"token": "fixture-only-not-a-real-token"})
        if path == "/api/quarantine":
            prior = next((i for i in FIXTURE["quarantine"] if i["sha256"] == value["sha256"]), None)
            item = dict(value, revision=value["expectedRevision"]+1, updated="2026-10-05T09:30:00Z", actor="admin")
            if prior: FIXTURE["quarantine"].remove(prior)
            FIXTURE["quarantine"].insert(0,item)
            return self.respond(200, item)
        if path == "/api/releases":
            item = copy.deepcopy(FIXTURE["releases"][3])
            item.update(id="414010b2-0487-43e5-8e52-8ea21fcad26b",name=value["name"],version=value["version"],note=value["note"],state="DRAFT",revision=1,allowedActions=["submit"])
            FIXTURE["releases"].append(item)
            return self.respond(201, item)
        if "/transitions/" in path:
            rid = path.split("/")[3]; action = path.split("/")[-1]
            item = next(i for i in FIXTURE["releases"] if i["id"] == rid)
            if item["revision"] != value["expectedRevision"]:
                return self.respond(409, {"error":"STALE_RELEASE"})
            item["revision"] += 1; item["state"] = {"submit":"IN_REVIEW","approve":"APPROVED","release":"RELEASED","revoke":"REVOKED","reject":"REJECTED"}[action]
            item["allowedActions"] = []
            item["events"].append({"action":action,"actor":"admin","time":"2026-10-05T09:35:00Z","note":value["note"]})
            return self.respond(200,item)
        if path == "/api/maintenance":
            return self.respond(200, {"ok": True, "dryRun": value.get("dryRun"), "fixture": True})
        return self.respond(200, {"ok": True})


def check(condition: bool, name: str):
    if not condition:
        raise AssertionError(name)
    RESULTS.append(name)


def ready(page):
    page.wait_for_selector("#shell:not([hidden])")
    page.wait_for_selector('#content[aria-busy="false"]')


def switch(page, language, location="header"):
    page.locator(f"{location} [data-language-switch]").select_option(language)
    expect(page.locator("html")).to_have_attribute("lang", language)
    ready(page)


def navigate(page, route):
    page.evaluate("(route) => { location.hash = route; }", route)
    language = page.locator("html").get_attribute("lang")
    expect(page.locator("#content h1")).to_have_text(CATALOGS[language][route])
    ready(page)


def screenshot(page, name):
    page.evaluate("document.activeElement?.blur()")
    page.evaluate("""() => { let badge = document.querySelector('#fixtureBadge'); if (!badge) {
      badge = document.createElement('div'); badge.id='fixtureBadge'; badge.textContent='SYNTHETIC WORKSPACE / UI PREVIEW';
      badge.style.cssText='position:fixed;bottom:8px;right:12px;padding:6px 10px;background:#f7f8f0;color:#516244;border:1px solid #dbe1d3;border-radius:5px;font:9px Arial;z-index:9999;pointer-events:none';document.body.appendChild(badge); }}""")
    page.screenshot(path=str(ASSETS / name), full_page=True, animations="disabled")


def bridge_bootstrap(page, base):
    """DOM-only escape hatch for environments where all navigation is blocked.

    This does not exercise native ES-module requests, CSP or browser cookie rules.
    The same source modules are concatenated only in the test harness, and fetch is
    forwarded to the synthetic HTTP server through a Python requests session.
    """
    session = requests.Session()
    session.trust_env = False

    def bridge(payload):
        response = session.request(payload["method"], base + payload["path"],
                                   headers=payload.get("headers"), data=payload.get("body"), timeout=10)
        return {"status": response.status_code, "body": response.text,
                "headers": dict(response.headers)}

    page.expose_function("__fixtureHttp", bridge)
    html = (STATIC / "index.html").read_text(encoding="utf-8")
    html = re.sub(r'<script type="module".*?</script>', "", html)
    html = re.sub(r'<link rel="stylesheet"[^>]*>', "", html)
    html = re.sub(r'<link rel="icon"[^>]*>', "", html)
    logo = "data:image/svg+xml;base64," + base64.b64encode((STATIC / "logo.svg").read_bytes()).decode()
    html = html.replace('src="/logo.svg"', f'src="{logo}"')
    page.set_content(html)
    page.add_style_tag(content=(STATIC / "style.css").read_text(encoding="utf-8"))
    page.add_script_tag(content="""
      window.__fixtureStorage = new Map();
      Object.defineProperty(window, 'localStorage', {configurable: true, value: {
        getItem: key => window.__fixtureStorage.get(key) ?? null,
        setItem: (key, value) => window.__fixtureStorage.set(key, String(value)),
        removeItem: key => window.__fixtureStorage.delete(key)
      }});
      window.fetch = async (path, options = {}) => {
        const result = await window.__fixtureHttp({
          path: String(path), method: options.method || 'GET',
          headers: options.headers || {}, body: options.body || null
        });
        return new Response(result.body, {status: result.status, headers: result.headers});
      };
    """)
    modules = []
    for name in ("i18n.js", "client-examples.js", "delivery.js", "app.js"):
        source = (STATIC / name).read_text(encoding="utf-8")
        source = re.sub(r"^import .+?;\n", "", source, flags=re.M)
        source = re.sub(r"^export (?=(const|class|function))", "", source, flags=re.M)
        modules.append(source)
    page.add_script_tag(type="module", content="\n\n".join(modules))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bridge", action="store_true",
                        help="DOM fixture mode; does not verify native navigation, CSP or cookies")
    args = parser.parse_args()
    REPORTS.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    RESULTS.clear()
    REQUESTS.clear()
    for name in ("browser-report.json", "browser-failure.txt", "browser-failure.png", "browser-trace.zip"):
        (REPORTS / name).unlink(missing_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    base = f"http://127.0.0.1:{server.server_port}"
    errors = []
    succeeded = False
    try:
        with sync_playwright() as playwright:
            executable = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
            options = {"headless": True, "args": ["--no-sandbox", "--disable-dev-shm-usage"]}
            if executable:
                options["executable_path"] = executable
            browser = playwright.chromium.launch(**options)
            context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="de-DE")
            page = context.new_page()
            page.set_default_timeout(10000)
            expect.set_options(timeout=10000)
            context.tracing.start(screenshots=True, snapshots=True, sources=True)
            try:
                page.on("pageerror", lambda error: errors.append(str(error)))
                if args.bridge:
                    bridge_bootstrap(page, base)
                else:
                    page.goto(base)
                page.wait_for_selector("#login:not([hidden])")
                check(page.locator("html").get_attribute("lang") == "en", "English default despite a German browser locale")
                check(page.locator("html").get_attribute("dir") == "ltr", "Initial LTR layout")
                check(page.locator("#loginForm button").inner_text() == "Sign in", "English default sign-in label")
                screenshot(page, "login-en.png")
                page.locator("#loginForm [name=password]").fill("incorrect")
                page.locator("#loginForm button").click()
                expect(page.locator("#loginError")).not_to_have_text("")
                check("Sign in" in page.locator("#loginError").inner_text(), "Localized authentication error")
                page.locator("#login [data-language-switch]").select_option("de")
                expect(page.locator("html")).to_have_attribute("lang", "de")
                check("Melden" in page.locator("#loginError").inner_text(), "Existing authentication error retranslates")
                page.locator("#loginForm [name=password]").fill(PASSWORD)
                page.locator("#loginForm button").click()
                ready(page)
                check(page.locator("#content h1").inner_text() == CATALOGS["de"]["dashboard"], "German sign-in and dashboard")
                for language, direction in (("en", "ltr"), ("de", "ltr"), ("fa", "rtl")):
                    switch(page, language)
                    check(page.locator("html").get_attribute("dir") == direction, f"{language}: document direction")
                    navigate(page, "dashboard")
                    ready(page)
                    screenshot(page, f"dashboard-{language}.png")
                    for route in ("dashboard", "releases", "insights", "quarantine", "repos", "browse", "clients", "users", "tokens", "audit", "maintenance", "settings"):
                        page.evaluate("(route) => { location.hash = route; }", route)
                        expect(page.locator("#content h1")).to_have_text(CATALOGS[language][route])
                        ready(page)
                        check(page.locator("#content h1").inner_text() == CATALOGS[language][route],
                              f"{language}: {route} heading")
                        check(page.evaluate("document.documentElement.scrollWidth <= innerWidth + 1"),
                              f"{language}: {route} desktop has no page overflow")
                    latest = [item for item in REQUESTS if item["path"] == "/api/system"][-1]
                    check(latest["language"] == language, f"{language}: selected Accept-Language reaches HTTP requests")
                # Actual delivered views and dialogs, with synthetic API data explicitly labelled.
                switch(page, "en")
                navigate(page, "releases")
                check(page.locator('.release-lane').count() == 4, 'Release board has four active lanes')
                screenshot(page, "releases-en.png")
                page.locator('#releaseState').select_option('RELEASED')
                ready(page)
                expect(page.locator(".release-lane")).to_have_count(1)
                check(page.locator('.release-card').count() == 2, 'Server-side stage filter is reflected in cards')
                page.locator('.release-card').first.click()
                page.wait_for_selector('#verifyCapsule')
                check(page.locator('#dialogBody a[href$="/assets/0"]').count() == 1, 'Released capsule links to frozen delivery endpoint')
                page.locator('#verifyCapsule').click()
                page.wait_for_selector('.gate-open')
                check(page.locator('.gate-open').count() == 1, 'Deep verification result is displayed (synthetic response)')
                screenshot(page, 'capsule-en.png')
                page.locator('#closeDialog').click()
                navigate(page, 'insights'); screenshot(page, 'insights-en.png')
                check(page.locator('meter').get_attribute('value') == '28.9', 'Storage meter reflects supplied measured accounting')
                navigate(page, 'quarantine'); screenshot(page, 'quarantine-en.png')
                page.locator('#newHold').click()
                page.locator('#holdForm [name=sha256]').fill('c' * 64)
                page.locator('#holdForm [name=reason]').fill('Synthetic UI investigation only')
                switch(page, 'de', '#dialog')
                check(page.locator('#holdForm [name=reason]').input_value() == 'Synthetic UI investigation only', 'Containment reason survives language switch')
                page.locator('#holdForm [type=submit]').click()
                expect(page.locator("#dialog")).not_to_be_visible()
                check(any(i.get('body', {}).get('sha256') == 'c'*64 for i in REQUESTS), 'Containment sends explicit digest and revision')
                navigate(page, 'browse')
                page.locator('[data-add-release]').first.click()
                page.locator('#createFromBasket').click()
                page.wait_for_selector('#capsuleForm')
                check(bool(page.locator('#capsuleForm [name=assets]').input_value()), 'Artifact explorer selection populates release capture')
                page.locator('#capsuleForm [name=name]').fill('sample-release')
                page.locator('#capsuleForm [name=version]').fill('1.0.0')
                page.locator('#capsuleForm [name=note]').fill('Synthetic UI capture')
                switch(page, 'fa', '#dialog')
                check(page.locator('#capsuleForm [name=name]').input_value() == 'sample-release', 'Release name and selection survive RTL switching')
                page.locator('#capsuleForm [type=submit]').click()
                page.wait_for_selector('#transition-submit')
                check('sample-release' in page.locator('#dialogTitle').inner_text(), 'Created capsule opens with server identifier')
                page.locator('#transition-submit').click()
                page.locator('#releaseDecision [name=note]').fill('Request independent review')
                page.locator('#releaseDecision [type=submit]').click()
                page.wait_for_selector('#verifyCapsule')
                last = [i for i in REQUESTS if '/transitions/submit' in i['path']][-1]
                check(last['body']['expectedRevision'] == 1, 'Decision sends the captured revision for optimistic concurrency')
                page.locator('#closeDialog').click()

                # Edit an unsaved form while switching between all three languages.
                navigate(page, "repos")
                ready(page)
                page.locator("#newRepo").click()
                page.locator('#repoForm [name=name]').fill("unsaved-repository")
                page.locator('#repoForm [name=format]').select_option("npm")
                page.locator('#repoForm [name=type]').select_option("proxy")
                page.locator('#repoForm [name=upstream]').fill("https://registry.npmjs.org/")
                switch(page, "de", "#dialog")
                check(page.locator('#repoForm [name=name]').input_value() == "unsaved-repository", "Unsaved name survives language switching")
                check(page.locator('#repoForm [name=format]').input_value() == "npm", "Unsaved format survives language switching")
                check(page.locator('#repoForm [name=upstream]').input_value() == "https://registry.npmjs.org/", "Unsaved upstream URL survives language switching")
                check(page.locator("#dialogTitle").inner_text() == CATALOGS["de"]["newRepo"], "Dialog title switches to German")
                screenshot(page, "repository-dialog-de.png")
                page.locator("#closeDialog").click()
                # Selected uploads must not disappear when translating an open dialog.
                navigate(page, "browse")
                ready(page)
                page.locator("#upload").click()
                page.locator('#uploadForm [name=file]').set_input_files({
                    "name": "fixture.txt", "mimeType": "text/plain", "buffer": b"UI fixture only"
                })
                switch(page, "fa", "#dialog")
                check(page.locator('#uploadForm [name=file]').evaluate("(element) => element.files[0].name") == "fixture.txt",
                      "Selected file survives dialog retranslation")
                check(page.locator('#uploadForm [name=path]').input_value() == "fixture.txt", "Upload path is retained")
                page.locator("#closeDialog").click()
                # One-time tokens are not generated again on a language change.
                navigate(page, "tokens")
                ready(page)
                page.locator("#newToken").click()
                page.locator('#tokenForm [name=label]').fill("UI test only")
                page.locator('#tokenForm [type=submit]').click()
                page.wait_for_selector(".token-value")
                before = len([item for item in REQUESTS if item["path"] == "/api/tokens" and "body" in item])
                switch(page, "en", "#dialog")
                check(page.locator(".token-value").inner_text() == "fixture-only-not-a-real-token",
                      "One-time token stays visible when language changes")
                after = len([item for item in REQUESTS if item["path"] == "/api/tokens" and "body" in item])
                check(before == after == 1, "Language change does not issue another token")
                # Reproduce a queued close event from the previous form after its replacement opened.
                page.evaluate("document.querySelector('#dialog').dispatchEvent(new Event('close'))")
                check(page.locator(".token-value").inner_text() == "fixture-only-not-a-real-token",
                      "A delayed close event cannot clear a replacement token dialog")
                page.locator("#closeDialog").click()
                if args.bridge:
                    check(page.evaluate("localStorage.getItem('graph.repository.language')") == "en",
                          "Selected language is stored (bridge; native reload/cookie checks excluded)")
                else:
                    page.reload()
                    ready(page)
                    check(page.locator("html").get_attribute("lang") == "en", "Selected language persists on reload")
                    cookies = context.cookies()
                    check(any(cookie["name"] == "gr_session" and cookie["httpOnly"] for cookie in cookies), "Native HttpOnly fixture cookie used")
                # Mobile RTL and LTR layout, navigation and German long labels.
                page.set_viewport_size({"width": 390, "height": 844})
                for language in ("en", "de", "fa"):
                    switch(page, language)
                    navigate(page, "dashboard")
                    ready(page)
                    check(page.evaluate("document.documentElement.scrollWidth <= innerWidth + 1"),
                          f"{language}: mobile dashboard has no horizontal page overflow")
                    screenshot(page, f"mobile-{language}.png")
                    page.locator("#menuBtn").click()
                    check(page.locator("#menuBtn").get_attribute("aria-expanded") == "true", f"{language}: mobile menu opens")
                    page.keyboard.press("Escape")
                    check(page.locator("#menuBtn").get_attribute("aria-expanded") == "false", f"{language}: Escape closes mobile menu")
                check(not errors, f"No unhandled browser errors ({len(errors)})")
                # Fresh browser contexts do not inherit language preferences.
                fresh = browser.new_context(locale="fa-IR")
                fresh_page = fresh.new_page()
                if args.bridge:
                    bridge_bootstrap(fresh_page, base)
                else:
                    fresh_page.goto(base)
                fresh_page.wait_for_selector("#login:not([hidden])")
                check(fresh_page.locator("html").get_attribute("lang") == "en", "Fresh Persian browser still starts in English")
                fresh.close()
            except Exception:
                # Do not replace the original exception if evidence capture itself fails.
                (REPORTS / "browser-failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
                try:
                    page.screenshot(path=str(REPORTS / "browser-failure.png"), full_page=True)
                except Exception as capture_error:
                    errors.append(f"Failure screenshot unavailable: {capture_error}")
                raise
            finally:
                try:
                    context.tracing.stop(path=str(REPORTS / "browser-trace.zip"))
                finally:
                    browser.close()
            succeeded = True
    finally:
        server.shutdown()
        server.server_close()
        report = {"suite": "multilingual-browser-fixture", "status": "passed" if succeeded else "failed", "springBootRuntimeTest": False,
                  "navigation": "DOM + synthetic HTTP bridge" if args.bridge else "native Chromium HTTP",
                  "nativeCspAndCookiesVerified": succeeded and not args.bridge, "cspFixture": CSP, "checksPassed": len(RESULTS),
                  "checks": RESULTS, "browserErrors": errors, "screenshotsUseSyntheticFixtures": True}
        (REPORTS / "browser-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"BROWSER_FIXTURE_CHECKS_PASSED={len(RESULTS)}")


if __name__ == "__main__":
    main()
