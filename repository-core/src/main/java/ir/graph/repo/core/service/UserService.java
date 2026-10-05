package ir.graph.repo.core.service;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.security.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public final class UserService {
    private final ContentStore store;
    private final IdentityService identity;
    private final PermissionService permissions;
    public UserService(ContentStore store, IdentityService identity, PermissionService permissions) {
        this.store = store; this.identity = identity; this.permissions = permissions;
    }
    public Map<String, Object> list(RepositoryPrincipal p) {
        permissions.admin(p);
        return Json.map("items", store.all("users").stream().map(IdentityService::publicUser)
                .sorted(Comparator.comparing(u -> (String) u.get("username"))).toList());
    }
    public Map<String, Object> save(RepositoryPrincipal p, String name, Map<String, Object> data, boolean create) throws IOException {
        permissions.admin(p);
        String username = name == null ? Json.str(data, "username", "") : name;
        validateUsername(username);
        synchronized (store) {
            var old = store.get("users", username);
            if (create && old != null) throw new RepositoryException(409, "EXISTS", "User already exists");
            if (!create && old == null) throw RepositoryException.missing();
            boolean admin = Json.bool(data, "admin", old != null && Boolean.TRUE.equals(old.get("admin")));
            boolean disabled = Json.bool(data, "disabled", old != null && Boolean.TRUE.equals(old.get("disabled")));
            lastAdmin(old, !admin || disabled);
            String password = Json.str(data, "password", "");
            if (old == null || !password.isEmpty()) Passwords.validate(password);
            var grants = Json.object(data.getOrDefault("grants", old == null ? Json.map() : old.get("grants")));
            validateGrants(grants);
            var user = Json.map("username", username, "admin", admin, "disabled", disabled, "grants", grants,
                    "created", old == null ? Instant.now().toString() : old.get("created"),
                    "password", password.isEmpty() ? old.get("password") : Passwords.hash(password),
                    "sessionEpoch", old == null || !password.isEmpty() || disabled ? Passwords.randomToken() : Json.str(old, "sessionEpoch", ""));
            var operations = new ArrayList<Map<String, Object>>();
            operations.add(ContentStore.set("users", username, user));
            if (!password.isEmpty() || disabled) revoke(username, operations);
            store.transact(operations);
            return IdentityService.publicUser(user);
        }
    }
    public Map<String, Object> delete(RepositoryPrincipal p, String username, String confirm) throws IOException {
        permissions.admin(p); validateUsername(username);
        if (!username.equals(confirm)) throw RepositoryException.bad("Confirm the exact username");
        synchronized (store) {
            var user = store.get("users", username); if (user == null) throw RepositoryException.missing();
            lastAdmin(user, true);
            var operations = new ArrayList<Map<String, Object>>();
            operations.add(ContentStore.del("users", username)); revoke(username, operations); store.transact(operations);
        }
        return Json.map("ok", true);
    }
    public Map<String, Object> changePassword(RepositoryPrincipal p, String oldPassword, String password) throws IOException {
        permissions.require(p); Passwords.validate(password);
        synchronized (store) {
            var user = store.get("users", p.username());
            if (user == null || !Passwords.verify(oldPassword, (String) user.get("password")))
                throw new RepositoryException(403, "PASSWORD", "Current password is incorrect");
            user.put("password", Passwords.hash(password));
            var operations = new ArrayList<Map<String, Object>>();
            operations.add(ContentStore.set("users", p.username(), user)); revoke(p.username(), operations); store.transact(operations);
        }
        return Json.map("ok", true, "loginRequired", true, "tokensRevoked", true);
    }
    public Map<String, Object> tokens(RepositoryPrincipal p) {
        permissions.require(p);
        return Json.map("items", store.all("tokens").stream().filter(t -> p.username().equals(t.get("username"))).toList());
    }
    public Map<String, Object> createToken(RepositoryPrincipal p, Map<String, Object> data) throws IOException {
        permissions.require(p);
        return identity.createToken(p.username(), Json.str(data, "label", ""), Json.str(data, "expires", ""));
    }
    public Map<String, Object> deleteToken(RepositoryPrincipal p, String id) throws IOException {
        permissions.require(p);
        synchronized (store) {
            var token = store.get("tokens", id); if (token == null) throw RepositoryException.missing();
            if (!p.admin() && !p.username().equals(token.get("username")))
                throw new RepositoryException(403, "FORBIDDEN", "Token belongs to another account");
            store.delete("tokens", id);
        }
        return Json.map("ok", true);
    }
    private void revoke(String username, List<Map<String, Object>> operations) {
        for (var token : store.all("tokens"))
            if (username.equals(token.get("username"))) operations.add(ContentStore.del("tokens", (String) token.get("id")));
    }
    private void lastAdmin(Map<String, Object> old, boolean removing) {
        if (old != null && removing && Boolean.TRUE.equals(old.get("admin")) && !Boolean.TRUE.equals(old.get("disabled"))
                && store.all("users").stream().filter(u -> Boolean.TRUE.equals(u.get("admin")) && !Boolean.TRUE.equals(u.get("disabled"))).count() <= 1)
            throw new RepositoryException(409, "LAST_ADMIN", "Cannot remove or disable the last administrator");
    }
    private void validateUsername(String username) {
        if (!username.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) throw RepositoryException.bad("Invalid username");
    }
    private void validateGrants(Map<String, Object> grants) {
        for (var e : grants.entrySet()) {
            if (!e.getKey().equals("*") && !e.getKey().matches("[a-z0-9][a-z0-9._-]{0,99}")) throw RepositoryException.bad("Invalid repository grant");
            for (Object action : Json.array(e.getValue()))
                if (!Set.of("read", "write", "delete", "approve", "*").contains(action)) throw RepositoryException.bad("Invalid permission");
        }
    }
}
