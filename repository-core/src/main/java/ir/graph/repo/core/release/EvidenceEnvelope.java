package ir.graph.repo.core.release;

import ir.graph.repo.core.util.Json;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Signs the exact UTF-8 payload bytes. This is an Atlas envelope, not DSSE/SLSA. */
public final class EvidenceEnvelope {
    public static final String TYPE = "application/vnd.graph-atlas.release.v1+json";
    private EvidenceEnvelope() { }
    public static String canonical(Object value) { return Json.stringify(ordered(value)); }
    private static Object ordered(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put((String) key, ordered(item))); return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(EvidenceEnvelope::ordered).toList();
        return value;
    }
    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String digest(Object value) { return sha256(canonical(value).getBytes(StandardCharsets.UTF_8)); }
    public static Map<String, Object> sign(Map<String, Object> payload, KeyPair key) throws GeneralSecurityException {
        byte[] bytes = canonical(payload).getBytes(StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance("Ed25519"); signature.initSign(key.getPrivate()); signature.update(bytes);
        return Json.map("schema", "graph-atlas.evidence/v1", "payloadType", TYPE, "algorithm", "Ed25519",
                "keyId", sha256(key.getPublic().getEncoded()),
                "publicKey", Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),
                "payload", Base64.getEncoder().encodeToString(bytes),
                "signature", Base64.getEncoder().encodeToString(signature.sign()));
    }
    /** The expected fingerprint MUST come from an independent trusted channel, not this envelope. */
    public static Map<String, Object> verify(Map<String, Object> envelope, String expectedFingerprint) throws GeneralSecurityException {
        try {
            if (expectedFingerprint == null || !expectedFingerprint.matches("[a-f0-9]{64}"))
                throw new GeneralSecurityException("A trusted SHA-256 public-key fingerprint is required");
            if (!"graph-atlas.evidence/v1".equals(envelope.get("schema")) || !TYPE.equals(envelope.get("payloadType"))
                    || !"Ed25519".equals(envelope.get("algorithm"))) throw new GeneralSecurityException("Unknown envelope schema");
            byte[] publicBytes = Base64.getDecoder().decode((String) envelope.get("publicKey"));
            String fingerprint = sha256(publicBytes);
            if (!MessageDigest.isEqual(fingerprint.getBytes(StandardCharsets.US_ASCII), expectedFingerprint.getBytes(StandardCharsets.US_ASCII))
                    || !fingerprint.equals(envelope.get("keyId"))) throw new GeneralSecurityException("Untrusted signing key");
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicBytes));
            Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key);
            byte[] payload = Base64.getDecoder().decode((String) envelope.get("payload")); verifier.update(payload);
            if (!verifier.verify(Base64.getDecoder().decode((String) envelope.get("signature"))))
                throw new GeneralSecurityException("Signature does not match the payload");
            Map<String, Object> decoded = Json.obj(new String(payload, StandardCharsets.UTF_8));
            if (!"graph-atlas.release/v1".equals(decoded.get("schema"))) throw new GeneralSecurityException("Unknown payload schema");
            return decoded;
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
            throw new GeneralSecurityException("Malformed evidence envelope", e);
        }
    }
}
