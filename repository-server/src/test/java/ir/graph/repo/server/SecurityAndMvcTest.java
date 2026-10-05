package ir.graph.repo.server;

import ir.graph.repo.core.util.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityAndMvcTest {
    static final String PASSWORD = "only-a-test-password-947521";
    static final String ORIGIN = "http://localhost:8081";
    static final Path HOME = Path.of("target", "test-data", "mvc-" + UUID.randomUUID()).toAbsolutePath();
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry p) {
        p.add("graph.home", HOME::toString);
        p.add("graph.public-url", () -> ORIGIN);
        p.add("graph.bootstrap-password", () -> PASSWORD);
        p.add("graph.login-failure-limit", () -> 0);
    }
    static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
    record Login(MockHttpSession session, String csrf) { }
    Login login(String user, String password) throws Exception {
        var result = mvc.perform(post("/api/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(Json.stringify(Json.map("username", user, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return new Login((MockHttpSession) result.getRequest().getSession(false),
                (String) Json.obj(result.getResponse().getContentAsString()).get("csrf"));
    }
    @Test void applicationAndStaticUiAreRealSpringEndpoints() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.engine").value("spring-boot"))
                .andExpect(jsonPath("$.springBoot").value("4.1.1"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/api/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", basic("admin", PASSWORD))).andExpect(status().isOk());
    }
    @Test void csrfOriginAndExplicitCredentialIsolation() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content(Json.stringify(Json.map("username", "admin", "password", PASSWORD))))
                .andExpect(status().isForbidden());
        Login state = login("admin", PASSWORD);
        mvc.perform(get("/api/session").session(state.session())).andExpect(status().isOk());
        String body = Json.stringify(Json.map("name", "mvc-csrf", "format", "raw"));
        mvc.perform(post("/api/repos").session(state.session()).header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/repos").session(state.session()).header("Origin", "https://attacker.invalid")
                .header("X-CSRF-Token", state.csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/repos").session(state.session()).header("Origin", ORIGIN).header("X-CSRF-Token", state.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(get("/api/session").session(state.session()).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        // A failing explicit credential may not destroy or impersonate the existing saved session.
        mvc.perform(get("/api/session").session(state.session())).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
        mvc.perform(post("/api/logout").session(state.session()).header("Origin", ORIGIN).header("X-CSRF-Token", state.csrf()))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
    @Test void apiTokensDoNotBecomeBrowserPasswordsAndMalformedJsonIsRejected() throws Exception {
        String token = (String) Json.obj(mvc.perform(post("/api/tokens").header("Authorization", basic("admin", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"mvc\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token");
        mvc.perform(get("/api/session").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/login").header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON)
                .content(Json.stringify(Json.map("username", "admin", "password", token)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/repos").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"one\",\"name\":\"two\"}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/repos").header("Authorization", "Bearer " + token).contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }
    @Test void passwordChangeInvalidatesOldSessionsAndTokens() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", basic("admin", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                .content(Json.stringify(Json.map("username", "mvc-user", "password", PASSWORD, "grants", Json.map("raw-hosted", List.of("read"))))))
                .andExpect(status().isCreated());
        Login state = login("mvc-user", PASSWORD);
        mvc.perform(get("/actuator/metrics").header("Authorization", basic("mvc-user", PASSWORD))).andExpect(status().isForbidden());
        String token = (String) Json.obj(mvc.perform(post("/api/tokens").header("Authorization", basic("mvc-user", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"password-rotation\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token");
        mvc.perform(post("/api/password").session(state.session()).header("Origin", ORIGIN).header("X-CSRF-Token", state.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(Json.stringify(Json.map("oldPassword", PASSWORD, "password", PASSWORD + "new"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/session").session(state.session())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/session").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login("mvc-user", PASSWORD + "new");
    }
}
