package ir.graph.repo.core;
import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.util.Versions;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.storage.AuditLog;
import ir.graph.repo.core.protocol.StreamingMultipart;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.service.UpstreamClient;
import ir.graph.repo.core.protocol.npm.NpmProtocol;
import ir.graph.repo.core.protocol.nuget.NugetProtocol;
import ir.graph.repo.core.protocol.pypi.PypiProtocol;
import ir.graph.repo.core.protocol.oci.OciProtocol;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.core.service.MaintenanceService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class SelfTest {
    static int passed;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    interface Throwing {void run()throws Exception;}
    static void rejects(Throwing action,String name){try{action.run();throw new AssertionError("Accepted: "+name);}catch(AssertionError e){throw e;}catch(Exception e){passed++;System.out.println("PASS "+name);}}
    public static void main(String[] args)throws Exception{
        var json=Json.obj("{\"text\":\"\\u0641\\n\\\"\\\\\",\"a\":[true,false,null,-1.25e3]}");check(Json.stringify(Json.parse(Json.stringify(json))).equals(Json.stringify(json)),"JSON Unicode and nested round-trip");
        for(String bad:List.of("{\"x\":1,\"x\":2}","01","[1,]","{\"x\":}","\"raw\nnewline\"","true false","[NaN]","{x:2}"))rejects(()->Json.parse(bad),"Reject invalid JSON "+bad.replace('\n',' '));
        rejects(()->Json.parse("[".repeat(102)+"0"+"]".repeat(102)),"Reject excessive nesting");
        for(String path:List.of("../secret","a/../b","a//b","/absolute","a\\b","a/%2e%2e/b","a\u0000b","x/./z"))rejects(()->ContentStore.safePath(path),"Reject path traversal or ambiguity");
        check(ContentStore.safePath("@scope/name/-/demo-1.0.0.tgz").equals("@scope/name/-/demo-1.0.0.tgz"),"Allow scoped npm path");
        check(Versions.nuget("01.02.003.0+BUILD").equals("1.2.3"),"Normalize NuGet version");
        check(Versions.compare("1.10.0","1.2.0")>0,"Numeric semantic version ordering");
        check(Versions.compare("1.0.0-rc.10","1.0.0-rc.2")>0,"Numeric prerelease ordering");
        check(Versions.compare("1.0.0","1.0.0-rc.2")>0,"Release sorts after prerelease");
        check(Versions.pythonName("Graph__Demo.Pkg").equals("graph-demo-pkg"),"Normalize Python distribution name");
        String password="test-password-12345",hash=Passwords.hash(password);check(Passwords.verify(password,hash),"PBKDF2 password verification");check(!Passwords.verify("wrong",hash),"Reject incorrect password");check(!hash.contains(password),"Password is not persisted in plaintext");
        rejects(()->RepositorySettings.load(Map.of("GR_PUBLIC_URL","https://example.org/path")),"Reject public URL with unsupported context path");
        Path home=Files.createTempDirectory("graph-repo-selftest-");RepositorySettings cfg=RepositorySettings.load(Map.of("GR_HOME",home.toString()));
        try{
            try(ContentStore store=new ContentStore(cfg)){
                store.put("repos","test",Json.map("name","test","format","raw","type","hosted"));
                rejects(()->new ContentStore(cfg),"Prevent second process/store using the same data directory");
                var blob=store.blob("hello".getBytes(StandardCharsets.UTF_8));check(blob.sha256().equals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"),"SHA-256 blob digest");
                var duplicate=store.blob("hello".getBytes(StandardCharsets.UTF_8));check(duplicate.sha256().equals(blob.sha256()),"Content-addressed de-duplication");
                store.saveAsset("test","demo.bin",blob,"application/octet-stream",Json.map(),false);
                var other=store.blob("other".getBytes(StandardCharsets.UTF_8));rejects(()->store.saveAsset("test","demo.bin",other,"text/plain",Json.map(),false),"Reject redeployment of immutable content");
                rejects(()->store.blob(new ByteArrayInputStream(new byte[9]),8),"Operator-set per-upload safety guard");
                check(Files.readString(store.blobPath(blob.sha256())).equals("hello"),"Blob bytes match published data");
                String payload="--BOUND\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nhello\r\n--BOUND\r\nContent-Disposition: form-data; name=\"content\"; filename=\"a.bin\"\r\nContent-Type: application/octet-stream\r\n\r\nabc\u0000def\r\n--BOUNDxxmore\r\n--BOUND--\r\n";
                try(StreamingMultipart form=StreamingMultipart.parse(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),"multipart/form-data; boundary=BOUND",store.temp(),0)){
                    check(form.text("name","").equals("hello"),"Read multipart text field");check(Files.readAllBytes(form.file("content").file()).length==22,"Streaming multipart preserves binary nulls and false delimiter prefixes");
                }
                rejects(()->StreamingMultipart.parse(new ByteArrayInputStream(payload.substring(0,payload.length()-10).getBytes(StandardCharsets.UTF_8)),"multipart/form-data; boundary=BOUND",store.temp(),0),"Reject truncated multipart payload");
                try(UpstreamClient upstream=new UpstreamClient(cfg)){rejects(()->upstream.validate("http://example.org/",null),"Disallow insecure upstream by default");rejects(()->upstream.validate("https://127.0.0.1/",null),"Disallow private upstream by default");rejects(()->upstream.validate("file:///etc/passwd",null),"Disallow non-HTTP upstream schemes");}
                List<Map<String,Object>> ops=new ArrayList<>();
                for(int i=0;i<40001;i++){String path="quota-proof/"+i;ops.add(ContentStore.set("assets",ContentStore.key("test",path),store.assetRecord("test",path,blob,"application/octet-stream",Json.map())));}
                store.transact(ops);check(store.assets("test").size()==40002,"ContentStore 40,001 additional artifact references without a licensing quota");
                store.requests.set(1000001);check(store.asset("test","quota-proof/40000")!=null,"Metrics do not act as a daily-request licensing gate");
                store.compact();check(Files.size(home.resolve("metadata.jsonl"))==0,"Checkpoint truncates the WAL only after snapshot creation");
                store.delete("assets",ContentStore.key("test","quota-proof/0"));
            }
            Files.writeString(home.resolve("metadata.jsonl"),"{\"ops\":[",StandardOpenOption.APPEND);
            try(ContentStore reopened=new ContentStore(cfg)){
                check(reopened.asset("test","demo.bin")!=null,"Recover assets after restart");check(reopened.asset("test","quota-proof/0")==null,"Recover committed deletion after checkpoint");
                check(Files.readString(home.resolve("metadata.jsonl")).endsWith("\n"),"Discard a simulated incomplete final WAL record");
                check(Boolean.TRUE.equals(MaintenanceService.verify(reopened).get("ok")),"Verify referenced blob integrity after recovery");
            }
        }finally{try(var walk=Files.walk(home)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("JAVA_SELF_TESTS_PASSED="+passed);
    }
}
