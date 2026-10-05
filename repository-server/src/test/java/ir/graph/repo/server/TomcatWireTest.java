package ir.graph.repo.server;

import ir.graph.repo.core.util.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real embedded Tomcat requests: catches connector/filter differences MockMvc cannot detect. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TomcatWireTest {
    static final String PASSWORD = "wire-test-password-875125";
    static final String ORIGIN = "http://localhost";
    static final Path HOME = Path.of("target", "test-data", "tomcat-" + UUID.randomUUID()).toAbsolutePath();
    @Autowired Environment environment;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry p) {
        p.add("graph.home", HOME::toString); p.add("graph.public-url", () -> ORIGIN);
        p.add("graph.bootstrap-password", () -> PASSWORD); p.add("graph.login-failure-limit", () -> 0);
    }
    String base() { return "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port"); }
    String basic() { return "Basic " + Base64.getEncoder().encodeToString(("admin:" + PASSWORD).getBytes(StandardCharsets.UTF_8)); }
    HttpResponse<byte[]> request(HttpClient client, String method, String path, byte[] body, String... headers) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(45));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return client.send(b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    static Map<String, Object> json(HttpResponse<byte[]> response) { return Json.obj(new String(response.body(), StandardCharsets.UTF_8)); }
    static byte[] bytes(Object value) { return Json.stringify(value).getBytes(StandardCharsets.UTF_8); }
    @Test void realCookieAndCsrfContract() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var login = request(client, "POST", "/api/login", bytes(Json.map("username", "admin", "password", PASSWORD)), "Content-Type", "application/json", "Origin", ORIGIN);
            assertEquals(200, login.statusCode());
            String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict")); assertTrue(cookie.contains("Path=/api"));
            String session = cookie.split(";", 2)[0], csrf = (String) json(login).get("csrf");
            byte[] repo = bytes(Json.map("name", "wire-cookie", "format", "raw"));
            assertEquals(403, request(client, "POST", "/api/repos", repo, "Content-Type", "application/json", "Cookie", session, "Origin", ORIGIN).statusCode());
            assertEquals(201, request(client, "POST", "/api/repos", repo, "Content-Type", "application/json", "Cookie", session, "Origin", ORIGIN, "X-CSRF-Token", csrf).statusCode());
            assertEquals(401, request(client, "GET", "/repository/raw-hosted/secret.txt", null, "Cookie", session).statusCode(), "Package endpoints must not use browser session credentials");
        }
    }
    @Test void streamingRangesScopedPathsAndStatelessAuthentication() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var issue = request(client, "POST", "/api/tokens", bytes(Json.map("label", "wire-test")), "Content-Type", "application/json", "Authorization", basic());
            assertEquals(201, issue.statusCode()); String auth = "Bearer " + json(issue).get("token");
            byte[] artifact = new byte[3 * 1024 * 1024]; new Random(47).nextBytes(artifact);
            var upload = request(client, "PUT", "/repository/raw-hosted/wire.bin", artifact, "Authorization", auth);
            assertEquals(201, upload.statusCode()); assertTrue(upload.headers().allValues("Set-Cookie").isEmpty());
            var range = request(client, "GET", "/repository/raw-hosted/wire.bin", null, "Authorization", auth, "Range", "bytes=1-31");
            assertEquals(206, range.statusCode()); assertArrayEquals(Arrays.copyOfRange(artifact, 1, 32), range.body());
            assertTrue(range.headers().firstValue("Content-Security-Policy").orElseThrow().contains("sandbox"));
            var head = request(client, "HEAD", "/repository/raw-hosted/wire.bin", null, "Authorization", auth);
            assertEquals(200, head.statusCode()); assertEquals(Integer.toString(artifact.length), head.headers().firstValue("Content-Length").orElseThrow());
            // The adapter checks the tarball digest; it does not execute package code.
            byte[] tarball;
            try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/graph-demo.tgz"))) { tarball = in.readAllBytes(); }
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(tarball));
            var packument = Json.map("name", "@wire/demo", "versions", Json.map("1.0.0", Json.map("name", "@wire/demo", "version", "1.0.0",
                    "dist", Json.map("tarball", "http://localhost/demo-1.0.0.tgz", "shasum", sha1))),
                    "dist-tags", Json.map("latest", "1.0.0"), "_attachments", Json.map("demo-1.0.0.tgz", Json.map("data", Base64.getEncoder().encodeToString(tarball))));
            var published = request(client, "PUT", "/repository/npm-hosted/@wire%2Fdemo", bytes(packument), "Authorization", auth, "Content-Type", "application/json");
            assertEquals(201, published.statusCode(), () -> new String(published.body(), StandardCharsets.UTF_8));
            var metadata = request(client, "GET", "/repository/npm-hosted/@wire%2fdemo", null, "Authorization", auth);
            assertEquals(200, metadata.statusCode()); assertEquals("@wire/demo", json(metadata).get("name"));
            for (String path : List.of("@wire%2Fdemo", "@wire/demo", "%40wire%2Fdemo")) {
                var alternate = request(client, "GET", "/repository/npm-hosted/" + path, null, "Authorization", auth);
                assertEquals(200, alternate.statusCode(), () -> path + ": " + new String(alternate.body(), StandardCharsets.UTF_8));
                assertEquals("@wire/demo", json(alternate).get("name"));
            }
            for (String path : List.of("a/%252e%252e/secret", "%2e%2e%2fsecret", "a//b"))
                assertEquals(400, request(client, "GET", "/repository/raw-hosted/" + path, null, "Authorization", auth).statusCode(), path);
            assertEquals(400, request(client, "GET", "/api%2frepos", null, "Authorization", auth).statusCode());
        }
    }

    @Test void encodedArtifactPathsPreserveIdentityAndPlus() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            byte[] content = "encoded slash and literal plus".getBytes(StandardCharsets.UTF_8);
            var put = request(client, "PUT", "/repository/raw-hosted/path-test%2Ffile+name.txt", content,
                    "Authorization", basic(), "Content-Type", "application/octet-stream");
            assertEquals(201, put.statusCode(), () -> new String(put.body(), StandardCharsets.UTF_8));
            for (String path : List.of("path-test/file+name.txt", "path-test%2Ffile+name.txt", "path-test%2ffile+name.txt")) {
                var get = request(client, "GET", "/repository/raw-hosted/" + path, null, "Authorization", basic());
                assertEquals(200, get.statusCode(), path);
                assertArrayEquals(content, get.body(), path);
            }
        }
    }

    @Test void ambiguousPathsRemainRejectedBeforeAuthentication() throws Exception {
        // These requests must fail even if the container normalizes its mapping path.
        // The raw URI guard runs before credentials, security-chain selection and MVC.
        var paths = List.of(
                "/api%2frepos", "/api%2Frepos", "/v2%2fcatalog",
                "/repository%2fraw-hosted/secret", "/repository/raw-hosted%2fsecret",
                "/repository/raw-hosted%2ffolder/file", "/repository/raw-hosted/a%2f%2fb",
                "/repository/raw-hosted/..%2f..%2fapi/repos",
                "/repository/raw-hosted/a%2f../secret", "/repository/raw-hosted/.%2e/secret",
                "/repository/raw-hosted/a/%252e%252e/secret", "/repository/raw-hosted/%252Fsecret",
                "/repository/raw-hosted/a%5cb", "/repository/raw-hosted/a%3bb", "/repository/raw-hosted/a//b");
        try (var client = HttpClient.newHttpClient()) {
            for (String path : paths) {
                assertEquals(400, request(client, "GET", path, null).statusCode(), "Anonymous: " + path);
                assertEquals(400, request(client, "GET", path, null, "Authorization", basic()).statusCode(), "Authenticated: " + path);
            }
        }
    }
}
