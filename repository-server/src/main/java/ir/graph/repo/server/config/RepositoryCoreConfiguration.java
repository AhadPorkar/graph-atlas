package ir.graph.repo.server.config;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.security.*;
import ir.graph.repo.core.storage.*;
import ir.graph.repo.core.service.*;
import ir.graph.repo.core.release.*;
import ir.graph.repo.core.protocol.*;
import ir.graph.repo.core.protocol.npm.NpmProtocol;
import ir.graph.repo.core.protocol.nuget.NugetProtocol;
import ir.graph.repo.core.protocol.pypi.PypiProtocol;
import ir.graph.repo.core.protocol.oci.OciProtocol;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class RepositoryCoreConfiguration {
    @Bean RepositorySettings repositorySettings(RepositoryProperties p) { return p.settings(); }
    @Bean(destroyMethod = "close") ContentStore contentStore(RepositorySettings settings, RepositoryProperties p, ApplicationContext context) throws IOException {
        if (p.offlineCommand() && context instanceof WebApplicationContext)
            throw new IOException("Offline maintenance requires --graph.command=... and a non-web application context");
        if (p.offlineCommand() && !Files.isRegularFile(settings.home().resolve("metadata.jsonl")))
            throw new IOException("Offline maintenance requires an existing data directory");
        return new ContentStore(settings);
    }
    @Bean AuditLog auditLog(ContentStore store) { return new AuditLog(store.home()); }
    @Bean LoginThrottle loginThrottle(RepositoryProperties p) { return new LoginThrottle(p.loginFailureLimit(), p.loginFailureWindow()); }
    @Bean IdentityService identityService(ContentStore store, LoginThrottle throttle, RepositoryProperties p) throws IOException {
        var service = new IdentityService(store, throttle);
        if (!p.offlineCommand()) service.bootstrap(p.bootstrapPassword());
        return service;
    }
    @Bean PermissionService permissionService() { return new PermissionService(); }
    @Bean(destroyMethod = "close") UpstreamClient upstreamClient(RepositorySettings settings) { return new UpstreamClient(settings); }
    @Bean RepositoryService repositoryService(ContentStore store, RepositorySettings settings, PermissionService permissions,
                                             UpstreamClient upstream, RepositoryProperties p) throws IOException {
        var service = new RepositoryService(store, settings, permissions, upstream);
        if (!p.offlineCommand()) RepositoryBootstrap.seed(service);
        return service;
    }
    @Bean RepositoryAdminService repositoryAdminService(RepositoryService repositories) { return new RepositoryAdminService(repositories); }
    @Bean EvidenceSigner evidenceSigner(ContentStore store) { return new EvidenceSigner(store); }
    @Bean ReleaseService releaseService(RepositoryService repositories, EvidenceSigner signer) { return new ReleaseService(repositories, signer); }
    @Bean QuarantineService quarantineService(ContentStore store, PermissionService permissions) { return new QuarantineService(store, permissions); }
    @Bean StorageInsightsService storageInsightsService(RepositoryService repositories) { return new StorageInsightsService(repositories); }
    @Bean UserService userService(ContentStore store, IdentityService identity, PermissionService permissions) { return new UserService(store, identity, permissions); }
    @Bean MaintenanceService maintenanceService(ContentStore store, PermissionService permissions) { return new MaintenanceService(store, permissions); }
    @Bean MavenRawProtocol mavenRawProtocol(RepositoryService r) { return new MavenRawProtocol(r); }
    @Bean NpmProtocol npmProtocol(RepositoryService r) { return new NpmProtocol(r); }
    @Bean NugetProtocol nugetProtocol(RepositoryService r) { return new NugetProtocol(r); }
    @Bean PypiProtocol pypiProtocol(RepositoryService r) { return new PypiProtocol(r); }
    @Bean OciProtocol ociProtocol(RepositoryService r) { return new OciProtocol(r); }
    @Bean PackageGateway packageGateway(RepositoryService r, List<PackageProtocol> protocols) { return new PackageGateway(r, protocols); }
    @Bean ApplicationRunner maintenanceRunner(RepositoryProperties p, ContentStore store) {
        return args -> { if (p.offlineCommand()) OfflineOperations.execute(store, p.command(), p.commandTarget(), p.bootstrapPassword()); };
    }
}
