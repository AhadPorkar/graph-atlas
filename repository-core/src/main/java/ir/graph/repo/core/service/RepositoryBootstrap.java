package ir.graph.repo.core.service;

import ir.graph.repo.core.util.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class RepositoryBootstrap {
    private RepositoryBootstrap() { }
    public static void seed(RepositoryService repositories) throws IOException {
        var store = repositories.store();
        Path marker = store.home().resolve("initialized");
        if (Files.exists(marker)) return;
        for (String format : List.of("maven", "npm", "nuget", "pypi", "raw", "docker")) {
            String name = format.equals("maven") ? "maven-releases" : format + "-hosted";
            if (store.get("repos", name) == null) store.put("repos", name, repositories.validate(Json.map("name", name,
                    "format", format, "type", "hosted", "versionPolicy", format.equals("maven") ? "release" : "mixed")));
        }
        if (store.get("repos", "maven-snapshots") == null) store.put("repos", "maven-snapshots", repositories.validate(
                Json.map("name", "maven-snapshots", "format", "maven", "type", "hosted", "versionPolicy", "snapshot", "redeploy", true)));
        Files.writeString(marker, "0.5.0-preview\n");
    }
}
