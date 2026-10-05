package ir.graph.repo.core.service;

import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.util.Json;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.zip.*;

/** Executed by a non-web Spring Boot context, holding the exclusive data-directory lock. */
public final class OfflineOperations {
    private OfflineOperations() { }
    public static void execute(ContentStore store, String command, String target, String password) throws IOException {
        switch (command) {
            case "verify" -> {
                var result = MaintenanceService.verify(store); System.out.println(Json.stringify(result));
                if (!Boolean.TRUE.equals(result.get("ok"))) throw new IOException("Blob verification failed");
            }
            case "compact" -> { store.compact(); System.out.println("Metadata compacted"); }
            case "backup" -> backup(store, target);
            case "reset-admin-password" -> {
                Passwords.validate(password);
                var user = store.get("users", "admin");
                if (user == null) user = Json.map("username", "admin", "grants", Json.map());
                user.put("admin", true); user.put("disabled", false); user.put("password", Passwords.hash(password));
                List<Map<String, Object>> operations = new ArrayList<>(); operations.add(ContentStore.set("users", "admin", user));
                for (var token : store.all("tokens")) if ("admin".equals(token.get("username"))) operations.add(ContentStore.del("tokens", (String) token.get("id")));
                store.transact(operations); Files.deleteIfExists(store.home().resolve("admin.password"));
                System.out.println("Admin password reset and admin tokens revoked");
            }
            default -> throw new IllegalArgumentException("Unknown maintenance command: " + command);
        }
    }
    private static void backup(ContentStore store, String destination) throws IOException {
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("A backup destination is required");
        Path target = Path.of(destination).toAbsolutePath().normalize();
        Path physicalTarget = target.getParent().toRealPath().resolve(target.getFileName());
        if (physicalTarget.startsWith(store.home().toRealPath()))
            throw new IllegalArgumentException("Backup must be physically outside the data directory, including symbolic links");
        store.compact();
        try {
            try { Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
            catch (UnsupportedOperationException e) { Files.createFile(target); }
            try (var zip = new ZipOutputStream(Files.newOutputStream(target)); var paths = Files.walk(store.home())) {
                for (Path file : paths.toList()) {
                    if (Files.isSymbolicLink(file)) throw new IOException("Refusing to follow a symbolic link in the data directory");
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    String relative = store.home().relativize(file).toString().replace('\\', '/');
                    if (relative.equals(".lock") || relative.equals("admin.password")) continue;
                    zip.putNextEntry(new ZipEntry(relative)); Files.copy(file, zip); zip.closeEntry();
                }
            }
        } catch (FileAlreadyExistsException e) { throw e; }
        catch (IOException e) { Files.deleteIfExists(target); throw e; }
        System.out.println("Backup created: " + target);
    }
}
