package ir.graph.repo.core.domain;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;

/** A request identity. The security stamp invalidates sessions after a password change. */
public record RepositoryPrincipal(String username, boolean admin, Map<String, Object> grants,
                                  String securityStamp) implements Principal, Serializable {
    public RepositoryPrincipal {
        Objects.requireNonNull(username);
        Map<String, Object> copy = new LinkedHashMap<>();
        grants.forEach((key, value) -> copy.put(key, List.copyOf((List<?>) value)));
        grants = Collections.unmodifiableMap(copy);
    }
    @Override public String getName() { return username; }
    @Override public String toString() { return "RepositoryPrincipal[" + username + "]"; }
}
