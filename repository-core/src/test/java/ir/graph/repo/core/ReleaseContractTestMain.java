package ir.graph.repo.core;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.protocol.*;
import ir.graph.repo.core.release.*;
import ir.graph.repo.core.security.PermissionService;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Executed against the actual core; no fixture implementation of release rules. */
public final class ReleaseContractTestMain {
    private static int count;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); count++; System.out.println("PASS " + label); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void rejects(int code, Action action, String label) throws Exception {
        try { action.run(); throw new AssertionError("Accepted: " + label); }
        catch (RepositoryException e) { check(e.status == code, label + " (" + e.code + ")"); }
    }
    private static RepositoryPrincipal principal(String name, boolean admin, Map<String, Object> grants) { return new RepositoryPrincipal(name, admin, grants, "test"); }
    private static Map<String, Object> change(Map<String, Object> value) { return Json.map("expectedRevision", value.get("revision"), "note", "Test decision with independent evidence"); }
    private static Map<String, Object> create(String name, Object... refs) { return Json.map("name", name, "version", "1.0.0", "assets", Arrays.asList(refs)); }
    private static Map<String, Object> ref(String repo, String path) { return Json.map("repo", repo, "path", path); }
    private static final class Exchange implements ProtocolExchange {
        final String method; final Map<String, String> headers; final ByteArrayOutputStream out = new ByteArrayOutputStream(); int status;
        Exchange(String method, Map<String, String> headers) { this.method = method; this.headers = headers; }
        public String method() { return method; } public URI uri() { return URI.create("/test"); }
        public String header(String key) { return headers.get(key); } public InputStream input() { return InputStream.nullInputStream(); }
        public OutputStream output() { return out; }
        public ResponseHeaders responseHeaders() { return new ResponseHeaders() { public void set(String a, String b) { } public void add(String a, String b) { } }; }
        public void respond(int code, long bytes) { status = code; }
    }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("atlas-release-");
        var config = RepositorySettings.load(Map.of("GR_HOME", home.toString()));
        String retainedId = "", fingerprint = "", pinnedSha = "";
        try {
            try (var store = new ContentStore(config); var upstream = new UpstreamClient(config)) {
                var perms = new PermissionService(); var repo = new RepositoryService(store, config, perms, upstream);
                var admin = principal("author", true, Json.map()); var reviewer = principal("reviewer", false, Json.map("raw", List.of("read", "approve")));
                var writer = principal("writer", false, Json.map("raw", List.of("read", "write")));
                var reader = principal("reader", false, Json.map("raw", List.of("read")));
                var outsider = principal("outsider", false, Json.map());
                var administration = new RepositoryAdminService(repo);
                administration.save(admin, null, Json.map("name", "raw", "format", "raw", "redeploy", true), true);
                administration.save(admin, null, Json.map("name", "secret", "format", "raw"), true);
                byte[] original = "fixed release payload".getBytes(StandardCharsets.UTF_8);
                var blob = store.blob(original); pinnedSha = blob.sha256();
                var asset = store.saveAsset("raw", "one.bin", blob, "application/octet-stream", Json.map(), true);
                store.saveAsset("raw", "same.bin", blob, "application/octet-stream", Json.map(), true);
                store.saveAsset("secret", "hidden.bin", store.blob("private".getBytes(StandardCharsets.UTF_8)), "application/octet-stream", Json.map(), false);
                var signer = new EvidenceSigner(store); var releases = new ReleaseService(repo, signer); var holds = new QuarantineService(store, perms);
                var analytics = new StorageInsightsService(repo);
                rejects(401, () -> releases.list(null, "", 0, 100), "Unauthenticated release listing rejected");
                rejects(403, () -> releases.create(reader, create("bad", ref("raw", "one.bin"))), "Read-only users cannot freeze releases");
                rejects(400, () -> releases.create(admin, create("empty")), "Empty release rejected");
                rejects(400, () -> releases.create(admin, create("duplicate", ref("raw", "one.bin"), ref("raw", "one.bin"))), "Duplicate asset reference rejected");
                rejects(404, () -> releases.create(admin, create("missing", ref("raw", "missing.bin"))), "Missing asset rejected");
                rejects(403, () -> releases.create(writer, create("mixed", ref("raw", "one.bin"), ref("secret", "hidden.bin"))), "Cross-repository authorization is all-or-nothing");
                var release = releases.create(admin, create("Checkout", ref("raw", "one.bin")));
                String id = Json.str(release, "id", ""); retainedId = id;
                check("DRAFT".equals(release.get("state")), "Capsule starts in draft");
                check(ReleaseService.validHistory(release), "History hash chain starts valid");
                check(release.get("manifestDigest").equals(EvidenceEnvelope.digest(release.get("manifest"))), "Manifest fingerprint is reproducible");
                rejects(409, () -> releases.create(admin, create("Checkout", ref("raw", "one.bin"))), "Release version cannot be replaced");
                check(Json.num(releases.list(outsider, "", 0, 100), "total", -1) == 0, "Unreadable capsules excluded from listing");
                rejects(403, () -> releases.get(outsider, id), "Direct capsule access respects source permissions");
                check(!(boolean)releases.gate(reader, id, true).get("allowed"), "Draft cannot pass deployment gate");
                rejects(409, () -> releases.evidence(reader, id), "Unsigned draft has no downloadable evidence");
                rejects(409, () -> releases.download(reader, id, 0), "Draft pinned content cannot be downloaded");
                rejects(409, () -> releases.transition(admin, id, "submit", Json.map("note", "Missing expected revision")), "Revision is mandatory");
                rejects(400, () -> releases.transition(admin, id, "submit", Json.map("expectedRevision", 1, "note", "")), "Decision explanation is mandatory");
                release = releases.transition(admin, id, "submit", change(release));
                check("IN_REVIEW".equals(release.get("state")), "Submission advances to review");
                Map<String, Object> pending = release;
                rejects(403, () -> releases.transition(admin, id, "approve", change(pending)), "Even admin cannot self-approve");
                rejects(409, () -> releases.transition(writer, id, "approve", change(pending)), "Write is not the approve permission");
                rejects(409, () -> releases.transition(reviewer, id, "approve", Json.map("expectedRevision", 1, "note", "stale")), "Stale reviewer cannot approve");
                release = releases.transition(reviewer, id, "approve", change(release));
                check("APPROVED".equals(release.get("state")), "Independent reviewer approval succeeds");
                release = releases.transition(admin, id, "release", change(release));
                check("RELEASED".equals(release.get("state")), "Approved release can be published");
                fingerprint = Json.str(signer.identity(), "keyId", "");
                var evidence = releases.evidence(reader, id);
                var payload = EvidenceEnvelope.verify(evidence, fingerprint);
                check(payload.get("manifestDigest").equals(release.get("manifestDigest")), "Ed25519 signature validates the frozen manifest");
                check(Json.array(payload.get("events")).size() == 4, "Signed evidence includes release decision history");
                try { EvidenceEnvelope.verify(evidence, "0".repeat(64)); throw new AssertionError("Untrusted key accepted"); }
                catch (GeneralSecurityException e) { check(true, "External trusted fingerprint is mandatory"); }
                var broken = new LinkedHashMap<>(evidence); broken.put("payload", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
                try { EvidenceEnvelope.verify(broken, fingerprint); throw new AssertionError("Modified signature accepted"); }
                catch (GeneralSecurityException e) { check(true, "Modified evidence fails signature verification"); }
                check((boolean) releases.gate(reader, id, true).get("allowed"), "Released capsule passes deep gate");
                check(blob.sha256().equals(releases.download(reader, id, 0).get("sha256")), "Pinned delivery resolves frozen digest");
                rejects(404, () -> releases.download(reader, id, 9), "Invalid pinned artifact index rejected");
                var meter = analytics.inspect(reader);
                check(Json.num(meter, "assets", -1) == 2, "Storage insight excludes unreadable repository");
                check(Json.num(meter, "deduplicatedBytes", -1) == original.length, "Deduplication is measured from actual shared bytes");
                check(!meter.containsKey("diskUsableBytes"), "Host capacity is not disclosed to ordinary users");
                check(analytics.inspect(admin).containsKey("diskUsableBytes"), "Administrators see actual file-system capacity");
                rejects(403, () -> holds.list(reader), "Quarantine registry is admin-only");
                rejects(400, () -> holds.save(admin, Json.map("sha256", blob.sha256(), "reason", "x", "expectedRevision", 0)), "Quarantine requires an explicit active state");
                var held = holds.save(admin, Json.map("sha256", blob.sha256(), "reason", "Under investigation", "active", true, "expectedRevision", 0));
                check(store.isHeld(blob.sha256()), "Digest hold persisted");
                rejects(423, () -> ProtocolIO.asset(new Exchange("GET", Map.of()), store, asset, true), "Quarantine blocks normal binary GET");
                rejects(423, () -> ProtocolIO.asset(new Exchange("HEAD", Map.of()), store, asset, true), "Quarantine blocks HEAD");
                rejects(423, () -> ProtocolIO.asset(new Exchange("GET", Map.of("Range", "bytes=0-2")), store, asset, true), "Range request cannot bypass quarantine");
                rejects(423, () -> ProtocolIO.asset(new Exchange("GET", Map.of("If-None-Match", "*")), store, asset, true), "Conditional 304 cannot bypass quarantine");
                rejects(423, () -> releases.download(reader, id, 0), "Pinned release delivery also checks quarantine");
                check(!(boolean)releases.gate(reader, id, true).get("allowed"), "Quarantine fails a previously released deployment gate");
                rejects(423, () -> releases.create(admin, create("Held", ref("raw", "same.bin"))), "New alias cannot bypass digest quarantine");
                rejects(409, () -> holds.save(admin, Json.map("sha256", blob.sha256(), "active", false, "reason", "stale", "expectedRevision", 0)), "Stale hold resolution rejected");
                holds.save(admin, Json.map("sha256", blob.sha256(), "active", false, "reason", "Cleared", "expectedRevision", held.get("revision")));
                check(!store.isHeld(blob.sha256()), "Explicit resolution restores digest availability");
                check((boolean)releases.gate(reader, id, true).get("allowed"), "Gate recovers after authorized hold resolution");
                // Replace and remove the live sources. The release must retain the original bytes.
                store.saveAsset("raw", "one.bin", store.blob("new payload".getBytes(StandardCharsets.UTF_8)), "application/octet-stream", Json.map(), true);
                store.delete("assets", ContentStore.key("raw", "same.bin"));
                var drifted = releases.gate(reader, id, true);
                check((boolean)drifted.get("allowed") && Json.num(drifted, "sourceDrift", 0) == 1, "Source drift is reported; pinned delivery stays valid");
                check(Arrays.equals(original, Files.readAllBytes(store.blobPath(blob.sha256()))), "Pinned bytes do not follow live-path replacement");
                check(Json.num(analytics.inspect(reader), "pinnedOnlyBytes", 0) == original.length, "Pinned historical bytes accounted separately");
                Files.setLastModifiedTime(store.blobPath(blob.sha256()), FileTime.from(Instant.now().minusSeconds(172800)));
                var garbage = store.blob("unreferenced garbage".getBytes(StandardCharsets.UTF_8));
                Files.setLastModifiedTime(store.blobPath(garbage.sha256()), FileTime.from(Instant.now().minusSeconds(172800)));
                store.garbageCollect(false);
                check(Files.exists(store.blobPath(blob.sha256())), "Garbage collection retains released content without live references");
                check(!Files.exists(store.blobPath(garbage.sha256())), "Unreferenced old blob is still collectible");
                rejects(409, () -> administration.delete(admin, "raw", Json.map("confirm", "raw", "purge", true)), "Referenced repositories cannot be purged");
                Files.write(store.blobPath(blob.sha256()), "corrupt".getBytes(StandardCharsets.UTF_8));
                check(!(boolean)releases.gate(reader, id, true).get("allowed"), "Modified bytes fail the deep deployment gate");
                check(!Boolean.TRUE.equals(MaintenanceService.verify(store).get("ok")), "Maintenance verifies pinned-only content too");
                Files.write(store.blobPath(blob.sha256()), original);
                // Evidence remains a historical receipt after revocation, not a claim of present validity.
                release = releases.transition(admin, id, "revoke", change(release));
                check("REVOKED".equals(release.get("state")), "Release can be revoked with a reason");
                check(!(boolean)releases.gate(reader, id, true).get("allowed"), "Revoked release fails gate");
                rejects(409, () -> releases.download(reader, id, 0), "Revoked snapshot cannot be downloaded");
                check("RELEASED".equals(EvidenceEnvelope.verify(releases.evidence(reader, id), fingerprint).get("state")), "Offline signature is historical; revocation is an online check");
                // Concurrent reviewers: exactly one stale revision wins.
                var race = releases.create(admin, create("Race", ref("raw", "one.bin")));
                String raceId = Json.str(race, "id", ""); race = releases.transition(admin, raceId, "submit", change(race));
                var decision = change(race); var pool = Executors.newFixedThreadPool(2);
                try {
                    Callable<Boolean> attempt = () -> { try { releases.transition(reviewer, raceId, "approve", decision); return true; }
                        catch (RepositoryException e) { if (e.status != 409) throw e; return false; } };
                    var results = pool.invokeAll(List.of(attempt, attempt)); int wins = 0; for (var future : results) if (future.get()) wins++;
                    check(wins == 1, "Concurrent approvals commit exactly one revision");
                } finally { pool.shutdownNow(); }
                var rejected = releases.create(admin, create("Rejected", ref("raw", "one.bin")));
                String rejectId = Json.str(rejected, "id", ""); rejected = releases.transition(admin, rejectId, "submit", change(rejected));
                rejected = releases.transition(reviewer, rejectId, "reject", change(rejected));
                check("REJECTED".equals(rejected.get("state")), "Reviewer can reject without author permission");
                check(Json.array(rejected.get("allowedActions")).isEmpty(), "Rejected release is terminal; a new version is required");
                // A missing identity must never silently become a different key on restart.
                Files.move(home.resolve("evidence-key.json"), home.resolve("key-backup.json"));
                try { new EvidenceSigner(store).identity(); throw new AssertionError("Missing key replaced"); }
                catch (IOException e) { check(true, "Missing signing key fails closed when signed releases exist"); }
                Files.move(home.resolve("key-backup.json"), home.resolve("evidence-key.json"));
                store.compact();
            }
            try (var store = new ContentStore(config); var upstream = new UpstreamClient(config)) {
                var repos = new RepositoryService(store, config, new PermissionService(), upstream);
                var signer = new EvidenceSigner(store); var service = new ReleaseService(repos, signer);
                var admin = principal("author", true, Json.map());
                check(fingerprint.equals(signer.identity().get("keyId")), "Signing identity survives store restart");
                check("REVOKED".equals(service.get(admin, retainedId).get("state")), "Release lifecycle survives snapshot and restart");
                check(Files.isRegularFile(store.blobPath(pinnedSha)), "Pinned blob survives compaction and restart");
                check(store.count("holds") == 1, "Resolved quarantine history is durable");
                check(ReleaseService.validHistory(service.get(admin, retainedId)), "Release history remains verifiable after restart");
            }
        } finally { try (var files = Files.walk(home)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); } }
        System.out.println("RELEASE_CONTRACT_TESTS_PASSED=" + count);
    }
}
