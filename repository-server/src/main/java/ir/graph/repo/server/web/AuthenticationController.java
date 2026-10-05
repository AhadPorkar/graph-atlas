package ir.graph.repo.server.web;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.server.RepositoryApplication;
import ir.graph.repo.server.security.*;
import jakarta.servlet.http.*;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class AuthenticationController {
    private final AuthenticationManager manager;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrfTokens;
    private final RepositorySettings settings;
    private final ApiSupport api;
    public AuthenticationController(AuthenticationManager manager, HttpSessionSecurityContextRepository contexts,
                                    HttpSessionCsrfTokenRepository csrfTokens, RepositorySettings settings, ApiSupport api) {
        this.manager = manager; this.contexts = contexts; this.csrfTokens = csrfTokens; this.settings = settings; this.api = api;
    }
    @PostMapping("/login") public Map<String, Object> login(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var data = api.body(request, response);
        var candidate = new PasswordLoginAuthentication(Json.str(data, "username", ""), Json.str(data, "password", ""));
        candidate.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        Authentication authentication = manager.authenticate(candidate);
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response);
        csrfTokens.saveToken(null, request, response);
        CsrfToken token = csrfTokens.generateToken(request); csrfTokens.saveToken(token, request, response);
        var principal = api.principal(authentication); request.setAttribute(ErrorResponder.ACTOR, principal.username());
        return Json.map("username", principal.username(), "admin", principal.admin(), "csrf", token.getToken());
    }
    @GetMapping("/session") public Map<String, Object> session(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var p = api.principal(authentication); String csrf = null;
        if (!CredentialAuthenticationFilter.hasExplicitCredentials(request)) {
            CsrfToken token = csrfTokens.loadToken(request);
            if (token == null) { token = csrfTokens.generateToken(request); csrfTokens.saveToken(token, request, response); }
            csrf = token.getToken();
        }
        return Json.map("username", p.username(), "admin", p.admin(), "csrf", csrf,
                "publicUrl", settings.publicUrl().toString(), "version", RepositoryApplication.VERSION,
                "java", Runtime.version().toString(), "springBoot", SpringBootVersion.getVersion(),
                "defaultLanguage", "en", "supportedLanguages", ir.graph.repo.core.i18n.SupportedLanguages.TAGS);
    }
    @PostMapping("/logout") public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        csrfTokens.saveToken(null, request, response); SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        HttpSession session = request.getSession(false); if (session != null) session.invalidate();
        response.addHeader("Set-Cookie", ResponseCookie.from("gr_session", "").path("/api").httpOnly(true)
                .secure(settings.secure()).sameSite("Strict").maxAge(Duration.ZERO).build().toString());
        return Json.map("ok", true);
    }
}
