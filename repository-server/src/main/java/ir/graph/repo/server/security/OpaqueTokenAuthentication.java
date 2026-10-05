package ir.graph.repo.server.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.util.List;

public final class OpaqueTokenAuthentication extends AbstractAuthenticationToken {
    private String token;
    public OpaqueTokenAuthentication(String token) { super(List.of()); this.token = token; }
    @Override public Object getCredentials() { return token; }
    @Override public Object getPrincipal() { return "opaque-token"; }
    @Override public void eraseCredentials() { super.eraseCredentials(); token = null; }
}
