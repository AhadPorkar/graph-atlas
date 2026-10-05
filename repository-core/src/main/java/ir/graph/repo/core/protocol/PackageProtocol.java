package ir.graph.repo.core.protocol;

import ir.graph.repo.core.domain.RepositoryPrincipal;
import java.util.Map;
import java.util.Set;

/** Implement and register a Spring bean to add a package protocol. */
public interface PackageProtocol {
    Set<String> formats();
    void handle(ProtocolExchange exchange, Map<String, Object> repository, String path,
                RepositoryPrincipal principal) throws Exception;
}
