package ir.graph.repo.server.security;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.protocol.RequestPaths;
import ir.graph.repo.core.storage.*;
import ir.graph.repo.server.web.ErrorResponder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

public final class RequestAuditFilter extends OncePerRequestFilter {
    public static final String DECODED_PATH = "graph.decodedPath";
    private final ErrorResponder errors;
    private final RepositorySettings settings;
    private final ContentStore store;
    private final AuditLog audit;
    private final Semaphore slots;
    public RequestAuditFilter(RepositorySettings settings, ContentStore store, AuditLog audit, ErrorResponder errors) {
        this.errors = errors;
        this.settings = settings; this.store = store; this.audit = audit;
        this.slots = settings.concurrentRequests() == 0 ? null : new Semaphore(settings.concurrentRequests());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString(); request.setAttribute(ErrorResponder.REQUEST_ID, id);
        MDC.put("requestId", id); response.setHeader("X-Request-ID", id); store.requests.incrementAndGet();
        boolean acquired = slots == null || slots.tryAcquire();
        try {
            headers(response);
            if (!acquired) throw new RepositoryException(503, "BUSY", "Configured concurrency guard is busy; retry later");
            request.setAttribute(DECODED_PATH, RequestPaths.decode(request.getRequestURI()));
            chain.doFilter(request, response);
        } catch (RepositoryException e) { errors.write(request, response, e.status, e.code, e.getMessage()); }
        finally {
            if (!Set.of("GET", "HEAD").contains(request.getMethod()) || response.getStatus() >= 400)
                audit.record(id, Objects.toString(request.getAttribute(ErrorResponder.ACTOR), ""), request.getMethod(), request.getRequestURI(), response.getStatus());
            if (acquired && slots != null) slots.release(); MDC.remove("requestId");
        }
    }
    private void headers(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (settings.secure()) response.setHeader("Strict-Transport-Security", "max-age=31536000");
    }
}
