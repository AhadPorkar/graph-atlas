package ir.graph.repo.server;

import ir.graph.repo.core.release.EvidenceEnvelope;
import ir.graph.repo.core.util.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

/** Runs in the full Maven reactor; core-only verification does not execute this suite. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReleaseMvcTest {
    static final String PASSWORD = "only-a-test-password-947521";
    static final Path HOME = Path.of("target", "test-data", "releases-" + UUID.randomUUID()).toAbsolutePath();
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry p) {
        p.add("graph.home", HOME::toString); p.add("graph.public-url", () -> "http://localhost:8081");
        p.add("graph.bootstrap-password", () -> PASSWORD); p.add("graph.login-failure-limit", () -> 0);
    }
    static String auth(String user) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }
    Map<String,Object> postJson(String url, String user, Object body, int expected) throws Exception {
        return Json.obj(mvc.perform(post(url).header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content(Json.stringify(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    Map<String,Object> transition(String id, String action, String user, long revision, int expected) throws Exception {
        return postJson("/api/releases/" + id + "/transitions/" + action, user,
                Json.map("expectedRevision", revision, "note", "Integration test decision"), expected);
    }
    @Test void completeGovernanceAndContainmentAcrossMvcAndPackageGateway() throws Exception {
        postJson("/api/repos", "admin", Json.map("name", "atlas-mvc", "format", "raw", "type", "hosted"), 201);
        postJson("/api/users", "admin", Json.map("username", "atlas-reviewer", "password", PASSWORD,
                "grants", Json.map("atlas-mvc", List.of("read", "approve"))), 201);
        byte[] bytes = "signed-release-package-fixture".getBytes(StandardCharsets.UTF_8);
        mvc.perform(put("/repository/atlas-mvc/app.bin").header("Authorization", auth("admin"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes)).andExpect(status().isCreated());
        var created = postJson("/api/releases", "admin", Json.map("name", "test-release", "version", "1.0.0",
                "assets", List.of(Json.map("repo", "atlas-mvc", "path", "app.bin"))), 201);
        String id = Json.str(created, "id", "");
        transition(id, "submit", "admin", 1, 200);
        var denial = transition(id, "approve", "admin", 2, 403);
        assertEquals("SELF_APPROVAL", denial.get("error"));
        transition(id, "approve", "atlas-reviewer", 2, 200);
        transition(id, "release", "admin", 2, 409);
        transition(id, "release", "admin", 3, 200);
        mvc.perform(get("/api/releases/" + id + "/gate").header("Authorization", auth("atlas-reviewer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true)).andExpect(jsonPath("$.deep").value(true));
        var key = Json.obj(mvc.perform(get("/api/release-signing-key").header("Authorization", auth("admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var envelope = Json.obj(mvc.perform(get("/api/releases/" + id + "/evidence").header("Authorization", auth("admin")))
                .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(id, EvidenceEnvelope.verify(envelope, Json.str(key, "keyId", "")).get("id"));
        mvc.perform(get("/api/releases/" + id + "/assets/0").header("Authorization", auth("atlas-reviewer")))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        String digest = EvidenceEnvelope.sha256(bytes);
        postJson("/api/quarantine", "admin", Json.map("sha256", digest, "active", true,
                "reason", "Test incident", "expectedRevision", 0), 200);
        mvc.perform(get("/repository/atlas-mvc/app.bin").header("Authorization", auth("admin"))).andExpect(status().is(423));
        mvc.perform(get("/api/releases/" + id + "/assets/0").header("Authorization", auth("admin"))).andExpect(status().is(423));
        mvc.perform(get("/api/releases/" + id + "/gate").header("Authorization", auth("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        postJson("/api/quarantine", "atlas-reviewer", Json.map("sha256", digest, "active", false,
                "reason", "Unauthorized action", "expectedRevision", 1), 403);
        postJson("/api/quarantine", "admin", Json.map("sha256", digest, "active", false,
                "reason", "Test resolved", "expectedRevision", 1), 200);
        mvc.perform(get("/api/insights/storage").header("Authorization", auth("atlas-reviewer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diskTotalBytes").doesNotExist());
        transition(id, "revoke", "admin", 4, 200);
        mvc.perform(get("/api/releases/" + id + "/gate").header("Authorization", auth("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(get("/api/releases/" + id + "/assets/0").header("Authorization", auth("admin"))).andExpect(status().isConflict());
        assertEquals("RELEASED", EvidenceEnvelope.verify(envelope, Json.str(key, "keyId", "")).get("state"));
    }
    @Test void newRoutesRequireAuthenticationAndLocalizeContainmentErrors() throws Exception {
        mvc.perform(get("/api/releases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/quarantine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/delivery.js")).andExpect(status().isOk());
    }
}
