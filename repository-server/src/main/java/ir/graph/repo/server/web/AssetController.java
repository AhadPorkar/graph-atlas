package ir.graph.repo.server.web;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.util.Json;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class AssetController {
    private final RepositoryService repositories;
    private final RepositoryAdminService administration;
    private final ApiSupport api;
    public AssetController(RepositoryService repositories, RepositoryAdminService administration, ApiSupport api) {
        this.repositories = repositories; this.administration = administration; this.api = api;
    }
    @GetMapping("/assets") public Map<String, Object> list(Authentication auth, HttpServletRequest request) {
        var query = ProtocolIO.query(request.getQueryString());
        return administration.assets(api.principal(auth), query.getOrDefault("repo", ""), query.getOrDefault("q", ""),
                ProtocolIO.page(query, "offset", 0, Integer.MAX_VALUE), ProtocolIO.page(query, "limit", 50, 1000));
    }
    @GetMapping("/download") public void download(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var query = ProtocolIO.query(request.getQueryString()); var repo = repositories.get(query.getOrDefault("repo", ""));
        repositories.permissions().repository(api.principal(auth), repo, "read");
        var asset = repositories.store().asset((String) repo.get("name"), query.getOrDefault("path", ""));
        if (asset == null) throw RepositoryException.missing();
        ProtocolIO.asset(new ServletProtocolExchange(request, response), repositories.store(), asset, true);
    }
    @PutMapping("/upload") public void upload(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var query = ProtocolIO.query(request.getQueryString()); var repo = repositories.get(query.getOrDefault("repo", ""));
        RepositoryPrincipal principal = api.principal(auth); rawWrite(principal, repo, "write");
        repositories.generic(new ServletProtocolExchange(request, response), repo, query.getOrDefault("path", ""), principal);
    }
    @DeleteMapping("/asset") public void delete(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var data = api.body(request, response); var repo = repositories.get(Json.str(data, "repo", ""));
        RepositoryPrincipal principal = api.principal(auth); rawWrite(principal, repo, "delete");
        repositories.generic(new ServletProtocolExchange(request, response), repo, Json.str(data, "path", ""), principal);
    }
    @PostMapping("/pypi/yank") public Map<String, Object> yank(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return administration.yank(api.principal(auth), api.body(request, response));
    }
    private void rawWrite(RepositoryPrincipal p, Map<String, Object> repo, String action) {
        repositories.permissions().repository(p, repo, action); repositories.hosted(repo);
        if (!Set.of("raw", "maven").contains(repo.get("format")))
            throw RepositoryException.bad("Use the package client so format-specific metadata is maintained");
    }
}
