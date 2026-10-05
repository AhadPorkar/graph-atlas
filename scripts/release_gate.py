#!/usr/bin/env python3
"""Fail-closed, point-in-time Graph Atlas release check. Python 3.10+, standard library.

Not a vulnerability scanner, download verifier or ongoing authorization. Follow with
pinned-asset downloads, hash verification and ordinary deployment controls.
"""
from __future__ import annotations
import argparse
import ipaddress
import json
import os
import sys
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

MAX_RESPONSE = 2 * 1024 * 1024

class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def endpoint(base: str, release: str, allow_loopback: bool = False) -> str:
    parsed = urlsplit(base)
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise ValueError('Base URL must be an origin without credentials, path, query or fragment.')
    if not parsed.hostname or parsed.port == 0:
        raise ValueError('A valid host and port are required.')
    try:
        local = ipaddress.ip_address(parsed.hostname).is_loopback
    except ValueError:
        local = parsed.hostname == 'localhost'
    if parsed.scheme != 'https' and not (parsed.scheme == 'http' and allow_loopback and local):
        raise ValueError('HTTPS is required; local HTTP requires --allow-loopback-http.')
    normalized = str(uuid.UUID(release))
    return base.rstrip('/') + '/api/releases/' + normalized + '/gate?deep=true'

def evaluate(value: object, release: str) -> bool:
    if not isinstance(value, dict) or value.get('releaseId') != str(uuid.UUID(release)):
        raise ValueError('Gate returned the wrong release identifier.')
    if type(value.get('allowed')) is not bool or value.get('deep') is not True:
        raise ValueError('A boolean decision and a deep verification result are required.')
    if value['allowed'] and (value.get('state') != 'RELEASED' or value.get('problems') != [] or value.get('signatureValid') is not True or value.get('integrityOk') is not True):
        raise ValueError('Gate returned an inconsistent allow decision.')
    return value['allowed']

def run(base: str, release: str, token: str, *, timeout: float = 60, allow_loopback: bool = False):
    if not token or any(c in token for c in '\r\n'):
        raise ValueError('GR_TOKEN must contain a valid nonempty API token.')
    if timeout <= 0:
        raise ValueError('Timeout must be positive.')
    url = endpoint(base, release, allow_loopback)
    request = Request(url, headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/json'})
    with build_opener(NoRedirects()).open(request, timeout=timeout) as response:
        if response.status != 200:
            raise ValueError('Gate returned a non-200 response.')
        content_type = response.headers.get_content_type()
        if content_type != 'application/json':
            raise ValueError('Gate response must be application/json.')
        raw = response.read(MAX_RESPONSE + 1)
        if len(raw) > MAX_RESPONSE:
            raise ValueError('Gate response exceeds the client safety limit.')
        value = json.loads(raw)
    return evaluate(value, release), value

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--timeout', type=float, default=60)
    parser.add_argument('--allow-loopback-http', action='store_true')
    args = parser.parse_args()
    try:
        allowed, value = run(args.base_url, args.release, os.environ.get('GR_TOKEN', ''),
                             timeout=args.timeout, allow_loopback=args.allow_loopback_http)
        print(json.dumps(value, ensure_ascii=False, indent=2))
        return 0 if allowed else 1
    except (ValueError, OSError, URLError, HTTPError) as exc:
        # Do not print server bodies, supplied URLs or tokens into CI logs.
        kind = 'HTTP ' + str(exc.code) if isinstance(exc, HTTPError) else type(exc).__name__
        print('Release gate could not establish approval (' + kind + ').', file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
