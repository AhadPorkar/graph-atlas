package ir.graph.repo.core;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.protocol.*;
import ir.graph.repo.core.protocol.npm.NpmProtocol;
import ir.graph.repo.core.protocol.nuget.NugetProtocol;
import ir.graph.repo.core.protocol.pypi.PypiProtocol;
import ir.graph.repo.core.protocol.oci.OciProtocol;
import ir.graph.repo.core.security.*;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Transport-neutral regression tests. These do NOT start or claim to test Spring Boot. */
public final class CoreContractTestMain {
    private static int passed;
    private static final String PASSWORD = "local-test-password-927515";
    private PackageGateway gateway;
    private RepositoryPrincipal admin;
    private static final class Exchange implements ProtocolExchange {
        final String method;
        final URI uri;
        final Map<String, String> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final InputStream input;
        int status = -1;
        Exchange(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method; this.uri = URI.create(path); this.requestHeaders.putAll(headers); this.input = new ByteArrayInputStream(body);
        }
        public String method() { return method; }
        public URI uri() { return uri; }
        public String header(String name) { return requestHeaders.get(name); }
        public InputStream input() { return input; }
        public OutputStream output() { return bytes; }
        public ResponseHeaders responseHeaders() { return new ResponseHeaders() {
            public void set(String name, String value) { headers.put(name, value); }
            public void add(String name, String value) { headers.merge(name, value, (a, b) -> a + ", " + b); }
        }; }
        public void respond(int status, long length) { this.status = status; }
        Map<String, Object> json() { return Json.obj(bytes.toString(StandardCharsets.UTF_8)); }
        String text() { return bytes.toString(StandardCharsets.UTF_8); }
    }
    private Exchange request(String method, String path, Map<String, String> headers, byte[] body, RepositoryPrincipal principal) throws Exception {
        var exchange = new Exchange(method, path, headers, body);
        try {
            String decoded = RequestPaths.decode(exchange.uri.getRawPath());
            if (decoded.startsWith("/v2/")) gateway.docker(exchange, decoded, principal);
            else {
                String tail = decoded.substring(12); int slash = tail.indexOf('/');
                gateway.repository(exchange, slash < 0 ? tail : tail.substring(0, slash), slash < 0 ? "" : tail.substring(slash + 1), principal);
            }
        } catch (RepositoryException error) { exchange.status = error.status; }
        return exchange;
    }
    private Exchange request(String method, String path) throws Exception { return request(method, path, Map.of(), new byte[0], admin); }
    private Exchange json(String method, String path, Object body) throws Exception {
        return request(method, path, Map.of("Content-Type", "application/json"), Json.stringify(body).getBytes(StandardCharsets.UTF_8), admin);
    }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name); passed++; System.out.println("PASS " + name);
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, int status, String name) throws Exception {
        try { action.run(); throw new AssertionError("Accepted: " + name); }
        catch (RepositoryException error) { check(error.status == status, name); }
    }
    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = CoreContractTestMain.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IOException("Fixture missing: " + name); return in.readAllBytes();
        }
    }
    private static byte[] multipart(byte[] content) throws IOException {
        var out = new ByteArrayOutputStream();
        for (var field : Map.of("name", "graph_demo", "version", "1.0.0", ":action", "file_upload", "requires_python", ">=3.9").entrySet())
            out.write(("--BOUND\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("--BOUND\r\nContent-Disposition: form-data; name=\"content\"; filename=\"graph_demo-1.0.0-py3-none-any.whl\"\r\nContent-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(content); out.write("\r\n--BOUND--\r\n".getBytes(StandardCharsets.UTF_8)); return out.toByteArray();
    }
    private Map<String, Object> npm(String name, String version) throws IOException {
        byte[] bytes = fixture("graph-demo.tgz"); String file = "graph-demo-" + version + ".tgz";
        return Json.map("name", name, "versions", Json.map(version, Json.map("name", name, "version", version,
                        "dist", Json.map("tarball", "https://registry.invalid/" + file))),
                "dist-tags", Json.map("latest", version), "_attachments", Json.map(file,
                        Json.map("content_type", "application/gzip", "data", Base64.getEncoder().encodeToString(bytes), "length", bytes.length)));
    }
    public static void main(String[] args) throws Exception { new CoreContractTestMain().run(); }
    private void run() throws Exception {
        Path parent = Files.createTempDirectory("graph-core-contract-"); Path home = parent.resolve("data");
        var settings = RepositorySettings.load(Map.of("GR_HOME", home.toString(), "GR_PUBLIC_URL", "http://localhost:8081"));
        try {
            try (ContentStore store = new ContentStore(settings); UpstreamClient upstream = new UpstreamClient(settings)) {
                var identity = new IdentityService(store, new LoginThrottle(10, Duration.ofMinutes(1))); identity.bootstrap(PASSWORD);
                var permissions = new PermissionService(); var repositories = new RepositoryService(store, settings, permissions, upstream);
                RepositoryBootstrap.seed(repositories);
                var administration = new RepositoryAdminService(repositories); var users = new UserService(store, identity, permissions);
                gateway = new PackageGateway(repositories, List.of(new MavenRawProtocol(repositories), new NpmProtocol(repositories),
                        new NugetProtocol(repositories), new PypiProtocol(repositories), new OciProtocol(repositories)));
                admin = identity.authenticatePassword("admin", PASSWORD, "test");
                check(admin != null && admin.admin(), "Bootstrap administrator authenticates");
                check(identity.authenticatePassword("admin", "wrong", "wrong-client") == null, "Invalid password is rejected");
                check(store.count("repos") == 7, "Default hosted repositories are seeded once");
                check(!Files.exists(home.resolve("admin.password")), "Supplied bootstrap password is not written in plaintext");
                var token = users.createToken(admin, Json.map("label", "contract")); String raw = (String) token.get("token");
                check(identity.token(raw) != null, "Opaque API token authenticates");
                check(!store.get("tokens", (String) token.get("id")).containsKey("token"), "Raw API token is never stored");
                check(identity.authenticate("token", raw, "token-client") != null, "API token works as a Basic password");
                check(identity.authenticatePassword("admin", raw, "browser-token") == null, "API token cannot create a password-login session");
                rejects(() -> users.delete(admin, "admin", "admin"), 409, "Last administrator cannot be deleted");
                users.save(admin, null, Json.map("username", "reader", "password", "reader-test-password-87221", "grants", Json.map("raw-hosted", List.of("read"))), true);
                var reader = identity.find("reader");
                check(!users.list(admin).toString().contains("pbkdf2"), "User listings never expose password hashes");
                rejects(() -> administration.save(reader, null, Json.map("name", "bad"), true), 403, "Non-admin cannot create repositories");
                var onlyRead = request("PUT", "/repository/raw-hosted/no.bin", Map.of(), new byte[]{1}, reader);
                check(onlyRead.status == 403, "Repository write permission is enforced");
                check(request("GET", "/repository/raw-hosted/a.bin", Map.of(), new byte[0], null).status == 401, "Private repositories require authentication");
                byte[] content = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
                check(request("PUT", "/repository/raw-hosted/a.bin", Map.of(), content, admin).status == 201, "Raw upload is persisted");
                var downloaded = request("GET", "/repository/raw-hosted/a.bin");
                check(Arrays.equals(downloaded.bytes.toByteArray(), content), "Raw download preserves bytes");
                check(request("HEAD", "/repository/raw-hosted/a.bin").bytes.size() == 0, "HEAD does not emit an artifact body");
                var range = request("GET", "/repository/raw-hosted/a.bin", Map.of("Range", "bytes=2-5"), new byte[0], admin);
                check(range.status == 206 && range.text().equals("cdef"), "Byte-range download is correct");
                check(request("GET", "/repository/raw-hosted/a.bin", Map.of("Range", "bytes=999-"), new byte[0], admin).status == 416, "Unsatisfiable range is rejected");
                check(request("GET", "/repository/raw-hosted/a.bin", Map.of("If-None-Match", downloaded.headers.get("ETag")), new byte[0], admin).status == 304, "Conditional download honors ETag");
                check(request("PUT", "/repository/raw-hosted/a.bin", Map.of(), new byte[]{3}, admin).status == 409, "Immutable redeployment is rejected");
                check(request("GET", "/repository/raw-hosted/a.bin", Map.of(), new byte[0], reader).status == 200, "Reader can download an allowed artifact");
                String mavenPath = "/repository/maven-releases/ir/graph/demo/1.0.0/demo-1.0.0.jar";
                check(request("PUT", mavenPath, Map.of(), content, admin).status == 201, "Maven release upload works");
                check(request("GET", mavenPath + ".sha256").text().equals(ContentStore.digest(new String(content, StandardCharsets.UTF_8))), "Maven SHA-256 sidecar is generated");
                check(request("PUT", "/repository/maven-releases/ir/graph/demo/1.0-SNAPSHOT/demo.jar", Map.of(), content, admin).status == 400, "Maven release policy rejects snapshots");
                check(request("PUT", "/repository/maven-snapshots/ir/graph/demo/1.0-SNAPSHOT/demo.jar", Map.of(), content, admin).status == 201, "Maven snapshot policy accepts snapshots");
                administration.save(admin, null, Json.map("name", "raw-group", "format", "raw", "type", "group", "members", List.of("raw-hosted")), true);
                check(Arrays.equals(request("GET", "/repository/raw-group/a.bin").bytes.toByteArray(), content), "Group downloads resolve a member");
                check(request("PUT", "/repository/raw-group/b.bin", Map.of(), content, admin).status == 405, "Groups reject publishing");
                rejects(() -> administration.delete(admin, "raw-hosted", Json.map("confirm", "raw-hosted", "purge", true)), 409, "Referenced repository cannot be deleted");
                rejects(() -> administration.save(admin, "raw-group", Json.map("name", "raw-group", "format", "raw", "type", "group", "members", List.of("raw-group")), false), 400, "Group cycles are rejected");
                check(json("PUT", "/repository/npm-hosted/graph-demo", npm("graph-demo", "1.0.0")).status == 201, "npm publication works");
                check(request("GET", "/repository/npm-hosted/graph-demo").json().containsKey("versions"), "npm packument is available");
                check(request("GET", "/repository/npm-hosted/graph-demo/latest").json().get("version").equals("1.0.0"), "npm latest tag resolves a version");
                check(json("PUT", "/repository/npm-hosted/@scope%2fdemo", npm("@scope/demo", "1.0.0")).status == 201, "Scoped npm publication works after one decoding pass");
                check(request("GET", "/repository/npm-hosted/@scope%2fdemo").status == 200, "Scoped npm metadata can be read");
                check(json("PUT", "/repository/npm-hosted/graph-demo", npm("graph-demo", "1.0.0")).status == 409, "npm duplicate version is rejected");
                check(request("GET", "/repository/npm-hosted/graph-demo/-/graph-demo-1.0.0.tgz").status == 200, "npm tarball is available");
                check(json("PUT", "/repository/npm-hosted/-/package/graph-demo/dist-tags/stable", "1.0.0").status == 200, "npm dist-tag update works");
                byte[] python = fixture("graph_demo-1.0.0-py3-none-any.whl");
                check(request("POST", "/repository/pypi-hosted/", Map.of("Content-Type", "multipart/form-data; boundary=BOUND"), multipart(python), admin).status == 200, "Python multipart upload works");
                var simple = request("GET", "/repository/pypi-hosted/simple/graph-demo/");
                check(simple.status == 200 && simple.text().contains("#sha256="), "PyPI Simple HTML includes hashes");
                var simpleJson = request("GET", "/repository/pypi-hosted/simple/graph-demo/", Map.of("Accept", "application/vnd.pypi.simple.v1+json"), new byte[0], admin);
                check(simpleJson.status == 200 && simpleJson.json().containsKey("files"), "PyPI Simple JSON is available");
                check(Arrays.equals(request("GET", "/repository/pypi-hosted/files/graph-demo/graph_demo-1.0.0-py3-none-any.whl").bytes.toByteArray(), python), "Python wheel bytes are preserved");
                administration.yank(admin, Json.map("repo", "pypi-hosted", "path", "files/graph-demo/graph_demo-1.0.0-py3-none-any.whl", "yanked", "withdrawn"));
                check(request("GET", "/repository/pypi-hosted/simple/graph-demo/").text().contains("data-yanked=\"withdrawn\""), "PyPI yanked metadata is emitted");
                byte[] nuget = fixture("Graph.Demo.1.0.0.nupkg");
                check(request("PUT", "/repository/nuget-hosted/v2/package", Map.of("Content-Type", "application/octet-stream"), nuget, admin).status == 201, "NuGet nupkg publication works");
                check(request("GET", "/repository/nuget-hosted/v3/index.json").json().containsKey("resources"), "NuGet V3 service discovery works");
                check(request("GET", "/repository/nuget-hosted/v3/flatcontainer/graph.demo/index.json").text().contains("1.0.0"), "NuGet version index is available");
                check(Arrays.equals(request("GET", "/repository/nuget-hosted/v3/flatcontainer/graph.demo/1.0.0/graph.demo.1.0.0.nupkg").bytes.toByteArray(), nuget), "NuGet flat-container download preserves bytes");
                check(request("GET", "/repository/nuget-hosted/v3/registration/graph.demo/index.json").text().contains("Graph.Dependency"), "NuGet registration includes dependency groups");
                check(request("PUT", "/repository/nuget-hosted/v2/package", Map.of("Content-Type", "application/octet-stream"), fixture("unsafe.nupkg"), admin).status == 400, "NuGet nuspec external entities are rejected");
                check(request("DELETE", "/repository/nuget-hosted/v2/package/Graph.Demo/1.0.0").status == 200, "NuGet unlist works");
                check(request("GET", "/repository/nuget-hosted/v3/registration/graph.demo/index.json").text().contains("\"listed\":false"), "NuGet unlisted state is reflected in registration");
                check(request("GET", "/v2/").status == 200, "OCI registry probe works");
                var upload = request("POST", "/v2/docker-hosted/demo/blobs/uploads/");
                check(upload.status == 202 && upload.headers.containsKey("Docker-Upload-UUID"), "OCI upload session starts");
                String uploadPath = URI.create(upload.headers.get("Location")).getPath();
                check(request("PATCH", uploadPath, Map.of("Content-Range", "0-2"), Arrays.copyOfRange(content, 0, 3), admin).status == 202, "OCI chunk upload commits an offset");
                String digest = "sha256:" + ContentStore.digest(new String(content, StandardCharsets.UTF_8));
                check(request("PUT", uploadPath + "?digest=" + digest, Map.of(), Arrays.copyOfRange(content, 3, content.length), admin).status == 201, "OCI blob finalization checks digest");
                check(Arrays.equals(request("GET", "/v2/docker-hosted/demo/blobs/" + digest).bytes.toByteArray(), content), "OCI blob download preserves bytes");
                String media = "application/vnd.oci.image.manifest.v1+json";
                var manifest = Json.map("schemaVersion", 2, "mediaType", media, "config", Json.map("mediaType", "application/vnd.oci.image.config.v1+json", "digest", digest, "size", content.length), "layers", List.of());
                check(request("PUT", "/v2/docker-hosted/demo/manifests/1.0", Map.of("Content-Type", media), Json.stringify(manifest).getBytes(StandardCharsets.UTF_8), admin).status == 201, "OCI manifest publication validates referenced content");
                check(request("GET", "/v2/docker-hosted/demo/tags/list").text().contains("1.0"), "OCI tags are listed");
                check(request("GET", "/v2/_catalog").text().contains("docker-hosted/demo"), "OCI catalog is filtered through readable repositories");
                for (String path : List.of("/repository/raw-hosted/../a", "/repository/raw-hosted/%2e%2e/a", "/repository/raw-hosted/%252e%252e/a", "/repository/raw-hosted/%5ca", "/api%2flogin", "/repository/raw-hosted/a//b", "/repository/raw-hosted/%c0%af"))
                    rejects(() -> RequestPaths.decode(path), 400, "Reject ambiguous path: " + path);
                check(RequestPaths.decode("/repository/raw-hosted/a+b.txt").endsWith("a+b.txt"), "Path decoding does not turn plus into space");
                check(Boolean.FALSE.equals(administration.stats(admin).get("licenseQuotas")), "Administrative stats have no license quota gate");
                store.requests.set(2_000_000);
                check(request("GET", "/repository/raw-hosted/a.bin").status == 200, "Request metric above one million does not block reads");
                var beforeDisable = identity.find("reader");
                var disabledToken = users.createToken(beforeDisable, Json.map("label", "disable-check"));
                users.save(admin, "reader", Json.map("disabled", true), false);
                users.save(admin, "reader", Json.map("disabled", false), false);
                check(!identity.sessionValid(beforeDisable), "Disable and re-enable does not resurrect an old session");
                check(identity.token((String) disabledToken.get("token")) == null, "Disable and re-enable does not resurrect old API tokens");
                var oldReader = identity.find("reader"); var readerToken = users.createToken(oldReader, Json.map("label", "reader"));
                users.changePassword(oldReader, "reader-test-password-87221", "reader-new-password-781234");
                check(!identity.sessionValid(oldReader), "Password change invalidates previously saved session identity");
                check(identity.token((String) readerToken.get("token")) == null, "Password change revokes API tokens atomically");
                check(identity.authenticatePassword("reader", "reader-new-password-781234", "new-login") != null, "New password authenticates");
                check(Boolean.TRUE.equals(MaintenanceService.verify(store).get("ok")), "All published blobs pass integrity verification");
                OfflineOperations.execute(store, "backup", parent.resolve("backup.zip").toString(), "");
                check(Files.size(parent.resolve("backup.zip")) > 0, "Offline backup is generated outside the data directory");
            }
            try (var reopened = new ContentStore(settings)) {
                check(reopened.asset("raw-hosted", "a.bin") != null, "Migrated storage survives close and reopen");
                check(Boolean.TRUE.equals(MaintenanceService.verify(reopened).get("ok")), "Reopened storage retains blob integrity");
            }
            System.out.println("CORE_CONTRACT_TESTS_PASSED=" + passed);
        } finally {
            try (var paths = Files.walk(parent)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
