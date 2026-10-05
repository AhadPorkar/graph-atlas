package ir.graph.repo.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ir.graph.repo.server.config.RepositoryProperties;
import java.util.*;

@SpringBootApplication
@EnableConfigurationProperties(RepositoryProperties.class)
@EnableScheduling
public class RepositoryApplication {
    public static final String VERSION = "0.5.0-preview";
    public static void main(String[] args) {
        String[] options = legacyMaintenanceArguments(args);
        boolean offline = Arrays.stream(options).anyMatch(a -> a.startsWith("--graph.command=") && !a.equals("--graph.command="));
        if (!offline && (System.getProperty("graph.command") != null || System.getenv("GRAPH_COMMAND") != null))
            throw new IllegalArgumentException("Use --graph.command=... on the command line for offline maintenance");
        SpringApplication application = new SpringApplication(RepositoryApplication.class);
        if (offline) {
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(options)) { /* the runner executes before run returns */ }
        } else application.run(options);
    }
    private static String[] legacyMaintenanceArguments(String[] args) {
        if (args.length == 0 || !Set.of("--verify", "--compact", "--backup", "--reset-admin-password").contains(args[0])) return args;
        if (args[0].equals("--backup") && args.length != 2) throw new IllegalArgumentException("Usage: --backup /absolute/path/backup.zip");
        if (!args[0].equals("--backup") && args.length != 1) throw new IllegalArgumentException("Unexpected maintenance arguments");
        return args.length == 2 ? new String[]{"--graph.command=" + args[0].substring(2), "--graph.command-target=" + args[1]}
                : new String[]{"--graph.command=" + args[0].substring(2)};
    }
}
