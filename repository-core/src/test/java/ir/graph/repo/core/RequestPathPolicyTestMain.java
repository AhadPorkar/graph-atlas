package ir.graph.repo.core;

import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.protocol.RequestPaths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Raw URI policy only. This does not simulate or replace the Tomcat wire tests. */
public final class RequestPathPolicyTestMain {
    private RequestPathPolicyTestMain() { }
    public static void main(String[] args) {
        var allowed = new LinkedHashMap<String, String>();
        allowed.put("/repository/npm-hosted/@wire%2Fdemo", "/repository/npm-hosted/@wire/demo");
        allowed.put("/repository/npm-hosted/@wire%2fdemo", "/repository/npm-hosted/@wire/demo");
        allowed.put("/repository/npm-hosted/%40wire%2Fdemo", "/repository/npm-hosted/@wire/demo");
        allowed.put("/repository/npm-hosted/@wire/demo", "/repository/npm-hosted/@wire/demo");
        allowed.put("/repository/raw-hosted/dir%2Ffile+name.txt", "/repository/raw-hosted/dir/file+name.txt");
        allowed.put("/repository/raw-hosted/dir%2ffile+name.txt", "/repository/raw-hosted/dir/file+name.txt");
        allowed.put("/repository/raw-hosted/report%20one.txt", "/repository/raw-hosted/report one.txt");
        allowed.put("/repository/raw-hosted/a+b.txt", "/repository/raw-hosted/a+b.txt");
        allowed.put("/api/repos", "/api/repos");
        int checks = 0;
        for (var entry : allowed.entrySet()) {
            if (!Objects.equals(entry.getValue(), RequestPaths.decode(entry.getKey())))
                throw new AssertionError("Incorrect decoding: " + entry.getKey());
            checks++;
        }
        var rejected = List.of(
                "/api%2frepos", "/api%2Frepos", "/v2%2fcatalog", "/repository%2fraw-hosted/secret",
                "/repository/raw-hosted%2fsecret", "/repository/raw-hosted%2ffolder/file",
                "/repository/raw-hosted/a%2f%2fb", "/repository/raw-hosted/..%2f..%2fapi/repos",
                "/repository/raw-hosted/a%2f../secret", "/repository/raw-hosted/.%2e/secret",
                "/repository/raw-hosted/a/%252e%252e/secret", "/repository/raw-hosted/%252Fsecret",
                "/repository/raw-hosted/a%5cb", "/repository/raw-hosted/a%3bb", "/repository/raw-hosted/a//b",
                "/repository/raw-hosted/%00", "/repository/raw-hosted/%c0%af", "/repository/raw-hosted/a%0db",
                "/repository/raw-hosted/a%0Ab", "/repository/raw-hosted/bad%2", "/repository/raw-hosted/bad%zz");
        for (String path : rejected) {
            try {
                RequestPaths.decode(path);
                throw new AssertionError("Unsafe URI accepted: " + path);
            } catch (RepositoryException error) {
                if (error.status != 400) throw new AssertionError("Wrong rejection status: " + path, error);
                checks++;
            }
        }
        System.out.println("REQUEST_PATH_POLICY_CHECKS_PASSED=" + checks);
    }
}
