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

@RequestMapping("/api/tokens")
public final class TokenController {
    private final UserService users;
    private final ApiSupport api;
    public TokenController(UserService users, ApiSupport api) { this.users = users; this.api = api; }
    @GetMapping public Map<String, Object> list(Authentication auth) { return users.tokens(api.principal(auth)); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, Object> create(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return users.createToken(api.principal(auth), api.body(request, response));
    }
    @DeleteMapping("/{id}") public Map<String, Object> delete(Authentication auth, @PathVariable String id) throws Exception {
        return users.deleteToken(api.principal(auth), id);
    }
}
