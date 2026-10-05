package ir.graph.repo.server.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Browser password login intentionally does not accept opaque API tokens as passwords. */
public final class PasswordLoginAuthentication extends UsernamePasswordAuthenticationToken {
    public PasswordLoginAuthentication(String username, String password) { super(username, password); }
}
