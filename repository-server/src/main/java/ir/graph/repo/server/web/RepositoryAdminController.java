package ir.graph.repo.server.web;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.util.Json;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)

@RequestMapping("/api/repos")
public final class RepositoryAdminController {
    private final RepositoryAdminService service;
    private final ApiSupport api;
    public RepositoryAdminController(RepositoryAdminService service, ApiSupport api) { this.service = service; this.api = api; }
    @GetMapping public Map<String, Object> list(Authentication auth) { return service.list(api.principal(auth)); }
    @GetMapping("/{name}") public Map<String, Object> get(Authentication auth, @PathVariable String name) { return service.get(api.principal(auth), name); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, Object> create(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return service.save(api.principal(auth), null, api.body(request, response), true);
    }
    @PutMapping("/{name}") public Map<String, Object> update(Authentication auth, @PathVariable String name,
                                                            HttpServletRequest request, HttpServletResponse response) throws Exception {
        return service.save(api.principal(auth), name, api.body(request, response), false);
    }
    @DeleteMapping("/{name}") public Map<String, Object> delete(Authentication auth, @PathVariable String name,
                                                               HttpServletRequest request, HttpServletResponse response) throws Exception {
        return service.delete(api.principal(auth), name, api.body(request, response));
    }
}
