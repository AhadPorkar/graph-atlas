package ir.graph.repo.core.service;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.protocol.ProtocolIO;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Fixed, administrator-selected upstreams. Never forwards client Authorization headers. */
public final class UpstreamClient implements AutoCloseable {
    private final RepositorySettings config;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
    public UpstreamClient(RepositorySettings config) {
        this.config=config;
    }
    public URI validate(String value,Map<String,Object> repo)throws IOException {
        URI uri=URI.create(value);
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null
        ||(!uri.getScheme().equals("https")&&!(config.allowHttpUpstream()&&uri.getScheme().equals("http"))))throw RepositoryException.bad("UpstreamClient must be HTTPS without credentials or fragment");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if(repo!=null) {
            Set<String> allowed=new HashSet<>();
            allowed.add(URI.create((String)repo.get("upstream")).getHost().toLowerCase(Locale.ROOT));
            Object hosts=repo.get("allowedHosts");
            if(hosts instanceof List<?> list)for(Object h:list)allowed.add(h.toString().toLowerCase(Locale.ROOT));
            if(!allowed.contains(host))throw new RepositoryException(502,"UPSTREAM_HOST","An upstream URL uses a hostname not explicitly allowed on the repository");
        }
        if(!config.allowPrivateUpstream())for(InetAddress ip:InetAddress.getAllByName(host)) {
            byte[] address=ip.getAddress();
            boolean ula=address.length==16&&(address[0]&0xfe)==0xfc;
            boolean cgnat=address.length==4&&(address[0]&255)==100&&(address[1]&192)==64;
            if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress()||ula||cgnat)
            throw new RepositoryException(502,"UPSTREAM_PRIVATE","Private upstream networks are disabled; configure an explicit egress policy before enabling them");
        }
        return uri;
    }
    public record Response(int status,String type,InputStream body) implements AutoCloseable {
        public void close()throws IOException {
            body.close();
        }
    }
    public Response get(String url,Map<String,Object> repo,String accept)throws IOException,InterruptedException {
        URI uri=validate(url,repo);
        URI original=URI.create((String)repo.get("upstream"));
        for(int hop=0; hop<6; hop++) {
            HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).GET()
            .header("User-Agent","GraphAtlas/0.5.0-preview").header("Accept",accept).header("Accept-Encoding","identity");
            String env=Json.str(repo,"upstreamAuthEnv","");
            if(!env.isEmpty()&&sameOrigin(uri,original)) {
                String auth=System.getenv(env);
                if(auth==null||auth.isBlank())throw new RepositoryException(502,"UPSTREAM_CREDENTIALS","Configured upstream credential environment variable is missing");
                b.header("Authorization",auth);
            }
            var r=client.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
            if(Set.of(301,302,303,307,308).contains(r.statusCode())) {
                String location=r.headers().firstValue("Location").orElse("");
                r.body().close();
                if(location.isEmpty())throw new RepositoryException(502,"UPSTREAM_REDIRECT","UpstreamClient returned a redirect without a location");
                uri=validate(uri.resolve(location).toString(),repo);
                continue;
            }
            return new Response(r.statusCode(),r.headers().firstValue("Content-Type").orElse("application/octet-stream"),r.body());
        }
        throw new RepositoryException(502,"UPSTREAM_REDIRECT","Too many upstream redirects");
    }
    public Map<String,Object> json(String url,Map<String,Object> repo,String accept)throws IOException,InterruptedException {
        try(var r=get(url,repo,accept)) {
            checkStatus(r.status());
            return Json.obj(new String(ProtocolIO.bytes(r.body(),config.maxJson()),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    public static void checkStatus(int status) {
        if(status==404)throw RepositoryException.missing();
        if(status!=200)throw new RepositoryException(502,"UPSTREAM_STATUS","UpstreamClient returned HTTP "+status);
    }
    public static String resolve(Map<String,Object> repo,String relative) {
        String base=(String)repo.get("upstream");
        return (base.endsWith("/")?base:base+"/")+relative;
    }
    private static boolean sameOrigin(URI a,URI b) {
        return a.getScheme().equals(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&a.getPort()==b.getPort();
    }
    public void close() {
        client.close();
    }
}
