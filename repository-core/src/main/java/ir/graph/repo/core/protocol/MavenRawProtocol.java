package ir.graph.repo.core.protocol;

import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.service.RepositoryService;
import java.util.*;

public final class MavenRawProtocol implements PackageProtocol {
    private final RepositoryService repositories;
    public MavenRawProtocol(RepositoryService repositories) { this.repositories = repositories; }
    @Override public Set<String> formats() { return Set.of("maven", "raw"); }
    @Override public void handle(ProtocolExchange x, Map<String, Object> repo, String path, RepositoryPrincipal p) throws Exception {
        repositories.generic(x, repo, path, p);
    }
}
