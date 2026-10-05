import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Local readiness probe without shell networking tools or any credentials. */
public final class HealthProbe {
    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault("GR_PORT", "8081");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            int code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code != 200) throw new IllegalStateException("Health status: " + code);
        }
    }
}
