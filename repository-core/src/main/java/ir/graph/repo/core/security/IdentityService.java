package ir.graph.repo.core.security;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;

/** Credentials, opaque tokens and live identities; sessions belong to Spring Security, not this service. */
public final class IdentityService {
    private final ContentStore store;
    private final LoginThrottle throttle;
    private final String dummyHash = Passwords.hash(Passwords.randomToken());
    public IdentityService(ContentStore store, LoginThrottle throttle) { this.store = store; this.throttle = throttle; }
    public void bootstrap(String suppliedPassword) throws IOException {
        synchronized (store) {
            if (!store.all("users").isEmpty()) return;
            boolean generated = suppliedPassword == null || suppliedPassword.isBlank();
            String password = generated ? Passwords.randomToken() : suppliedPassword;
            Passwords.validate(password);
            // Write generated credentials before committing the first user: a crash cannot strand the admin.
            Path file = store.home().resolve("admin.password");
            if (generated) {
                if (Files.exists(file)) password = Files.readString(file).strip();
                else Files.writeString(file, password + "\n", StandardOpenOption.CREATE_NEW);
                Passwords.validate(password);
                try { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")); }
                catch (UnsupportedOperationException ignored) { }
            }
            store.put("users", "admin", Json.map("username", "admin", "password", Passwords.hash(password),
                    "admin", true, "disabled", false, "grants", Json.map(), "created", Instant.now().toString()));
        }
    }
    public RepositoryPrincipal find(String username) {
        var user = store.get("users", username);
        if (user == null || Boolean.TRUE.equals(user.get("disabled"))) return null;
        return new RepositoryPrincipal(username, Boolean.TRUE.equals(user.get("admin")),
                Json.object(user.get("grants")), ContentStore.digest(Json.str(user, "password", "") + "\n" + Json.str(user, "sessionEpoch", "")));
    }
    public RepositoryPrincipal authenticate(String username, String password, String remoteAddress) {
        if (username == null || password == null || username.length() > 100) return null;
        throttle.check(remoteAddress);
        // A token may be the password for Maven, pip and Docker clients.
        RepositoryPrincipal token = token(password);
        if (token != null && (username.equals(token.username()) || username.equals("token"))) {
            throttle.succeeded(remoteAddress); return token;
        }
        return authenticatePassword(username, password, remoteAddress);
    }
    public RepositoryPrincipal authenticatePassword(String username, String password, String remoteAddress) {
        if (username == null || password == null || username.length() > 100) return null;
        throttle.check(remoteAddress);
        var user = store.get("users", username);
        boolean valid = Passwords.verify(password.length() > 256 ? "invalid-password" : password,
                user == null ? dummyHash : Json.str(user, "password", ""));
        if (user == null || !valid || password.length() > 256 || Boolean.TRUE.equals(user.get("disabled"))) {
            throttle.failed(remoteAddress); return null;
        }
        throttle.succeeded(remoteAddress);
        return find(username);
    }
    public RepositoryPrincipal token(String raw) {
        if (raw == null || !raw.startsWith("gr_") || raw.length() > 512) return null;
        var token = store.get("tokens", ContentStore.digest(raw));
        if (token == null) return null;
        String expiry = Json.str(token, "expires", "");
        if (!expiry.isEmpty() && !Instant.parse(expiry).isAfter(Instant.now())) return null;
        return find((String) token.get("username"));
    }
    public Map<String, Object> createToken(String username, String label, String expires) throws IOException {
        if (label.length() > 200) throw RepositoryException.bad("Token label is too long");
        try {
            if (!expires.isEmpty() && !Instant.parse(expires).isAfter(Instant.now()))
                throw RepositoryException.bad("Token expiration must be in the future");
        } catch (DateTimeException e) { throw RepositoryException.bad("Token expiration must be a valid UTC timestamp"); }
        String raw = "gr_" + Passwords.randomToken(), id = ContentStore.digest(raw);
        var record = Json.map("id", id, "username", username, "label", label, "expires", expires, "created", Instant.now().toString());
        synchronized (store) {
            if (find(username) == null) throw new RepositoryException(401, "UNAUTHORIZED", "Token owner is not active");
            store.put("tokens", id, record);
        }
        record.put("token", raw);
        return record;
    }
    public boolean sessionValid(RepositoryPrincipal saved) {
        RepositoryPrincipal current = saved == null ? null : find(saved.username());
        return current != null && Passwords.constantTimeEquals(current.securityStamp(), saved.securityStamp());
    }
    public static Map<String, Object> publicUser(Map<String, Object> user) {
        var copy = new LinkedHashMap<>(user); copy.remove("password"); copy.remove("sessionEpoch"); return copy;
    }
}
