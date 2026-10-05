package ir.graph.repo.server.config;

import ir.graph.repo.core.config.RepositorySettings;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties("graph")
public record RepositoryProperties(
        @NotNull Path home, @NotNull URI publicUrl,
        @Min(0) long maxJsonBytes, @Min(0) long maxUploadBytes, @Min(0) int concurrentRequests,
        boolean allowPrivateUpstream, boolean allowHttpUpstream,
        String bootstrapPassword, boolean cookieSecure,
        @Min(0) int loginFailureLimit, @NotNull Duration loginFailureWindow,
        String command, String commandTarget) {
    public RepositorySettings settings() {
        if (publicUrl == null || home == null) throw new IllegalArgumentException("graph.home and graph.public-url are required");
        if ("https".equals(publicUrl.getScheme()) && !cookieSecure)
            throw new IllegalArgumentException("Set GR_COOKIE_SECURE=true for an HTTPS public URL");
        return RepositorySettings.load(Map.of(
                "GR_HOME", home.toString(), "GR_PUBLIC_URL", publicUrl.toString(),
                "GR_MAX_JSON_BYTES", Long.toString(maxJsonBytes), "GR_MAX_UPLOAD_BYTES", Long.toString(maxUploadBytes),
                "GR_CONCURRENT_REQUESTS", Integer.toString(concurrentRequests),
                "GR_ALLOW_PRIVATE_UPSTREAM", Boolean.toString(allowPrivateUpstream),
                "GR_ALLOW_HTTP_UPSTREAM", Boolean.toString(allowHttpUpstream)));
    }
    public boolean offlineCommand() { return command != null && !command.isBlank(); }
}
