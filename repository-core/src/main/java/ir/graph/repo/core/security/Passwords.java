package ir.graph.repo.core.security;

import ir.graph.repo.core.domain.RepositoryException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/** Retains compatibility with the v0.2 password format. Raw passwords are never stored. */
public final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private Passwords() { }
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static void validate(String password) {
        if (password == null || password.length() < 12 || password.length() > 256)
            throw RepositoryException.bad("Passwords must contain 12 to 256 characters");
    }
    public static String hash(String password) {
        validate(password);
        byte[] salt = new byte[24];
        RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
    public static boolean verify(String password, String encoded) {
        if (password == null || password.length() > 256 || encoded == null) return false;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[2]), iterations),
                    Base64.getDecoder().decode(parts[3]));
        } catch (IllegalArgumentException e) { return false; }
    }
    public static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
