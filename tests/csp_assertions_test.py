#!/usr/bin/env python3
"""CSP regression tests for browser waits; no server or network is required.

The fixture is a real Chromium document with a restrictive meta CSP. Only the
exact fixture script is hash-authorized. Dynamic eval remains forbidden.
This verifies locator assertions under CSP, not Spring Boot or HTTP cookies.
"""
from __future__ import annotations

import ast
import base64
import hashlib
import json
import os
import shutil
import unittest
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = """
document.querySelector('#evalProbe').addEventListener('click', () => {
  try { globalThis.eval('1 + 1'); document.querySelector('#evalResult').textContent = 'unexpectedly allowed'; }
  catch (error) { document.querySelector('#evalResult').textContent = error.name; }
});
const titles = {en: 'Overview', de: 'Uebersicht', fa: 'Persian overview'};
document.querySelector('#language').addEventListener('change', event => {
  const language = event.target.value;
  setTimeout(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'fa' ? 'rtl' : 'ltr';
    document.querySelector('h1').textContent = titles[language];
  }, 30);
});
document.querySelector('#login').addEventListener('click', () => {
  setTimeout(() => { document.querySelector('#loginError').textContent = 'Sign in failed'; }, 30);
});
document.querySelector('#filter').addEventListener('click', () => {
  setTimeout(() => {
    const lanes = [...document.querySelectorAll('.release-lane')];
    lanes.slice(1).forEach(lane => lane.remove());
  }, 30);
});
document.querySelector('#openDialog').addEventListener('click', () => {
  document.querySelector('#dialog').showModal();
});
"""
DIGEST = base64.b64encode(hashlib.sha256(SCRIPT.encode()).digest()).decode()
POLICY = f"default-src 'none'; script-src 'sha256-{DIGEST}'; base-uri 'none'; form-action 'none'"
HTML = f'''<!doctype html><html lang="en" dir="ltr"><head>
<meta http-equiv="Content-Security-Policy" content="{POLICY}">
<title>CSP assertion fixture</title></head><body>
<select id="language"><option value="en">English</option><option value="de">Deutsch</option>
<option value="fa">Persian</option></select><h1>Overview</h1>
<button id="evalProbe">Probe</button><output id="evalResult"></output>
<button id="login">Sign in</button><div id="loginError"></div>
<button id="filter">Filter</button><div class="release-lane">Draft</div>
<div class="release-lane">Review</div><div class="release-lane">Approved</div>
<div class="release-lane">Released</div>
<button id="openDialog">Open</button><dialog id="dialog">
<form method="dialog"><button id="closeDialog">Close</button></form></dialog>
<script>{SCRIPT}</script></body></html>'''


class CspAssertionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.playwright = sync_playwright().start()
        options = {"headless": True, "args": ["--no-sandbox", "--disable-dev-shm-usage"]}
        executable = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
        if executable:
            options["executable_path"] = executable
        try:
            cls.browser = cls.playwright.chromium.launch(**options)
        except BaseException:
            cls.playwright.stop()
            raise
        expect.set_options(timeout=3000)

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()

    def setUp(self):
        self.context = self.browser.new_context(bypass_csp=False)
        self.addCleanup(self.context.close)
        self.page = self.context.new_page()
        self.page.set_content(HTML)

    def test_dynamic_eval_stays_blocked(self):
        # Run from a page event handler, not CDP evaluate (which can bypass CSP).
        self.page.locator("#evalProbe").click()
        expect(self.page.locator("#evalResult")).to_have_text("EvalError")

    def test_language_and_heading_wait_without_eval(self):
        for language, direction, heading in (("de", "ltr", "Uebersicht"),
                                               ("fa", "rtl", "Persian overview"),
                                               ("en", "ltr", "Overview")):
            self.page.locator("#language").select_option(language)
            expect(self.page.locator("html")).to_have_attribute("lang", language)
            expect(self.page.locator("html")).to_have_attribute("dir", direction)
            expect(self.page.locator("h1")).to_have_text(heading)

    def test_async_error_text_without_eval(self):
        expect(self.page.locator("#loginError")).to_have_text("")
        self.page.locator("#login").click()
        expect(self.page.locator("#loginError")).not_to_have_text("")

    def test_async_lane_count_without_eval(self):
        expect(self.page.locator(".release-lane")).to_have_count(4)
        self.page.locator("#filter").click()
        expect(self.page.locator(".release-lane")).to_have_count(1)

    def test_dialog_closes_without_eval(self):
        self.page.locator("#openDialog").click()
        expect(self.page.locator("#dialog")).to_be_visible()
        self.page.locator("#closeDialog").click()
        expect(self.page.locator("#dialog")).not_to_be_visible()

    def test_wrong_language_is_not_reported_as_success(self):
        with self.assertRaises(AssertionError):
            expect(self.page.locator("html")).to_have_attribute("lang", "de", timeout=150)

    def test_ui_suite_has_no_eval_based_polling_or_csp_bypass(self):
        tree = ast.parse((ROOT / "tests/ui_test.py").read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Call):
                if isinstance(node.func, ast.Attribute):
                    self.assertNotEqual("wait_for_function", node.func.attr)
                for keyword in node.keywords:
                    if keyword.arg == "bypass_csp":
                        self.assertIsInstance(keyword.value, ast.Constant)
                        self.assertIs(keyword.value.value, False)

    def test_production_policy_is_not_weakened(self):
        policy_file = (ROOT / "repository-server/src/main/java/ir/graph/repo/server/security/RequestAuditFilter.java")
        production = policy_file.read_text(encoding="utf-8")
        self.assertIn("script-src 'self';", production)
        self.assertNotIn("'unsafe-eval'", production)
        self.assertNotIn("'unsafe-inline'", production)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(CspAssertionTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    report = {
        "suite": "csp-locator-assertions",
        "status": "passed" if result.wasSuccessful() else "failed",
        "testsRun": result.testsRun,
        "failures": len(result.failures),
        "errors": len(result.errors),
        "skipped": len(result.skipped),
        "scope": "Chromium set_content with a hash-authorized fixture script and enforced meta CSP",
        "springBootRuntimeTest": False,
        "nativeHttpNavigationTest": False,
    }
    destination = ROOT / "docs/qa/csp-assertions-report.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(0 if result.wasSuccessful() else 1)
