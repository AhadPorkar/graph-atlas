package ir.graph.repo.server.security;

import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.security.IdentityService;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import java.util.List;
import java.util.Objects;

public final class RepositoryAuthenticationProvider implements AuthenticationProvider {
    private final IdentityService identity;
    public RepositoryAuthenticationProvider(IdentityService identity) { this.identity = identity; }
    @Override public Authentication authenticate(Authentication request) {
        String credential = Objects.toString(request.getCredentials(), "");
        String remote = request.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : "unknown";
        RepositoryPrincipal principal = request instanceof OpaqueTokenAuthentication
                ? identity.token(credential) : request instanceof PasswordLoginAuthentication
                ? identity.authenticatePassword(request.getName(), credential, remote)
                : identity.authenticate(request.getName(), credential, remote);
        if (principal == null) throw new BadCredentialsException("Invalid credentials");
        var result = authenticated(principal); result.setDetails(request.getDetails()); return result;
    }
    @Override public boolean supports(Class<?> type) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type) || OpaqueTokenAuthentication.class.isAssignableFrom(type);
    }
    public static UsernamePasswordAuthenticationToken authenticated(RepositoryPrincipal p) {
        return UsernamePasswordAuthenticationToken.authenticated(p, null,
                List.of(new SimpleGrantedAuthority(p.admin() ? "ROLE_ADMIN" : "ROLE_USER")));
    }
}
