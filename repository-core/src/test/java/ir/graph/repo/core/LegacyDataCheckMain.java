package ir.graph.repo.core;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.security.*;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.service.MaintenanceService;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Run after the 0.2.0 server has stopped; verifies real legacy data using the new core. */
public final class LegacyDataCheckMain {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; System.out.println("PASS " + label); }
    public static void main(String[] args) throws Exception {
        var settings = RepositorySettings.load(Map.of("GR_HOME", args[0]));
        try (var store = new ContentStore(settings)) {
            var identity = new IdentityService(store, new LoginThrottle(0, Duration.ofMinutes(1)));
            check(identity.authenticatePassword("admin", System.getenv("LEGACY_TEST_PASSWORD"), "local") != null, "Existing PBKDF2 admin password is accepted");
            check(identity.token(System.getenv("LEGACY_TEST_TOKEN")) != null, "Existing opaque API token is accepted");
            check(store.count("repos") == 7, "Existing default repository definitions are loaded");
            var asset = store.asset("raw-hosted", "legacy/migration.bin");
            check(asset != null, "Existing asset metadata is loaded without conversion");
            check(Files.readString(store.blobPath((String) asset.get("sha256"))).equals("published-by-0.2.0"), "Previously published blob bytes are unchanged");
            check(store.get("docs", "npm-hosted\nnpm:legacy-demo") != null, "Existing npm package metadata is loaded");
            var reader = identity.find("legacy-reader");
            check(reader != null && reader.grants().containsKey("raw-hosted"), "Existing user repository grants are preserved");
            check(Boolean.TRUE.equals(MaintenanceService.verify(store).get("ok")), "All referenced legacy blobs pass SHA-256 verification");
        }
        System.out.println("LEGACY_DATA_COMPATIBILITY_CHECKS_PASSED=" + checks);
        System.out.println("OLD_SERVER_TO_NEW_CORE_ONLY_NOT_A_SPRING_BOOT_RUNTIME_TEST");
    }
}
