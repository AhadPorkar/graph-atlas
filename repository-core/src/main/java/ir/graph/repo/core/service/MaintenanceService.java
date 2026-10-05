package ir.graph.repo.core.service;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.security.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class MaintenanceService {
    private final ContentStore store;
    private final PermissionService permissions;
    public MaintenanceService(ContentStore store, PermissionService permissions) { this.store = store; this.permissions = permissions; }
    public Map<String, Object> execute(RepositoryPrincipal p, Map<String, Object> data) throws IOException {
        permissions.admin(p);
        if (!Json.str(data, "confirm", "").equals("MAINTENANCE")) throw RepositoryException.bad("Explicit maintenance confirmation is required");
        return switch (Json.str(data, "action", "")) {
            case "compact" -> { store.compact(); yield Json.map("ok", true); }
            case "gc" -> store.garbageCollect(Json.bool(data, "dryRun", true));
            case "verify" -> verify(store);
            default -> throw RepositoryException.bad("Unknown maintenance action");
        };
    }
    public static Map<String, Object> verify(ContentStore store) throws IOException {
        Set<String> checked = new HashSet<>(); List<Object> errors = new ArrayList<>();
        for (var asset : store.retainedAssets()) {
            String digest = (String) asset.get("sha256"); if (!checked.add(digest)) continue;
            Path file = store.blobPath(digest);
            if (!Files.exists(file)) { errors.add(Json.map("sha256", digest, "error", "missing")); continue; }
            var sha = ContentStore.hash("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) sha.update(buffer, 0, count);
            }
            if (!Passwords.constantTimeEquals(digest, HexFormat.of().formatHex(sha.digest()))) errors.add(Json.map("sha256", digest, "error", "checksum"));
        }
        return Json.map("checked", checked.size(), "errors", errors, "ok", errors.isEmpty());
    }
}
