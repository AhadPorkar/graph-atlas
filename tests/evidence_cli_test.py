#!/usr/bin/env python3
"""Exercise the delivered standalone Java verifier using ephemeral, real Ed25519 keys."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(shutil.which('java') and shutil.which('javac'), 'Requires a full JDK 21+')
class EvidenceCliTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='atlas-evidence-cli-')
        cls.home = Path(cls.temp.name)
        src = cls.home / 'EvidenceFixture.java'
        src.write_text('''import ir.graph.repo.core.release.EvidenceEnvelope;
import ir.graph.repo.core.util.Json;
import java.security.*;
import java.nio.file.*;
public class EvidenceFixture {
 public static void main(String[] args) throws Exception {
  var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  var payload = Json.map("schema","graph-atlas.release/v1","id","cli-fixture","state","RELEASED");
  Files.writeString(Path.of(args[0]),Json.stringify(EvidenceEnvelope.sign(payload,keys)));
  System.out.println(EvidenceEnvelope.sha256(keys.getPublic().getEncoded()));
 }
}''')
        core = ROOT / 'repository-core/src/main/java/ir/graph/repo/core'
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(cls.home),str(src),
                        str(core/'util/Json.java'),str(core/'release/EvidenceEnvelope.java')],check=True,capture_output=True)
        cls.envelope = cls.home / 'evidence.json'
        result = subprocess.run(['java','-cp',str(cls.home),'EvidenceFixture',str(cls.envelope)],check=True,text=True,capture_output=True)
        cls.fingerprint = result.stdout.strip()
    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()
    def call(self, *args):
        return subprocess.run(['bash',str(ROOT/'scripts/verify-evidence.sh'),*map(str,args)],text=True,capture_output=True)
    def test_valid_signature_with_independently_supplied_identity(self):
        result = self.call(self.envelope, self.fingerprint)
        self.assertEqual(0, result.returncode, result.stderr)
        value = json.loads(result.stdout)
        self.assertTrue(value['signatureValid']); self.assertFalse(value['currentRevocationChecked']); self.assertFalse(value['artifactBytesChecked'])
    def test_replaced_trust_root_is_rejected(self):
        result = self.call(self.envelope, '0'*64)
        self.assertEqual(1, result.returncode)
    def test_payload_tampering_is_rejected(self):
        value = json.loads(self.envelope.read_text()); value['payload'] = 'e30='
        path = self.home/'tampered.json'; path.write_text(json.dumps(value))
        self.assertEqual(1, self.call(path, self.fingerprint).returncode)
    def test_missing_trust_argument_is_not_automatic_trust(self):
        self.assertEqual(2, self.call(self.envelope).returncode)

if __name__ == '__main__': unittest.main(verbosity=2)
