#!/usr/bin/env python3
"""Dependency-free source, translation and bilingual documentation checks.

This does not compile Spring Boot or act as a complete secret scanner.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {'.git', '.build', 'target', 'dist', 'data', 'backups', 'node_modules',
             '.venv', 'venv', '__pycache__', '.idea', '.vscode'}
PLACEHOLDER = re.compile(r'\{([A-Za-z_][A-Za-z_0-9]*)\}')
LINK = re.compile(r'!?\[[^\]]*\]\(([^\s)]+)(?:\s+"[^"]*")?\)')


def source_files() -> list[Path]:
    return sorted(p for p in ROOT.rglob('*') if p.is_file()
                  and not any(part in SKIP_DIRS for part in p.relative_to(ROOT).parts))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', action='store_true', help='Also verify the packaged SHA-256 manifest.')
    args = parser.parse_args()
    errors: list[str] = []
    checks = 0

    def check(condition: bool, message: str) -> None:
        nonlocal checks
        checks += 1
        if not condition:
            errors.append(message)

    required = ['README.md', 'README.de.md', 'LICENSE', 'pom.xml', 'package.json',
                'repository-core/pom.xml', 'repository-server/pom.xml',
                'CONTRIBUTING.md', 'CONTRIBUTING.de.md', 'SECURITY.md', 'SECURITY.de.md',
                'CODE_OF_CONDUCT.md', 'CODE_OF_CONDUCT.de.md', 'CHANGELOG.md', 'CHANGELOG.de.md',
                'THIRD-PARTY-NOTICES.md', 'THIRD-PARTY-NOTICES.de.md',
                '.gitignore', '.gitattributes', '.editorconfig', '.env.example',
                '.github/workflows/verify.yml', '.github/workflows/package.yml',
                '.github/ISSUE_TEMPLATE/bug-en.yml', '.github/ISSUE_TEMPLATE/bug-de.yml',
                'scripts/build.sh', 'scripts/build.ps1', 'scripts/package_source.py',
                'tests/ui_test.py', 'tests/frontend/i18n.test.mjs']
    for name in required:
        check((ROOT / name).is_file(), f'Missing required source: {name}')

    files = source_files()
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        check(not path.is_symlink(), f'Symlink must not be distributed: {rel}')
        if path.suffix == '.json':
            try:
                json.loads(path.read_text(encoding='utf-8'))
                check(True, rel)
            except (ValueError, UnicodeError) as exc:
                check(False, f'Invalid JSON {rel}: {exc}')
        if path.name == 'pom.xml':
            try:
                ET.parse(path)
                check(True, rel)
            except ET.ParseError as exc:
                check(False, f'Invalid Maven XML {rel}: {exc}')
        if path.suffix.lower() in {'.ttf', '.otf', '.woff', '.woff2'}:
            check(False, f'Unexpected font binary: {rel}')

    static = ROOT / 'repository-server/src/main/resources/static'
    catalogs = {lang: json.loads((static / f'locales/{lang}.json').read_text(encoding='utf-8'))
                for lang in ('en', 'de', 'fa')}
    english = catalogs['en']
    for lang, values in catalogs.items():
        check(values.keys() == english.keys(), f'Catalog keys differ for {lang}')
        for key, value in values.items():
            check(isinstance(value, str) and bool(value.strip()), f'Empty translation: {lang}:{key}')
            check(set(PLACEHOLDER.findall(value)) == set(PLACEHOLDER.findall(english[key])),
                  f'Placeholder mismatch: {lang}:{key}')
    html = (static / 'index.html').read_text(encoding='utf-8')
    check(bool(re.search(r'<html[^>]+lang="en"[^>]+dir="ltr"', html)), 'Initial document must be English/LTR.')
    i18n = (static / 'i18n.js').read_text(encoding='utf-8')
    check("DEFAULT_LANGUAGE = 'en'" in i18n, 'English must be the explicit frontend default.')
    check('navigator.language' not in i18n, 'Browser language must not replace English as the default.')

    resources = ROOT / 'repository-server/src/main/resources/i18n'
    property_sets = []
    for suffix in ('', '_de', '_fa'):
        lines = (resources / f'messages{suffix}.properties').read_text(encoding='utf-8').splitlines()
        keys = [line.split('=', 1)[0].strip() for line in lines if line.strip() and not line.startswith('#')]
        check(len(keys) == len(set(keys)), f'Duplicate message keys in messages{suffix}.properties')
        property_sets.append(set(keys))
    check(property_sets[0] == property_sets[1] == property_sets[2], 'Backend message keys differ.')

    en_docs = {p.name for p in (ROOT / 'docs/en').glob('*.md')}
    de_docs = {p.name for p in (ROOT / 'docs/de').glob('*.md')}
    check(bool(en_docs) and en_docs == de_docs, 'English/German documentation sets must match.')
    link_count = 0
    for path in files:
        if path.suffix != '.md':
            continue
        text = path.read_text(encoding='utf-8')
        for target in LINK.findall(text):
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            link_count += 1
            resolved = (path.parent / unquote(parsed.path)).resolve()
            check(resolved.is_relative_to(ROOT), f'Document link escapes source: {path.name}: {target}')
            check(resolved.exists(), f'Broken local link: {path.relative_to(ROOT)} -> {target}')

    metadata = json.loads((ROOT / 'project.lock.json').read_text(encoding='utf-8'))
    package = json.loads((ROOT / 'package.json').read_text(encoding='utf-8'))
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(ROOT / 'pom.xml').getroot()
    check(pom.findtext('m:version', namespaces=ns) == metadata['projectVersion'], 'Project versions differ.')
    check(pom.findtext('m:parent/m:version', namespaces=ns) == metadata['springBootVersion'], 'Spring versions differ.')
    check(package.get('private') is True, 'Frontend tooling package must not be published to npm.')
    check(not package.get('dependencies'), 'Unexpected frontend runtime dependency.')
    check(metadata['defaultLanguage'] == 'en' and metadata['supportedLanguages'] == ['en', 'de', 'fa'],
          'Project language metadata differs.')
    check(metadata['uiCatalogKeysPerLanguage'] == len(english), 'Catalog size metadata is stale.')

    # Focused safety checks, not a replacement for repository secret scanning.
    check(not (ROOT / '.env').exists(), 'A real .env is present; remove it before publishing.')
    private_key = re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')
    for path in files:
        if path.suffix in {'.java', '.js', '.mjs', '.json', '.yaml', '.yml', '.md', '.py', '.sh', '.ps1'}:
            try:
                check(not private_key.search(path.read_text(encoding='utf-8')),
                      f'Possible private key in {path.relative_to(ROOT)}')
            except UnicodeError:
                pass
    if args.manifest:
        manifest = ROOT / 'SOURCE-SHA256SUMS.txt'
        check(manifest.is_file(), 'Source manifest is missing.')
        if manifest.is_file():
            for line in manifest.read_text(encoding='utf-8').splitlines():
                if not line or line.startswith('#'):
                    continue
                digest, name = line.split('  ', 1)
                path = (ROOT / name).resolve()
                safe = path.is_relative_to(ROOT) and path.is_file()
                check(safe and hashlib.sha256(path.read_bytes()).hexdigest() == digest,
                      f'Manifest mismatch: {name}')

    report = {'checks': checks, 'failures': len(errors), 'sourceFiles': len(files),
              'uiKeysPerLanguage': len(english), 'bilingualGuidePairs': len(en_docs),
              'localDocumentationLinks': link_count, 'springBootCompiled': False}
    print(json.dumps(report, indent=2))
    for error in errors:
        print(f'ERROR: {error}', file=sys.stderr)
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
