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

@RequestMapping("/api")
public final class UserController {
    private final UserService users;
    private final ApiSupport api;
    public UserController(UserService users, ApiSupport api) { this.users = users; this.api = api; }
    @GetMapping("/users") public Map<String, Object> list(Authentication auth) { return users.list(api.principal(auth)); }
    @PostMapping("/users") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String, Object> create(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return users.save(api.principal(auth), null, api.body(request, response), true);
    }
    @PutMapping("/users/{name}") public Map<String, Object> update(Authentication auth, @PathVariable String name,
                                                                  HttpServletRequest request, HttpServletResponse response) throws Exception {
        return users.save(api.principal(auth), name, api.body(request, response), false);
    }
    @DeleteMapping("/users/{name}") public Map<String, Object> delete(Authentication auth, @PathVariable String name,
                                                                     HttpServletRequest request, HttpServletResponse response) throws Exception {
        return users.delete(api.principal(auth), name, Json.str(api.body(request, response), "confirm", ""));
    }
    @PostMapping("/password") public Map<String, Object> password(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var data = api.body(request, response);
        return users.changePassword(api.principal(auth), Json.str(data, "oldPassword", ""), Json.str(data, "password", ""));
    }
}
