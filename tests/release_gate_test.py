#!/usr/bin/env python3
"""The CI client against a local synthetic server, NOT Spring Boot."""
import copy
import importlib.util
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

spec = importlib.util.spec_from_file_location('atlas_gate', Path(__file__).resolve().parents[1] / 'scripts/release_gate.py')
gate = importlib.util.module_from_spec(spec); spec.loader.exec_module(gate)
RID = '5e5f5020-5cfe-4b69-821e-8a53097eb16a'
GOOD = {'releaseId': RID, 'state': 'RELEASED', 'deep': True, 'allowed': True, 'problems': [], 'signatureValid': True, 'integrityOk': True}

class Handler(BaseHTTPRequestHandler):
    body = GOOD
    code = 200
    location = None
    observed = []
    def log_message(self, *args): pass
    def do_GET(self):
        type(self).observed.append((self.path, self.headers.get('Authorization')))
        self.send_response(type(self).code)
        self.send_header('Content-Type', 'application/json')
        if type(self).location: self.send_header('Location', type(self).location)
        self.end_headers(); self.wfile.write(json.dumps(type(self).body).encode())

class GateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True); cls.thread.start()
        cls.base = f'http://127.0.0.1:{cls.server.server_port}'
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown(); cls.server.server_close()
    def setUp(self):
        Handler.body = copy.deepcopy(GOOD); Handler.code = 200; Handler.location = None; Handler.observed = []
    def test_https_url_and_canonical_id(self):
        self.assertEqual(gate.endpoint('https://atlas.example.test/', RID), f'https://atlas.example.test/api/releases/{RID}/gate?deep=true')
    def test_remote_http_denied_even_with_local_override(self):
        with self.assertRaises(ValueError): gate.endpoint('http://example.test', RID, True)
    def test_local_http_requires_explicit_override(self):
        with self.assertRaises(ValueError): gate.endpoint(self.base, RID)
    def test_ambiguous_origins_denied(self):
        for base in ['https://user:pass@example.test', 'https://example.test/path', 'https://example.test/?q=x', 'https://example.test/#x']:
            with self.subTest(base=base), self.assertRaises(ValueError): gate.endpoint(base, RID)
    def test_release_path_injection_denied(self):
        with self.assertRaises(ValueError): gate.endpoint('https://example.test', '../admin')
    def test_real_http_allow_and_token_header(self):
        allowed, _ = gate.run(self.base, RID, 'fixture-only', allow_loopback=True)
        self.assertTrue(allowed)
        self.assertEqual(Handler.observed, [(f'/api/releases/{RID}/gate?deep=true', 'Bearer fixture-only')])
    def test_real_http_denial(self):
        Handler.body.update(allowed=False, state='REVOKED')
        self.assertFalse(gate.run(self.base, RID, 'fixture-only', allow_loopback=True)[0])
    def test_redirects_are_not_followed(self):
        Handler.code = 302; Handler.location = self.base + '/would-leak'
        with self.assertRaises(Exception): gate.run(self.base, RID, 'fixture-only', allow_loopback=True)
        self.assertEqual(len(Handler.observed), 1)
    def test_unauthorized_is_not_a_pass(self):
        Handler.code = 401
        with self.assertRaises(Exception): gate.run(self.base, RID, 'fixture-only', allow_loopback=True)
    def test_wrong_release_is_denied(self):
        value = dict(GOOD, releaseId='other')
        with self.assertRaises(ValueError): gate.evaluate(value, RID)
    def test_string_booleans_are_denied(self):
        with self.assertRaises(ValueError): gate.evaluate(dict(GOOD, allowed='true'), RID)
    def test_shallow_response_is_denied(self):
        with self.assertRaises(ValueError): gate.evaluate(dict(GOOD, deep=False), RID)
    def test_inconsistent_allow_is_denied(self):
        for key, value in [('problems', ['bad']), ('signatureValid', False), ('integrityOk', False), ('state', 'DRAFT')]:
            with self.subTest(key=key), self.assertRaises(ValueError): gate.evaluate(dict(GOOD, **{key: value}), RID)
    def test_missing_and_multiline_tokens_denied(self):
        for token in ['', 'x\r\nInjected: value']:
            with self.assertRaises(ValueError): gate.run(self.base, RID, token, allow_loopback=True)

if __name__ == '__main__': unittest.main(verbosity=2)
