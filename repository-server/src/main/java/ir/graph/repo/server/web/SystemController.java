package ir.graph.repo.server.web;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.storage.*;
import ir.graph.repo.core.security.PermissionService;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.server.RepositoryApplication;
import jakarta.servlet.http.*;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.SpringVersion;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class SystemController {
    private final ContentStore store;
    private final AuditLog audit;
    private final RepositorySettings settings;
    private final RepositoryAdminService administration;
    private final MaintenanceService maintenance;
    private final PermissionService permissions;
    private final ApiSupport api;
    public SystemController(ContentStore store, AuditLog audit, RepositorySettings settings, RepositoryAdminService administration,
                            MaintenanceService maintenance, PermissionService permissions, ApiSupport api) {
        this.store = store; this.audit = audit; this.settings = settings; this.administration = administration;
        this.maintenance = maintenance; this.permissions = permissions; this.api = api;
    }
    @GetMapping("/healthz") public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.status(store.failed() ? 503 : 200).body(Json.map("status", store.failed() ? "down" : "ok",
                "version", RepositoryApplication.VERSION, "engine", "spring-boot", "springBoot", SpringBootVersion.getVersion(), "java", Runtime.version().toString()));
    }
    @GetMapping("/api/stats") public Map<String, Object> stats(Authentication auth) { return administration.stats(api.principal(auth)); }
    @GetMapping("/api/system") public Map<String, Object> system(Authentication auth) {
        permissions.admin(api.principal(auth));
        return Json.map("version", RepositoryApplication.VERSION, "java", Runtime.version().toString(),
                "framework", "Spring Boot", "springBoot", SpringBootVersion.getVersion(), "springFramework", SpringVersion.getVersion(),
                "dataDirectory", store.home().toString(), "publicUrl", settings.publicUrl().toString(),
                "maxUploadBytes", settings.maxUpload(), "maxJsonBytes", settings.maxJson(), "concurrentRequests", settings.concurrentRequests(),
                "allowPrivateUpstream", settings.allowPrivateUpstream(), "allowHttpUpstream", settings.allowHttpUpstream(),
                "licenseQuotas", false, "formats", new TreeSet<>(RepositoryService.FORMATS), "clustered", false,
                "defaultLanguage", "en", "supportedLanguages", ir.graph.repo.core.i18n.SupportedLanguages.TAGS);
    }
    @GetMapping("/api/audit") public Map<String, Object> audit(Authentication auth, HttpServletRequest request) throws Exception {
        permissions.admin(api.principal(auth));
        return Json.map("items", audit.tail(ProtocolIO.page(ProtocolIO.query(request.getQueryString()), "limit", 100, 1000)));
    }
    @PostMapping("/api/maintenance") public Map<String, Object> maintenance(Authentication auth, HttpServletRequest request, HttpServletResponse response) throws Exception {
        return maintenance.execute(api.principal(auth), api.body(request, response));
    }
}
