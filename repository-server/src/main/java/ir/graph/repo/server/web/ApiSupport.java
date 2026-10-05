package ir.graph.repo.server.web;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.protocol.ProtocolIO;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public final class ApiSupport {
    private final RepositorySettings settings;
    public ApiSupport(RepositorySettings settings) { this.settings = settings; }
    public Map<String, Object> body(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return ProtocolIO.bodyJson(new ServletProtocolExchange(request, response), settings);
    }
    public RepositoryPrincipal principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof RepositoryPrincipal principal))
            throw new RepositoryException(401, "UNAUTHORIZED", "Authentication is required");
        return principal;
    }
}
