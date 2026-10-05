package ir.graph.repo.server.web;

import ir.graph.repo.core.release.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.ProtocolIO;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class ReleaseController {
    private final ReleaseService releases;
    private final QuarantineService quarantine;
    private final StorageInsightsService insights;
    private final ContentStore store;
    private final ApiSupport api;
    public ReleaseController(ReleaseService releases, QuarantineService quarantine, StorageInsightsService insights,
                             ContentStore store, ApiSupport api) {
        this.releases = releases; this.quarantine = quarantine; this.insights = insights; this.store = store; this.api = api;
    }
    @GetMapping("/releases") public Map<String, Object> list(Authentication auth, HttpServletRequest request) {
        var query = ProtocolIO.query(request.getQueryString());
        return releases.list(api.principal(auth), query.getOrDefault("state", ""),
                ProtocolIO.page(query, "offset", 0, Integer.MAX_VALUE), ProtocolIO.page(query, "limit", 100, 200));
    }
    @PostMapping("/releases") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return releases.create(api.principal(auth), api.body(request, response));
    }
    @GetMapping("/releases/{id}") public Map<String, Object> get(Authentication auth, @PathVariable String id) {
        return releases.get(api.principal(auth), id);
    }
    @PostMapping("/releases/{id}/transitions/{action}")
    public Map<String, Object> transition(Authentication auth, @PathVariable String id, @PathVariable String action,
                                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        return releases.transition(api.principal(auth), id, action, api.body(request, response));
    }
    @GetMapping("/releases/{id}/gate")
    public Map<String, Object> gate(Authentication auth, @PathVariable String id, @RequestParam(defaultValue = "true") boolean deep) throws Exception {
        return releases.gate(api.principal(auth), id, deep);
    }
    @GetMapping("/releases/{id}/evidence")
    public Map<String, Object> evidence(Authentication auth, @PathVariable String id, HttpServletResponse response) {
        var value = releases.evidence(api.principal(auth), id);
        response.setHeader("Content-Disposition", "attachment; filename=\"atlas-release-" + id + ".json\"");
        response.setHeader("Cache-Control", "no-store"); return value;
    }
    @GetMapping("/releases/{id}/assets/{index}")
    public void download(Authentication auth, @PathVariable String id, @PathVariable int index,
                         HttpServletRequest request, HttpServletResponse response) throws Exception {
        ProtocolIO.asset(new ServletProtocolExchange(request, response), store, releases.download(api.principal(auth), id, index), true);
    }
    @GetMapping("/release-signing-key") public Map<String, Object> key(Authentication auth) throws Exception { return releases.identity(api.principal(auth)); }
    @GetMapping("/quarantine") public Map<String, Object> holds(Authentication auth) { return quarantine.list(api.principal(auth)); }
    @PostMapping("/quarantine") public Map<String, Object> hold(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return quarantine.save(api.principal(auth), api.body(request, response));
    }
    @GetMapping("/insights/storage") public Map<String, Object> storage(Authentication auth) throws Exception { return insights.inspect(api.principal(auth)); }
}
