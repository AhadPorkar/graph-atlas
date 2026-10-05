package ir.graph.repo.core.release;

import ir.graph.repo.core.util.Json;
import java.nio.file.*;

/** Offline signature verification. This does not establish current revocation or scan packages. */
public final class EvidenceVerifierMain {
    private EvidenceVerifierMain() { }
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: verify-evidence <envelope.json> <trusted-public-key-sha256>"); System.exit(2);
        }
        try {
            var payload = EvidenceEnvelope.verify(Json.obj(Files.readString(Path.of(args[0]))), args[1]);
            System.out.println(Json.stringify(Json.map("signatureValid", true, "releaseId", payload.get("id"),
                    "stateAtSigning", payload.get("state"), "currentRevocationChecked", false,
                    "artifactBytesChecked", false, "payload", payload)));
        } catch (Exception e) { System.err.println("Evidence verification failed: " + e.getMessage()); System.exit(1); }
    }
}
