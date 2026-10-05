package ir.graph.repo.core.release;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Immutable, multi-repository release snapshots with optimistic concurrency and dual control. */
public final class ReleaseService {
    public static final Set<String> STATES = Set.of("DRAFT", "IN_REVIEW", "APPROVED", "RELEASED", "REJECTED", "REVOKED");
    private final RepositoryService repositories;
    private final ContentStore store;
    private final EvidenceSigner signer;
    public ReleaseService(RepositoryService repositories, EvidenceSigner signer) {
        this.repositories = repositories; this.store = repositories.store(); this.signer = signer;
    }
    private Map<String, Object> lookup(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw RepositoryException.missing();
        var value = store.get("releases", id); if (value == null) throw RepositoryException.missing(); return value;
    }
    private boolean permitted(RepositoryPrincipal p, Map<String, Object> release, String action) {
        if (p == null) return false;
        for (Object item : Json.array(release.get("manifest"))) {
            var repo = store.get("repos", Json.str(Json.object(item), "repo", ""));
            if (repo == null || !repositories.permissions().allowed(p, repo, "read")
                    || !repositories.permissions().allowed(p, repo, action)) return false;
        }
        return true;
    }
    private void require(RepositoryPrincipal p, Map<String, Object> release, String action) {
        repositories.permissions().require(p);
        if (!permitted(p, release, action)) throw new RepositoryException(403, "FORBIDDEN", "Permission is required on every repository in this release");
    }
    private List<String> actions(RepositoryPrincipal p, Map<String, Object> release) {
        List<String> result = new ArrayList<>(); String state = Json.str(release, "state", "");
        boolean owner = p.admin() || p.username().equals(release.get("createdBy"));
        boolean writer = owner && permitted(p, release, "write");
        if (writer && state.equals("DRAFT")) result.add("submit");
        if (state.equals("IN_REVIEW") && !p.username().equals(release.get("createdBy")) && permitted(p, release, "approve")) {
            result.add("approve"); result.add("reject");
        }
        if (writer && state.equals("APPROVED")) result.add("release");
        if (writer && state.equals("RELEASED")) result.add("revoke");
        return result;
    }
    public Map<String, Object> get(RepositoryPrincipal p, String id) {
        var release = lookup(id); require(p, release, "read"); release.put("allowedActions", actions(p, release)); return release;
    }
    public Map<String, Object> list(RepositoryPrincipal p, String state, int offset, int limit) {
        repositories.permissions().require(p);
        if (offset < 0 || limit < 1 || limit > 200 || (!state.isEmpty() && !STATES.contains(state))) throw RepositoryException.bad("Invalid release query");
        var values = store.all("releases").stream().filter(r -> permitted(p, r, "read") && (state.isEmpty() || state.equals(r.get("state"))))
                .sorted(Comparator.comparing((Map<String, Object> r) -> Json.str(r, "updated", "")).reversed()).toList();
        return Json.map("total", values.size(), "offset", offset, "limit", limit,
                "items", values.stream().skip(offset).limit(limit).map(r -> summary(p, r)).toList());
    }
    private Map<String, Object> summary(RepositoryPrincipal p, Map<String, Object> value) {
        var result = new LinkedHashMap<>(value); result.remove("manifest"); result.remove("evidence"); result.remove("events");
        result.put("allowedActions", actions(p, value)); return result;
    }
    public Map<String, Object> create(RepositoryPrincipal p, Map<String, Object> input) throws IOException {
        repositories.permissions().require(p);
        String name = text(input, "name", 100), version = text(input, "version", 100);
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,99}") || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,99}"))
            throw RepositoryException.bad("Use an ASCII release name and version; put descriptive text in the note");
        String note = Json.str(input, "note", "").strip(); if (note.length() > 2000) throw RepositoryException.bad("Release note is too long");
        var refs = Json.array(input.getOrDefault("assets", List.of())); if (refs.isEmpty()) throw RepositoryException.bad("A release must contain at least one artifact");
        synchronized (store) {
            for (var existing : store.all("releases")) if (name.equals(existing.get("name")) && version.equals(existing.get("version")))
                throw new RepositoryException(409, "RELEASE_EXISTS", "This release name and version already exist");
            List<Map<String, Object>> manifest = new ArrayList<>(); Set<String> seen = new HashSet<>(); long bytes = 0;
            for (Object ref : refs) {
                var item = Json.object(ref); String repoName = Json.str(item, "repo", ""), path = ContentStore.safePath(Json.str(item, "path", ""));
                var repo = repositories.get(repoName);
                repositories.permissions().repository(p, repo, "read"); repositories.permissions().repository(p, repo, "write");
                if (!seen.add(ContentStore.key(repoName, path))) throw RepositoryException.bad("Duplicate release artifact");
                var asset = store.asset(repoName, path); if (asset == null) throw RepositoryException.missing();
                store.assertDownloadAllowed(Json.str(asset, "sha256", ""));
                var frozen = Json.map("repo", repoName, "path", path, "format", repo.get("format"), "sha256", asset.get("sha256"),
                        "size", asset.get("size"), "contentType", asset.get("contentType"), "updated", asset.get("updated"));
                manifest.add(frozen); bytes = Math.addExact(bytes, Json.num(asset, "size", 0));
            }
            manifest.sort(Comparator.comparing(a -> a.get("repo") + "/" + a.get("path")));
            String now = Instant.now().toString(), id = UUID.randomUUID().toString();
            var release = Json.map("id", id, "name", name, "version", version, "note", note, "state", "DRAFT", "revision", 1,
                    "created", now, "updated", now, "createdBy", p.username(), "assetCount", manifest.size(), "logicalBytes", bytes,
                    "manifest", manifest, "manifestDigest", EvidenceEnvelope.digest(manifest), "events", new ArrayList<>());
            event(release, p.username(), "create", note); store.put("releases", id, release); return get(p, id);
        }
    }
    public Map<String, Object> transition(RepositoryPrincipal p, String id, String action, Map<String, Object> input) throws IOException {
        synchronized (store) {
            var release = lookup(id); require(p, release, "read");
            if (Json.num(input, "expectedRevision", -1) != Json.num(release, "revision", 0))
                throw new RepositoryException(409, "STALE_RELEASE", "Release changed; reload before making a decision");
            String note = text(input, "note", 2000);
            if (!actions(p, release).contains(action)) {
                if (action.equals("approve") && p.username().equals(release.get("createdBy")))
                    throw new RepositoryException(403, "SELF_APPROVAL", "A second person must approve; administrators cannot approve their own releases");
                throw new RepositoryException(409, "RELEASE_TRANSITION", "This action is not permitted for the current release and identity");
            }
            if (Set.of("submit", "approve", "release").contains(action)) {
                var inspection = inspect(release, true);
                if (!Boolean.TRUE.equals(inspection.get("integrityOk")))
                    throw new RepositoryException(409, "RELEASE_INTEGRITY", "A release artifact is missing, quarantined or fails integrity verification");
            }
            release.put("state", switch (action) {
                case "submit" -> "IN_REVIEW"; case "approve" -> "APPROVED"; case "reject" -> "REJECTED";
                case "release" -> "RELEASED"; case "revoke" -> "REVOKED";
                default -> throw RepositoryException.bad("Unknown transition");
            });
            release.put("updated", Instant.now().toString()); release.put("revision", Json.num(release, "revision", 0) + 1);
            event(release, p.username(), action, note);
            if (action.equals("release")) {
                var payload = new LinkedHashMap<>(release); payload.put("schema", "graph-atlas.release/v1");
                release.put("evidence", signer.sign(payload));
            }
            store.put("releases", id, release); return get(p, id);
        }
    }
    private static String text(Map<String, Object> input, String key, int max) {
        String value = Json.str(input, key, "").strip();
        if (value.isEmpty() || value.length() > max || value.codePoints().anyMatch(c -> c < 32 && c != 10 && c != 9))
            throw RepositoryException.bad("Invalid " + key); return value;
    }
    private static void event(Map<String, Object> release, String actor, String action, String note) {
        List<Object> events = Json.array(release.get("events"));
        String previous = events.isEmpty() ? "0".repeat(64) : Json.str(Json.object(events.getLast()), "hash", "");
        var next = Json.map("sequence", events.size() + 1, "time", Instant.now().toString(), "actor", actor,
                "action", action, "state", release.get("state"), "note", note, "previous", previous);
        next.put("hash", EvidenceEnvelope.digest(next)); events.add(next);
    }
    public static boolean validHistory(Map<String, Object> release) {
        try {
            String previous = "0".repeat(64); long sequence = 0;
            for (Object value : Json.array(release.get("events"))) {
                var item = new LinkedHashMap<>(Json.object(value)); String hash = Json.str(item, "hash", ""); item.remove("hash");
                if (!previous.equals(item.get("previous")) || Json.num(item, "sequence", 0) != ++sequence || !hash.equals(EvidenceEnvelope.digest(item))) return false;
                previous = hash;
            }
            return sequence > 0;
        } catch (RuntimeException e) { return false; }
    }
    private Map<String, Object> inspect(Map<String, Object> release, boolean deep) throws IOException {
        List<Object> problems = new ArrayList<>(); Set<String> digests = new HashSet<>(); int drift = 0;
        if (!Json.str(release, "manifestDigest", "").equals(EvidenceEnvelope.digest(release.get("manifest")))) problems.add(Json.map("reason", "manifest"));
        if (!validHistory(release)) problems.add(Json.map("reason", "history"));
        for (Object item : Json.array(release.get("manifest"))) {
            var a = Json.object(item); String sha = Json.str(a, "sha256", "");
            var current = store.asset(Json.str(a, "repo", ""), Json.str(a, "path", ""));
            if (current == null || !sha.equals(current.get("sha256"))) drift++;
            if (store.isHeld(sha)) problems.add(Json.map("sha256", sha, "reason", "held"));
            if (!digests.add(sha)) continue;
            Path file = store.blobPath(sha);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { problems.add(Json.map("sha256", sha, "reason", "missing")); continue; }
            if (Files.size(file) != Json.num(a, "size", -1)) problems.add(Json.map("sha256", sha, "reason", "size"));
            if (deep) {
                var hasher = ContentStore.hash("SHA-256");
                try (InputStream in = Files.newInputStream(file)) { byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) hasher.update(buffer, 0, n); }
                if (!sha.equals(HexFormat.of().formatHex(hasher.digest()))) problems.add(Json.map("sha256", sha, "reason", "checksum"));
            }
        }
        return Json.map("integrityOk", problems.isEmpty(), "deep", deep, "checkedDigests", digests.size(),
                "sourceDrift", drift, "problems", problems, "checkedAt", Instant.now().toString());
    }
    public Map<String, Object> gate(RepositoryPrincipal p, String id, boolean deep) throws IOException {
        synchronized (store) {
            var release = lookup(id); require(p, release, "read"); var result = inspect(release, deep);
            boolean signature = false;
            if (release.get("evidence") instanceof Map<?, ?>) {
                try {
                    var payload = EvidenceEnvelope.verify(Json.object(release.get("evidence")), Json.str(signer.identity(), "keyId", ""));
                    signature = id.equals(payload.get("id")) && "RELEASED".equals(payload.get("state"))
                            && release.get("manifestDigest").equals(payload.get("manifestDigest"));
                } catch (java.security.GeneralSecurityException e) { signature = false; }
            }
            result.putAll(Json.map("releaseId", id, "state", release.get("state"), "revision", release.get("revision"),
                    "signatureValid", signature, "allowed", "RELEASED".equals(release.get("state")) && signature && Boolean.TRUE.equals(result.get("integrityOk")),
                    "delivery", "pinned-release-assets", "pointInTime", true)); return result;
        }
    }
    public Map<String, Object> evidence(RepositoryPrincipal p, String id) {
        var release = lookup(id); require(p, release, "read");
        if (!(release.get("evidence") instanceof Map<?, ?>)) throw new RepositoryException(409, "NO_EVIDENCE", "Evidence is issued when the release is published");
        return Json.object(release.get("evidence"));
    }
    public Map<String, Object> download(RepositoryPrincipal p, String id, int index) {
        synchronized (store) {
            var release = lookup(id); require(p, release, "read");
            if (!"RELEASED".equals(release.get("state"))) throw new RepositoryException(409, "RELEASE_NOT_AVAILABLE", "Only released snapshots can be downloaded");
            var manifest = Json.array(release.get("manifest"));
            if (index < 0 || index >= manifest.size()) throw RepositoryException.missing();
            var asset = Json.object(manifest.get(index)); store.assertDownloadAllowed(Json.str(asset, "sha256", "")); return asset;
        }
    }
    public Map<String, Object> identity(RepositoryPrincipal p) throws IOException { repositories.permissions().admin(p); return signer.identity(); }
}
