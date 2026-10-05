package ir.graph.repo.server.config;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.security.IdentityService;
import ir.graph.repo.core.storage.*;
import ir.graph.repo.server.security.*;
import ir.graph.repo.server.web.ErrorResponder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    private final ErrorResponder errors;
    public SecurityConfiguration(ErrorResponder errors) { this.errors = errors; }

    @Bean AuthenticationManager authenticationManager(IdentityService identity) {
        return new ProviderManager(new RepositoryAuthenticationProvider(identity));
    }
    @Bean HttpSessionSecurityContextRepository sessionSecurityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository(); repository.setHeaderName("X-CSRF-Token"); return repository;
    }
    @Bean FilterRegistrationBean<RequestAuditFilter> requestAuditRegistration(RepositorySettings settings, ContentStore store, AuditLog audit) {
        var registration = new FilterRegistrationBean<>(new RequestAuditFilter(settings, store, audit, errors));
        registration.setOrder(-110); registration.setName("repositoryRequestPolicy"); return registration;
    }
    @Bean WebSecurityCustomizer firewall() {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        // All other strict firewall protections remain enabled; RequestPaths additionally validates decoded segments.
        return web -> web.httpFirewall(firewall).requestRejectedHandler((request, response, ex) ->
                errors.write(request, response, 400, "BAD_PATH", "Request rejected by the HTTP firewall"));
    }
    private void common(HttpSecurity http, AuthenticationManager manager, IdentityService identity, RepositorySettings settings) throws Exception {
        http.authenticationManager(manager)
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable()).logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403, ex instanceof CsrfException ? "CSRF" : "FORBIDDEN", "Permission denied or invalid CSRF token")))
                .addFilterAfter(new CredentialAuthenticationFilter(manager, identity, errors), SecurityContextHolderFilter.class)
                .addFilterAfter(new OriginPolicyFilter(settings, errors), CredentialAuthenticationFilter.class);
    }
    @Bean @Order(1)
    SecurityFilterChain protocolAndMonitoring(HttpSecurity http, AuthenticationManager manager, IdentityService identity, RepositorySettings settings) throws Exception {
        http.securityMatcher("/repository/**", "/v2", "/v2/**", "/actuator/**", "/healthz");
        common(http, manager, identity, settings);
        http.csrf(c -> c.disable())
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/healthz").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().permitAll()); // Repository ACLs, including anonymous policy, are enforced by PackageGateway.
        return http.build();
    }
    @Bean @Order(2)
    SecurityFilterChain administration(HttpSecurity http, AuthenticationManager manager, IdentityService identity, RepositorySettings settings,
                                      HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrfTokens) throws Exception {
        http.securityMatcher("/api/**"); common(http, manager, identity, settings);
        http.securityContext(c -> c.requireExplicitSave(true).securityContextRepository(contexts))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(c -> c.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(req -> !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
                                && !req.getRequestURI().equals("/api/login")
                                && !CredentialAuthenticationFilter.hasExplicitCredentials(req)))
                .authorizeHttpRequests(a -> a.requestMatchers("/api/login").permitAll().anyRequest().authenticated());
        return http.build();
    }
    @Bean @Order(3)
    SecurityFilterChain staticResources(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).requestCache(c -> c.disable()).formLogin(f -> f.disable()).httpBasic(b -> b.disable())
                .logout(l -> l.disable()).securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/app.js", "/i18n.js", "/client-examples.js", "/delivery.js", "/locales/en.json", "/locales/de.json", "/locales/fa.json", "/style.css", "/logo.svg", "/error", "/favicon.ico").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 404, "NOT_FOUND", "Endpoint not found"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 404, "NOT_FOUND", "Endpoint not found")));
        return http.build();
    }
}
