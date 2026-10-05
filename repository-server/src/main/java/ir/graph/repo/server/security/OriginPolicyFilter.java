package ir.graph.repo.server.security;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.server.web.ErrorResponder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

public final class OriginPolicyFilter extends OncePerRequestFilter {
    private final ErrorResponder errors;
    private final RepositorySettings settings;
    public OriginPolicyFilter(RepositorySettings settings, ErrorResponder errors) { this.settings = settings; this.errors = errors; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            String origin = request.getHeader("Origin"), site = request.getHeader("Sec-Fetch-Site");
            boolean login = request.getRequestURI().equals("/api/login");
            boolean session = request.getRequestURI().startsWith("/api/") && !CredentialAuthenticationFilter.hasExplicitCredentials(request)
                    && SecurityContextHolder.getContext().getAuthentication() != null;
            if (((login || session) && origin == null)
                    || (origin != null && !Passwords.constantTimeEquals(origin, settings.publicUrl().toString()))
                    || (site != null && !Set.of("same-origin", "none").contains(site))) {
                errors.write(request, response, 403, "ORIGIN", "Origin does not match GR_PUBLIC_URL"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
