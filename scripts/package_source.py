#!/usr/bin/env python3
"""Create a clean source ZIP and SHA-256 manifest without compiling the application."""
from __future__ import annotations

import argparse
import hashlib
import os
import stat
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_DIRS = {'.git', '.build', 'target', 'dist', 'data', 'backups', 'node_modules',
                 '.venv', 'venv', '__pycache__', '.idea', '.vscode'}
EXCLUDED_SUFFIXES = {'.jar', '.class', '.pyc', '.pyo', '.log', '.zip', '.sha256',
                     '.tmp', '.pem', '.key', '.p12', '.pfx', '.ttf', '.otf', '.woff', '.woff2'}
EXCLUDED_NAMES = {'admin.password', 'evidence-key.json', '.DS_Store', 'Thumbs.db', '.npmrc', 'NuGet.Config',
                  'nuget.config', 'SOURCE-SHA256SUMS.txt'}
ARCHIVE_ROOT = 'graph-atlas'


def collect_files(output: Path) -> list[Path]:
    result = []
    for path in sorted(ROOT.rglob('*')):
        relative = path.relative_to(ROOT)
        if any(part in EXCLUDED_DIRS for part in relative.parts):
            continue
        if path.is_symlink():
            raise ValueError(f'Refusing to package a symlink: {relative}')
        if not path.is_file() or path.resolve() == output:
            continue
        if path.suffix.lower() in EXCLUDED_SUFFIXES or path.name in EXCLUDED_NAMES:
            continue
        if path.name.startswith('.env') and path.name != '.env.example':
            continue
        result.append(path)
    return result


def entry(name: str, executable: bool = False) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(f'{ARCHIVE_ROOT}/{name}', date_time=(2026, 10, 5, 0, 0, 0))
    info.create_system = 3
    info.external_attr = ((stat.S_IFREG | (0o755 if executable else 0o644)) << 16)
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/graph-atlas-source.zip')
    args = parser.parse_args()
    output = args.output.expanduser().resolve()
    if output.suffix.lower() != '.zip':
        parser.error('--output must end with .zip')
    subprocess.run([sys.executable, str(ROOT / 'scripts/check_repository.py')], check=True, cwd=ROOT)
    output.parent.mkdir(parents=True, exist_ok=True)
    paths = collect_files(output)
    manifest_lines = ['# SHA-256 of packaged source files; this manifest excludes itself.']
    staging = output.with_suffix('.zip.tmp')
    try:
        with zipfile.ZipFile(staging, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in paths:
                name = path.relative_to(ROOT).as_posix()
                data = path.read_bytes()
                manifest_lines.append(f'{hashlib.sha256(data).hexdigest()}  {name}')
                archive.writestr(entry(name, path.suffix == '.sh'), data)
            manifest = '\n'.join(manifest_lines) + '\n'
            archive.writestr(entry('SOURCE-SHA256SUMS.txt'), manifest.encode('utf-8'))
        with zipfile.ZipFile(staging) as archive:
            bad = archive.testzip()
            if bad:
                raise ValueError(f'Archive integrity failure: {bad}')
        os.replace(staging, output)
        digest = hashlib.sha256(output.read_bytes()).hexdigest()
        Path(str(output) + '.sha256').write_text(f'{digest}  {output.name}\n', encoding='ascii')
        print(f'SOURCE_FILES={len(paths)}')
        print(f'ZIP={output}')
        print(f'BYTES={output.stat().st_size}')
        print(f'SHA256={digest}')
        return 0
    finally:
        staging.unlink(missing_ok=True)


if __name__ == '__main__':
    raise SystemExit(main())
