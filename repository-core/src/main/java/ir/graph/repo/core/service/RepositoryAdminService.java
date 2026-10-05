package ir.graph.repo.core.service;

import ir.graph.repo.core.domain.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.util.*;

/** Administrative use cases without HTTP routing or a framework dependency. */
public final class RepositoryAdminService {
    private final RepositoryService repositories;
    private final ContentStore store;
    public RepositoryAdminService(RepositoryService repositories) {
        this.repositories = repositories; this.store = repositories.store();
    }
    public List<Map<String, Object>> readable(RepositoryPrincipal p) {
        repositories.permissions().require(p);
        return store.all("repos").stream().filter(r -> p.admin() || repositories.permissions().allowed(p, r, "read"))
                .sorted(Comparator.comparing(r -> (String) r.get("name"))).toList();
    }
    public Map<String, Object> get(RepositoryPrincipal p, String name) {
        var repo = repositories.get(name);
        if (!p.admin()) repositories.permissions().repository(p, repo, "read");
        return repo;
    }
    public Map<String, Object> list(RepositoryPrincipal p) {
        Map<String, Long> counts = new HashMap<>();
        for (var asset : store.all("assets")) counts.merge((String) asset.get("repo"), 1L, Long::sum);
        List<Object> items = new ArrayList<>();
        for (var repo : readable(p)) {
            repo.put("assetCount", counts.getOrDefault((String) repo.get("name"), 0L));
            repo.put("url", repositories.config().base((String) repo.get("name")));
            items.add(repo);
        }
        return Json.map("items", items);
    }
    public Map<String, Object> save(RepositoryPrincipal p, String name, Map<String, Object> input, boolean create) throws IOException {
        repositories.permissions().admin(p);
        var data = new LinkedHashMap<>(input);
        if (name != null) data.put("name", name);
        synchronized (store) {
            var validated = repositories.validate(data);
            String key = (String) validated.get("name");
            boolean exists = store.get("repos", key) != null;
            if (create && exists) throw new RepositoryException(409, "EXISTS", "Repository already exists");
            if (!create && !exists) throw RepositoryException.missing();
            store.put("repos", key, validated);
            return validated;
        }
    }
    public Map<String, Object> delete(RepositoryPrincipal p, String name, Map<String, Object> data) throws IOException {
        repositories.permissions().admin(p);
        if (!name.equals(Json.str(data, "confirm", ""))) throw RepositoryException.bad("Confirm the exact repository name");
        synchronized (store) {
            repositories.get(name);
            for (var release : store.all("releases")) for (Object value : Json.array(release.get("manifest")))
                if (name.equals(Json.object(value).get("repo")))
                    throw new RepositoryException(409, "RELEASE_REFERENCE", "Release snapshots reference this repository; deletion is blocked");
            for (var repo : store.all("repos"))
                if (Json.array(repo.getOrDefault("members", List.of())).contains(name))
                    throw new RepositoryException(409, "GROUP_REFERENCE", "Remove this repository from its groups first");
            var assets = store.assets(name);
            if (!assets.isEmpty() && !Json.bool(data, "purge", false))
                throw new RepositoryException(409, "NOT_EMPTY", "Non-empty repository requires explicit purge confirmation");
            List<Map<String, Object>> operations = new ArrayList<>();
            for (var asset : assets) operations.add(ContentStore.del("assets", ContentStore.key(name, (String) asset.get("path"))));
            for (var doc : store.all("docs")) {
                if (!name.equals(doc.get("repo"))) continue;
                String key = doc.containsKey("key") ? Json.str(doc, "key", "") : "upload:" + doc.get("id");
                operations.add(ContentStore.del("docs", name + "\n" + key));
            }
            operations.add(ContentStore.del("repos", name));
            store.transact(operations);
        }
        return Json.map("ok", true);
    }
    public Map<String, Object> stats(RepositoryPrincipal p) {
        var repos = readable(p);
        Set<String> names = new HashSet<>(); repos.forEach(r -> names.add((String) r.get("name")));
        var assets = store.all("assets").stream().filter(a -> names.contains(a.get("repo"))).toList();
        long logical = 0, unique = 0; Set<String> digests = new HashSet<>();
        for (var asset : assets) {
            long size = Json.num(asset, "size", 0); logical += size;
            if (digests.add((String) asset.get("sha256"))) unique += size;
        }
        return Json.map("repositories", repos.size(), "assets", assets.size(), "logicalBytes", logical,
                "referencedUniqueBytes", unique, "requestsSinceStart", store.requests.get(),
                "started", store.started.toString(), "users", p.admin() ? store.all("users").size() : null,
                "licenseQuotas", false);
    }
    public Map<String, Object> assets(RepositoryPrincipal p, String name, String query, int offset, int limit) {
        if (offset < 0 || limit < 0 || limit > 1000) throw RepositoryException.bad("Invalid pagination");
        Set<String> names = new HashSet<>(); readable(p).forEach(r -> names.add((String) r.get("name")));
        String term = query.toLowerCase(Locale.ROOT);
        var assets = store.all("assets").stream().filter(a -> names.contains(a.get("repo"))
                && (name.isEmpty() || name.equals(a.get("repo")))
                && Json.str(a, "path", "").toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing(a -> a.get("repo") + "/" + a.get("path"))).toList();
        return Json.map("total", assets.size(), "offset", offset, "limit", limit,
                "items", assets.stream().skip(offset).limit(limit).toList());
    }
    public Map<String, Object> yank(RepositoryPrincipal p, Map<String, Object> data) throws IOException {
        var repo = repositories.get(Json.str(data, "repo", ""));
        repositories.permissions().repository(p, repo, "delete"); repositories.hosted(repo);
        if (!repo.get("format").equals("pypi")) throw RepositoryException.bad("Expected PyPI repository");
        Object value = data.getOrDefault("yanked", true);
        if (!(value instanceof String) && !(value instanceof Boolean)) throw RepositoryException.bad("Invalid yanked value");
        synchronized (store) {
            String key = ContentStore.key((String) repo.get("name"), Json.str(data, "path", ""));
            var asset = store.get("assets", key);
            if (asset == null) throw RepositoryException.missing();
            Json.object(asset.get("meta")).put("yanked", value);
            store.put("assets", key, asset);
        }
        return Json.map("ok", true);
    }
}
