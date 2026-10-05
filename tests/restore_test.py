#!/usr/bin/env python3
"""Offline restore tests; these do not exercise Spring Boot or a live server."""
from __future__ import annotations
import importlib.util
import stat
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('graph_restore', ROOT / 'scripts' / 'restore.py')
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)


class RestoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='graph-restore-test-')
        self.root = Path(self.temp.name)
        self.archive = self.root / 'backup.zip'
        self.destination = self.root / 'restored'

    def tearDown(self):
        self.temp.cleanup()

    def archive_entries(self, entries):
        with zipfile.ZipFile(self.archive, 'w') as out:
            for path, value in entries:
                out.writestr(path, value)

    def reject(self, entries):
        self.archive_entries(entries)
        with self.assertRaises((ValueError, FileExistsError)):
            module.restore(self.archive, self.destination)
        self.assertFalse(self.destination.exists())
        self.assertEqual([], list(self.root.glob('.graph-restore-*')))

    def test_valid_backup_excludes_lock_and_bootstrap_password(self):
        self.archive_entries([('metadata.jsonl', b'{}\n'), ('blobs/aa/blob', b'package'),
                              ('.lock', b'old lock'), ('admin.password', b'old password')])
        module.restore(self.archive, self.destination)
        self.assertEqual(b'package', (self.destination/'blobs/aa/blob').read_bytes())
        self.assertFalse((self.destination/'.lock').exists())
        self.assertFalse((self.destination/'admin.password').exists())
        self.assertTrue((self.destination/'metadata.jsonl').is_file())

    def test_signing_identity_is_preserved_and_private(self):
        # Synthetic material checks file preservation, not cryptographic validity.
        payload = b'{"syntheticFixture":true}'
        self.archive_entries([('metadata.jsonl', b'{}\n'), ('evidence-key.json', payload)])
        module.restore(self.archive, self.destination)
        target = self.destination / 'evidence-key.json'
        self.assertEqual(payload, target.read_bytes())
        import os
        if os.name == 'posix': self.assertEqual(0o600, stat.S_IMODE(target.stat().st_mode))

    def test_existing_destination_is_never_overwritten(self):
        self.archive_entries([('metadata.jsonl', b'{}\n')])
        self.destination.mkdir()
        sentinel = self.destination/'sentinel'; sentinel.write_text('keep')
        with self.assertRaises(ValueError):
            module.restore(self.archive, self.destination)
        self.assertEqual('keep', sentinel.read_text())

    def test_parent_traversal(self):
        self.reject([('metadata.jsonl', b'{}\n'), ('../escape', b'bad')])
        self.assertFalse((self.root/'escape').exists())

    def test_absolute_path(self):
        self.reject([('metadata.jsonl', b'{}\n'), ('/absolute/path', b'bad')])

    def test_windows_paths(self):
        for path in ('C:/escape', 'dir\\escape', '..\\escape', 'file:stream'):
            with self.subTest(path=path):
                self.reject([('metadata.jsonl', b'{}\n'), (path, b'bad')])

    def test_symbolic_links(self):
        link = zipfile.ZipInfo('link')
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.reject([('metadata.jsonl', b'{}\n'), (link, b'../escape')])

    def test_duplicate_entries(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            self.reject([('metadata.jsonl', b'{}\n'), ('metadata.jsonl', b'{}\n')])

    def test_normalized_duplicate_entries(self):
        self.reject([('metadata.jsonl', b'{}\n'), ('a/file', b'one'), ('a/./file', b'two')])

    def test_missing_metadata(self):
        self.reject([('blobs/a/file', b'package')])

    def test_metadata_directory_is_not_valid(self):
        self.reject([('metadata.jsonl/', b'')])

    def test_corrupt_archive(self):
        self.archive.write_bytes(b'not a zip file')
        with self.assertRaises(zipfile.BadZipFile):
            module.restore(self.archive, self.destination)
        self.assertFalse(self.destination.exists())
        self.assertEqual([], list(self.root.glob('.graph-restore-*')))


if __name__ == '__main__':
    unittest.main(verbosity=2)
