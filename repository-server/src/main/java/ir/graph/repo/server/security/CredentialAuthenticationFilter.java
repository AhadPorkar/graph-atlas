package ir.graph.repo.server.security;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.security.IdentityService;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.server.web.ErrorResponder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Explicit credentials never fall back to a browser session on failure. */
public final class CredentialAuthenticationFilter extends OncePerRequestFilter {
    public static final String EXPLICIT = "graph.explicitCredentials";
    private final ErrorResponder errors;
    private final AuthenticationManager manager;
    private final IdentityService identity;
    public CredentialAuthenticationFilter(AuthenticationManager manager, IdentityService identity, ErrorResponder errors) { this.manager = manager; this.identity = identity; this.errors = errors; }
    public static boolean hasExplicitCredentials(HttpServletRequest request) {
        return request.getHeader("Authorization") != null || request.getHeader("X-NuGet-ApiKey") != null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            if (hasExplicitCredentials(request)) {
                request.setAttribute(EXPLICIT, true);
                AbstractAuthenticationToken candidate = credentials(request);
                candidate.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                Authentication authenticated = manager.authenticate(candidate);
                var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authenticated);
                SecurityContextHolder.setContext(context);
                request.setAttribute(ErrorResponder.ACTOR, authenticated.getName());
            } else {
                Authentication saved = SecurityContextHolder.getContext().getAuthentication();
                if (saved != null && saved.getPrincipal() instanceof RepositoryPrincipal principal) {
                    RepositoryPrincipal current = identity.find(principal.username());
                    if (current == null || !Passwords.constantTimeEquals(current.securityStamp(), principal.securityStamp())) {
                        SecurityContextHolder.clearContext();
                        HttpSession session = request.getSession(false); if (session != null) session.invalidate();
                        throw new BadCredentialsException("Session is no longer valid");
                    }
                    // New context prevents a header identity or refreshed grants from mutating another stored context.
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(RepositoryAuthenticationProvider.authenticated(current));
                    SecurityContextHolder.setContext(context);
                    request.setAttribute(ErrorResponder.ACTOR, principal.username());
                }
            }
        } catch (RepositoryException e) {
            SecurityContextHolder.clearContext(); errors.write(request, response, e.status, e.code, e.getMessage()); return;
        } catch (AuthenticationException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext(); errors.write(request, response, 401, "UNAUTHORIZED", "Invalid or expired credentials"); return;
        }
        chain.doFilter(request, response);
    }
    private AbstractAuthenticationToken credentials(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization"), apiKey = request.getHeader("X-NuGet-ApiKey");
        if (authorization != null && apiKey != null) throw new BadCredentialsException("Use a single credential mechanism");
        if (apiKey != null) return new OpaqueTokenAuthentication(apiKey);
        if (authorization == null || authorization.length() > 8192) throw new BadCredentialsException("Invalid header");
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return new OpaqueTokenAuthentication(authorization.substring(7));
        if (authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            String value = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int colon = value.indexOf(':'); if (colon < 0) throw new BadCredentialsException("Malformed Basic credentials");
            return UsernamePasswordAuthenticationToken.unauthenticated(value.substring(0, colon), value.substring(colon + 1));
        }
        throw new BadCredentialsException("Unsupported authentication scheme");
    }
}
