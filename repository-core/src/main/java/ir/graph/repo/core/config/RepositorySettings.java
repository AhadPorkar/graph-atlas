package ir.graph.repo.core.config;
import java.nio.file.*;
import java.net.*;
import java.util.*;
public record RepositorySettings(Path home,String bind,int port,URI publicUrl,long maxJson,long maxUpload,
                     boolean allowPrivateUpstream,boolean allowHttpUpstream,int concurrentRequests) {
    public static RepositorySettings load(Map<String,String> env) {
        int port=Integer.parseInt(env.getOrDefault("GR_PORT","8081"));
        URI url=URI.create(env.getOrDefault("GR_PUBLIC_URL","http://localhost:"+port));
        if(!Set.of("http","https").contains(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null
            ||url.getRawQuery()!=null||url.getFragment()!=null||!(url.getPath().isEmpty()||url.getPath().equals("/")))
            throw new IllegalArgumentException("GR_PUBLIC_URL must be an absolute HTTP(S) origin without a path");
        long json=Long.parseLong(env.getOrDefault("GR_MAX_JSON_BYTES","67108864"));
        long upload=Long.parseLong(env.getOrDefault("GR_MAX_UPLOAD_BYTES","0"));
        int concurrency=Integer.parseInt(env.getOrDefault("GR_CONCURRENT_REQUESTS","128"));
        if(port<1||port>65535||json<0||upload<0||concurrency<0)throw new IllegalArgumentException("Invalid configuration");
        return new RepositorySettings(Path.of(env.getOrDefault("GR_HOME","data")).toAbsolutePath().normalize(),
            env.getOrDefault("GR_BIND","127.0.0.1"),port,URI.create(url.toString().replaceAll("/$","")),
            json,upload,Boolean.parseBoolean(env.getOrDefault("GR_ALLOW_PRIVATE_UPSTREAM","false")),
            Boolean.parseBoolean(env.getOrDefault("GR_ALLOW_HTTP_UPSTREAM","false")),concurrency);
    }
    public String base(String repo){return publicUrl+"/repository/"+repo+"/";}
    public boolean secure(){return publicUrl.getScheme().equals("https");}
}
