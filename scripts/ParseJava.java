import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JDK syntax check only. Deliberately does not resolve Spring dependencies or emit application bytecode. */
public final class ParseJava {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A complete JDK is required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<Path> sources;
        try (var files = Files.walk(Path.of(args.length == 0 ? "." : args[0]))) {
            sources = files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/.build/")
                    && !p.toString().contains("/target/")).sorted().toList();
        }
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "21", "-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(sources));
            task.parse();
        }
        long errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
        diagnostics.getDiagnostics().forEach(System.out::println);
        if (errors != 0) throw new IllegalStateException("Java syntax errors: " + errors);
        System.out.println("JAVA_SOURCE_FILES_SYNTAX_PARSED=" + sources.size());
        System.out.println("SYNTAX_ONLY_NOT_SPRING_BOOT_COMPILATION");
    }
}
