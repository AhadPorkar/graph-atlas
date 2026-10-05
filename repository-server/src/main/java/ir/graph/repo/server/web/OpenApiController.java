package ir.graph.repo.server.web;

import ir.graph.repo.core.util.Json;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class OpenApiController {
    private final ApiSupport api;
    public OpenApiController(ApiSupport api) { this.api = api; }
    @GetMapping("/api/openapi") public Map<String, Object> document(Authentication auth) throws IOException {
        api.principal(auth);
        try (var stream = new ClassPathResource("openapi/graph-repository.openapi.json").getInputStream()) {
            return Json.obj(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
