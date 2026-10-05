package ir.graph.repo.server.web;

import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.protocol.RequestPaths;
import ir.graph.repo.core.service.PackageGateway;
import ir.graph.repo.server.security.RequestAuditFilter;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class PackageController {
    private final PackageGateway gateway;
    public PackageController(PackageGateway gateway) { this.gateway = gateway; }
    @RequestMapping({"/repository/{name}", "/repository/{name}/**"})
    public void repository(@PathVariable String name, Authentication authentication, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String decoded = path(request), prefix = "/repository/" + name;
        if (!decoded.equals(prefix) && !decoded.startsWith(prefix + "/"))
            throw ir.graph.repo.core.domain.RepositoryException.bad("Inconsistent repository path");
        String tail = decoded.length() <= prefix.length() ? "" : decoded.substring(prefix.length() + 1);
        gateway.repository(new ServletProtocolExchange(request, response), name, tail, principal(authentication));
    }
    @RequestMapping({"/v2", "/v2/**"})
    public void docker(Authentication authentication, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = path(request);
        gateway.docker(new ServletProtocolExchange(request, response), path.equals("/v2") ? "/v2/" : path, principal(authentication));
    }
    private String path(HttpServletRequest request) {
        Object path = request.getAttribute(RequestAuditFilter.DECODED_PATH);
        return path instanceof String s ? s : RequestPaths.decode(request.getRequestURI());
    }
    private RepositoryPrincipal principal(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof RepositoryPrincipal p ? p : null;
    }
}
