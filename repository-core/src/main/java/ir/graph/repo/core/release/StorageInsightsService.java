package ir.graph.repo.core.release;

import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** Permission-scoped, measured storage accounting; never estimates currency savings. */
public final class StorageInsightsService {
    private final RepositoryService repositories;
    private final ContentStore store;
    public StorageInsightsService(RepositoryService repositories) { this.repositories = repositories; this.store = repositories.store(); }
    public Map<String, Object> inspect(RepositoryPrincipal p) throws IOException {
        repositories.permissions().require(p);
        synchronized (store) {
            var repos = store.all("repos").stream().filter(r -> repositories.permissions().allowed(p, r, "read")).toList();
            Set<String> names = new HashSet<>(); repos.forEach(r -> names.add(Json.str(r, "name", "")));
            var assets = store.all("assets").stream().filter(a -> names.contains(a.get("repo"))).toList();
            Map<String, Long> unique = new HashMap<>(); Map<String, long[]> repoTotals = new HashMap<>();
            long logical = 0; int held = 0;
            for (var asset : assets) { String sha = Json.str(asset, "sha256", ""); long bytes = Json.num(asset, "size", 0);
                logical += bytes; unique.putIfAbsent(sha, bytes); var total = repoTotals.computeIfAbsent(Json.str(asset, "repo", ""), k -> new long[2]); total[0]++; total[1] += bytes; if (store.isHeld(sha)) held++; }
            long uniqueBytes = unique.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Long> pinned = new HashMap<>(); Map<String, Long> stages = new TreeMap<>(); int releases = 0;
            for (var release : store.all("releases")) {
                var manifest = Json.array(release.get("manifest"));
                if (!manifest.stream().allMatch(a -> names.contains(Json.object(a).get("repo")))) continue;
                releases++; stages.merge(Json.str(release, "state", ""), 1L, Long::sum);
                for (Object value : manifest) { var a = Json.object(value); pinned.put(Json.str(a, "sha256", ""), Json.num(a, "size", 0)); }
            }
            long pinnedOnly = pinned.entrySet().stream().filter(e -> !unique.containsKey(e.getKey())).mapToLong(Map.Entry::getValue).sum();
            List<Object> recommendations = new ArrayList<>(); List<Object> breakdown = new ArrayList<>();
            for (var repo : repos) {
                String name = Json.str(repo, "name", ""); var totals = repoTotals.getOrDefault(name, new long[2]); long count = totals[0], bytes = totals[1];
                breakdown.add(Json.map("name", name, "format", repo.get("format"), "type", repo.get("type"), "assets", count, "logicalBytes", bytes));
                if (Boolean.TRUE.equals(repo.get("redeploy"))) recommendations.add(Json.map("code", "MUTABLE_REPOSITORY", "repo", name));
                if (Boolean.TRUE.equals(repo.get("anonymous"))) recommendations.add(Json.map("code", "PUBLIC_DOWNLOADS", "repo", name));
                if (Boolean.TRUE.equals(repo.get("offline"))) recommendations.add(Json.map("code", "OFFLINE_CACHE", "repo", name));
            }
            if (held > 0) recommendations.add(Json.map("code", "HELD_CONTENT", "count", held));
            if (pinnedOnly > 0) recommendations.add(Json.map("code", "PINNED_HISTORY", "bytes", pinnedOnly));
            var result = Json.map("measuredAt", Instant.now().toString(), "logicalBytes", logical, "liveUniqueBytes", uniqueBytes,
                    "deduplicatedBytes", logical - uniqueBytes, "pinnedOnlyBytes", pinnedOnly, "retainedUniqueBytes", uniqueBytes + pinnedOnly,
                    "deduplicationPercent", logical == 0 ? 0 : Math.round((logical - uniqueBytes) * 1000.0 / logical) / 10.0,
                    "assets", assets.size(), "quarantinedReferences", held, "releaseCount", releases, "releaseStates", stages,
                    "repositories", breakdown, "recommendations", recommendations,
                    "scope", p.admin() ? "visible-repositories-and-host" : "visible-repositories", "includesUnreferencedBlobs", false);
            if (p.admin()) {
                var disk = Files.getFileStore(store.home()); result.put("diskTotalBytes", disk.getTotalSpace()); result.put("diskUsableBytes", disk.getUsableSpace());
                if (disk.getTotalSpace() > 0 && disk.getUsableSpace() < disk.getTotalSpace() * 0.15) recommendations.add(Json.map("code", "DISK_PRESSURE"));
            }
            return result;
        }
    }
}
