package ir.graph.repo.core.service;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.protocol.*;
import ir.graph.repo.core.util.Json;
import java.util.*;

/** Spring MVC delegates a validated route here; this service owns package authorization and dispatch. */
public final class PackageGateway {
    private final RepositoryService repositories;
    private final Map<String, PackageProtocol> adapters = new LinkedHashMap<>();
    public PackageGateway(RepositoryService repositories, List<PackageProtocol> protocols) {
        this.repositories = repositories;
        for (var protocol : protocols) for (String format : protocol.formats())
            if (adapters.putIfAbsent(format, protocol) != null) throw new IllegalArgumentException("Duplicate protocol: " + format);
    }
    public void repository(ProtocolExchange x, String name, String path, RepositoryPrincipal p) throws Exception {
        if (!name.matches("[a-z0-9][a-z0-9._-]{0,99}")) throw RepositoryException.bad("Invalid repository name");
        var repo = repositories.get(name);
        String action = ProtocolIO.read(x) ? "read" : x.method().equals("DELETE") ? "delete" : "write";
        try { repositories.permissions().repository(p, repo, action); }
        catch (RepositoryException e) { if (e.status == 401) repositories.permissions().unauthorized(x); throw e; }
        PackageProtocol protocol = adapters.get((String) repo.get("format"));
        if (protocol == null) throw new RepositoryException(501, "UNSUPPORTED_FORMAT", "No adapter is registered for this format");
        if (repo.get("format").equals("docker") && path.startsWith("v2/")) path = path.substring(3);
        protocol.handle(x, repo, path, p);
    }
    public void docker(ProtocolExchange x, String path, RepositoryPrincipal p) throws Exception {
        if (path.equals("/v2/") || path.equals("/v2/_catalog")) { dockerRoot(x, path, p); return; }
        String rest = path.substring(4); int slash = rest.indexOf('/');
        if (slash < 1) throw RepositoryException.missing();
        String name = rest.substring(0, slash);
        if (!repositories.get(name).get("format").equals("docker")) throw RepositoryException.missing();
        repository(x, name, rest.substring(slash + 1), p);
    }
    private void dockerRoot(ProtocolExchange x, String path, RepositoryPrincipal p) throws Exception {
        ProtocolIO.method(x, "GET", "HEAD");
        var readable = repositories.store().all("repos").stream().filter(r -> r.get("format").equals("docker")
                && repositories.permissions().allowed(p, r, "read")).toList();
        if (readable.isEmpty() && p == null) repositories.permissions().unauthorized(x);
        x.responseHeaders().set("Docker-Distribution-Api-Version", "registry/2.0");
        if (path.equals("/v2/")) { ProtocolIO.json(x, 200, Json.map()); return; }
        Set<String> names = new TreeSet<>();
        for (var repo : readable) for (var asset : repositories.store().assets((String) repo.get("name"))) {
            var metadata = Json.object(asset.get("meta"));
            if ("oci-manifest".equals(metadata.get("kind"))) names.add(repo.get("name") + "/" + metadata.get("image"));
        }
        var query = ProtocolIO.query(x); String last = query.getOrDefault("last", "");
        int count = ProtocolIO.page(query, "n", 100, 1000);
        var items = names.stream().filter(n -> n.compareTo(last) > 0).limit(count).toList();
        if (count > 0 && names.stream().filter(n -> n.compareTo(last) > 0).count() > items.size())
            x.responseHeaders().set("Link", "<" + repositories.config().publicUrl() + "/v2/_catalog?n=" + count
                    + "&last=" + ProtocolIO.enc(items.getLast()) + ">; rel=\"next\"");
        ProtocolIO.json(x, 200, Json.map("repositories", items));
    }
}
