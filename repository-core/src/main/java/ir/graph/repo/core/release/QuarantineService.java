package ir.graph.repo.core.release;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.security.PermissionService;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Digest-level manual containment. This is NOT a vulnerability scanner. */
public final class QuarantineService {
    private final ContentStore store;
    private final PermissionService permissions;
    public QuarantineService(ContentStore store, PermissionService permissions) { this.store = store; this.permissions = permissions; }
    public Map<String, Object> list(RepositoryPrincipal p) {
        permissions.admin(p); return Json.map("items", store.all("holds").stream()
                .sorted(Comparator.comparing((Map<String, Object> v) -> Json.str(v, "updated", "")).reversed()).toList());
    }
    public Map<String, Object> save(RepositoryPrincipal p, Map<String, Object> input) throws IOException {
        permissions.admin(p); String sha = Json.str(input, "sha256", ""), reason = Json.str(input, "reason", "").strip();
        store.blobPath(sha);
        if (reason.isEmpty() || reason.length() > 2000) throw RepositoryException.bad("A quarantine reason is required (max 2000 characters)");
        if (!input.containsKey("active")) throw RepositoryException.bad("Explicit active state is required");
        boolean active = Json.bool(input, "active", true);
        synchronized (store) {
            var old = store.get("holds", sha); long revision = old == null ? 0 : Json.num(old, "revision", 0);
            if (Json.num(input, "expectedRevision", -1) != revision) throw new RepositoryException(409, "STALE_HOLD", "Quarantine changed; reload before updating");
            var events = old == null ? new ArrayList<>() : Json.array(old.get("events"));
            String now = Instant.now().toString();
            events.add(Json.map("actor", p.username(), "time", now, "active", active, "reason", reason));
            var record = Json.map("sha256", sha, "active", active, "reason", reason, "updated", now,
                    "updatedBy", p.username(), "revision", revision + 1, "events", events);
            store.put("holds", sha, record); return record;
        }
    }
}
