#!/usr/bin/env python3
"""Restore a local backup to a NEW directory. Run only while all repository processes are stopped."""
from __future__ import annotations
import argparse, os, shutil, stat, tempfile, zipfile
from pathlib import Path, PurePosixPath

def restore(archive: Path, destination: Path) -> None:
    archive = archive.resolve(strict=True)
    destination = destination.absolute()
    if destination.exists():
        raise ValueError('The destination must not exist. Never overwrite a live data directory.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.graph-restore-', dir=destination.parent))
    try:
        with zipfile.ZipFile(archive) as source:
            names = set()
            for item in source.infolist():
                path = PurePosixPath(item.filename)
                if path.is_absolute() or not path.parts or '..' in path.parts or '\\' in item.filename or ':' in item.filename:
                    raise ValueError(f'Unsafe archive path: {item.filename!r}')
                if item.filename in names:
                    raise ValueError('Duplicate archive entries are not allowed')
                names.add(item.filename)
                if stat.S_ISLNK(item.external_attr >> 16):
                    raise ValueError('Symbolic links are not permitted in a backup')
                target = staging.joinpath(*path.parts)
                if item.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                if item.filename in ('.lock', 'admin.password'):
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(item) as reader, target.open('xb') as writer:
                    shutil.copyfileobj(reader, writer)
                if os.name == 'posix': target.chmod(0o600)
        if not (staging/'metadata.jsonl').is_file():
            raise ValueError('Not a repository backup: metadata.jsonl is missing')
        if os.name == 'posix':
            for root, dirs, _ in os.walk(staging):
                Path(root).chmod(0o700)
        if destination.exists(): raise ValueError('Destination appeared during restore; refusing to overwrite')
        staging.rename(destination)
        print(f'Restored to {destination}. Run the server --verify command before normal startup.')
    finally:
        if staging.exists(): shutil.rmtree(staging)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    restore(args.archive, args.destination)
