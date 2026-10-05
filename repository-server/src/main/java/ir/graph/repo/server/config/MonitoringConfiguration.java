package ir.graph.repo.server.config;

import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.security.LoginThrottle;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.health.contributor.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.Files;

@Configuration(proxyBeanMethods = false)
public class MonitoringConfiguration {
    private final LoginThrottle throttle;
    public MonitoringConfiguration(LoginThrottle throttle) { this.throttle = throttle; }
    @Scheduled(fixedDelayString = "60000") public void expireLoginFailures() { throttle.clean(); }
    @Bean HealthIndicator repositoryStorageHealthIndicator(ContentStore store) {
        return () -> !store.failed() && Files.isReadable(store.home()) && Files.isWritable(store.home())
                ? Health.up().withDetail("storage", "local-content-addressed").build()
                : Health.down().withDetail("storage", "unavailable").build();
    }
    @Bean MeterBinder repositoryMetrics(ContentStore store) {
        return registry -> {
            Gauge.builder("graph.repository.assets", store, s -> s.count("assets")).register(registry);
            Gauge.builder("graph.repository.repositories", store, s -> s.count("repos")).register(registry);
            Gauge.builder("graph.repository.requests.since.start", store, s -> s.requests.get()).register(registry);
        };
    }
}
