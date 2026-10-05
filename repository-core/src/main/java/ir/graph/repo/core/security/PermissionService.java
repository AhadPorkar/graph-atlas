package ir.graph.repo.core.security;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.protocol.ProtocolExchange;
import java.util.*;

/** Authorization is rechecked by the core, including group members; the HTTP boundary is not trusted. */
public final class PermissionService {
    public RepositoryPrincipal require(RepositoryPrincipal principal) {
        if (principal == null) throw new RepositoryException(401, "UNAUTHORIZED", "Authentication is required");
        return principal;
    }
    public void admin(RepositoryPrincipal principal) {
        require(principal);
        if (!principal.admin()) throw new RepositoryException(403, "FORBIDDEN", "Administrator permission is required");
    }
    public boolean allowed(RepositoryPrincipal p, Map<String, Object> repo, String action) {
        if (Boolean.FALSE.equals(repo.get("online"))) return false;
        if (action.equals("read") && Boolean.TRUE.equals(repo.get("anonymous"))) return true;
        if (p == null) return false;
        if (p.admin()) return true;
        for (String name : List.of((String) repo.get("name"), "*")) {
            Object grants = p.grants().get(name);
            if (grants instanceof List<?> values && (values.contains(action) || values.contains("*"))) return true;
        }
        return false;
    }
    public void repository(RepositoryPrincipal p, Map<String, Object> repo, String action) {
        if (Boolean.FALSE.equals(repo.get("online"))) throw new RepositoryException(503, "REPOSITORY_OFFLINE", "Repository is offline");
        if (!allowed(p, repo, action)) {
            require(p);
            throw new RepositoryException(403, "FORBIDDEN", "Repository permission denied: " + action);
        }
    }
    public void unauthorized(ProtocolExchange x) {
        x.responseHeaders().set("WWW-Authenticate", "Basic realm=\"GraphAtlas\", charset=\"UTF-8\"");
        throw new RepositoryException(401, "UNAUTHORIZED", "Authentication is required");
    }
}
