package ir.graph.repo.core.release;

import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Instance signing identity, stored atomically. Backups contain this sensitive private key. */
public final class EvidenceSigner {
    private final ContentStore store;
    private KeyPair keys;
    public EvidenceSigner(ContentStore store) { this.store = store; }
    private KeyPair keys() throws IOException {
        synchronized (store) {
        if (keys != null) return keys;
        Path file = store.home().resolve("evidence-key.json");
        if (Files.isSymbolicLink(file)) throw new IOException("Refusing a symbolic-link signing key");
        try {
            if (Files.exists(file)) {
                var data = Json.obj(Files.readString(file)); var factory = KeyFactory.getInstance("Ed25519");
                keys = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(Json.str(data, "public", "")))),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Json.str(data, "private", "")))));
                var probe = EvidenceEnvelope.sign(Json.map("schema", "graph-atlas.release/v1"), keys);
                EvidenceEnvelope.verify(probe, EvidenceEnvelope.sha256(keys.getPublic().getEncoded()));
                return keys;
            }
            if (store.all("releases").stream().anyMatch(value -> value.containsKey("evidence")))
                throw new IOException("Signing key missing: restore it from backup; automatic replacement is prohibited");
            KeyPair generated = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            String json = Json.stringify(Json.map("algorithm", "Ed25519", "public", Base64.getEncoder().encodeToString(generated.getPublic().getEncoded()),
                    "private", Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded())));
            Path temporary;
            try { temporary = Files.createTempFile(store.home(), ".evidence-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
            catch (UnsupportedOperationException e) { temporary = Files.createTempFile(store.home(), ".evidence-", ".tmp"); }
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
                    while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
                }
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file); }
                try (FileChannel directory = FileChannel.open(store.home(), StandardOpenOption.READ)) { directory.force(true); }
                catch (IOException | UnsupportedOperationException ignored) { /* Not supported on every file system. */ }
            } finally { Files.deleteIfExists(temporary); }
            keys = generated; return keys;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            keys = null; throw new IOException("Invalid signing identity; refusing to replace it", e);
        }
    }
    }
    public Map<String, Object> identity() throws IOException {
        PublicKey key = keys().getPublic();
        return Json.map("algorithm", "Ed25519", "keyId", EvidenceEnvelope.sha256(key.getEncoded()),
                "publicKey", Base64.getEncoder().encodeToString(key.getEncoded()));
    }
    public Map<String, Object> sign(Map<String, Object> payload) throws IOException {
        try { return EvidenceEnvelope.sign(payload, keys()); }
        catch (GeneralSecurityException e) { throw new IOException("Evidence signing failed", e); }
    }
}
