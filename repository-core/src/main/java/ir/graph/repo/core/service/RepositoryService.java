package ir.graph.repo.core.service;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.core.security.PermissionService;
import ir.graph.repo.core.protocol.ProtocolExchange;

import java.io.*;
import java.time.*;
import java.util.*;

public final class RepositoryService {
    public static final Set<String> FORMATS=Set.of("maven","raw","npm","pypi","nuget","docker");
    private final ContentStore store;
    private final RepositorySettings config;
    private final PermissionService permissions;
    private final UpstreamClient upstream;
    private final Object[] locks=new Object[256];
    public RepositoryService(ContentStore store,RepositorySettings config,PermissionService permissions,UpstreamClient upstream) {
        this.store=store;
        this.config=config;
        this.permissions=permissions;
        this.upstream=upstream;
        Arrays.setAll(locks,i->new Object());
    }
    public ContentStore store() {
        return store;
    }
    public RepositorySettings config() {
        return config;
    }
    public PermissionService permissions() {
        return permissions;
    }
    public UpstreamClient upstream() {
        return upstream;
    }
    public Object lock(String key) {
        return locks[Math.floorMod(key.hashCode(),locks.length)];
    }
    public Map<String,Object> get(String name) {
        var r=store.get("repos",name);
        if(r==null)throw RepositoryException.missing();
        return r;
    }
    public Map<String,Object> validate(Map<String,Object> in)throws IOException {
        String name=Json.str(in,"name",""),format=Json.str(in,"format",""),type=Json.str(in,"type","hosted");
        if(!name.matches("[a-z0-9][a-z0-9._-]{0,99}"))throw RepositoryException.bad("Repository name: lowercase letters, digits, dot, underscore and dash; maximum 100 characters");
        if(!FORMATS.contains(format)||!Set.of("hosted","proxy","group").contains(type))throw RepositoryException.bad("Unsupported repository format or type");
        if(format.equals("docker")&&!type.equals("hosted"))throw RepositoryException.bad("This release implements Docker/OCI hosted repositories only");
        var existing=store.get("repos",name);
        if(existing!=null&&(!format.equals(existing.get("format"))||!type.equals(existing.get("type"))))throw RepositoryException.bad("Repository format and type cannot be changed in place");
        long ttl=Json.num(in,"cacheSeconds",3600);
        if(ttl<0)throw RepositoryException.bad("cacheSeconds must not be negative");
        var r=Json.map("name",name,"format",format,"type",type,"online",Json.bool(in,"online",true),"anonymous",Json.bool(in,"anonymous",false),
        "redeploy",Json.bool(in,"redeploy",false),"cacheSeconds",ttl,"offline",Json.bool(in,"offline",false),"upstream",Json.str(in,"upstream",""),
        "upstreamAuthEnv",Json.str(in,"upstreamAuthEnv",""),"allowedHosts",in.getOrDefault("allowedHosts",List.of()),"members",in.getOrDefault("members",List.of()),
        "versionPolicy",Json.str(in,"versionPolicy","mixed"),"created",existing==null?Instant.now().toString():existing.get("created"));
        if(!Set.of("mixed","release","snapshot").contains(r.get("versionPolicy")))throw RepositoryException.bad("Invalid Maven version policy");
        for(Object h:Json.array(r.get("allowedHosts")))if(!(h instanceof String)||!h.toString().matches("[A-Za-z0-9.:-]+"))throw RepositoryException.bad("Invalid allowed hostname");
        if(!Json.str(r,"upstreamAuthEnv","").matches("[A-Z_][A-Z0-9_]*|"))throw RepositoryException.bad("Invalid environment variable name");
        if(type.equals("proxy")) {
            upstream.validate((String)r.get("upstream"),null);
        }
        if(type.equals("group")) {
            var members=Json.array(r.get("members"));
            if(members.isEmpty())throw RepositoryException.bad("A group must have at least one member");
            Set<String> unique=new HashSet<>();
            for(Object m:members) {
                if(!(m instanceof String)||!unique.add(m.toString()))throw RepositoryException.bad("Invalid or duplicate group member");
                var child=get(m.toString());
                if(!format.equals(child.get("format")))throw RepositoryException.bad("Group members must use the same package format");
            }
            cycle(name,r,new HashSet<>());
        }
        return r;
    }
    private void cycle(String name,Map<String,Object> replacement,Set<String> stack) {
        if(!stack.add(name))throw RepositoryException.bad("Repository group cycle detected");
        var r=name.equals(replacement.get("name"))?replacement:get(name);
        if(r.get("type").equals("group"))for(Object m:Json.array(r.get("members")))cycle(m.toString(),replacement,stack);
        stack.remove(name);
    }
    public List<Map<String,Object>> leaves(Map<String,Object> repo,RepositoryPrincipal principal) {
        List<Map<String,Object>> out=new ArrayList<>();
        leaves(repo,principal,out,new HashSet<>());
        return out;
    }
    private void leaves(Map<String,Object> repo,RepositoryPrincipal p,List<Map<String,Object>> out,Set<String> seen) {
        if(!seen.add((String)repo.get("name"))||!permissions.allowed(p,repo,"read"))return;
        if(repo.get("type").equals("group"))for(Object member:Json.array(repo.get("members")))leaves(get(member.toString()),p,out,seen);
        else out.add(repo);
    }
    public boolean fresh(Map<String,Object> record,Map<String,Object> repo) {
        if(Boolean.TRUE.equals(repo.get("offline")))return true;
        return Instant.parse((String)record.get("updated")).plusSeconds(Json.num(repo,"cacheSeconds",3600)).isAfter(Instant.now());
    }
    public Map<String,Object> cachedJson(Map<String,Object> repo,String key,String url,String accept)throws IOException,InterruptedException {
        String id=(String)repo.get("name")+"\n"+key;
        var cached=store.get("docs",id);
        if(cached!=null&&fresh(cached,repo))return Json.object(cached.get("data"));
        if(Boolean.TRUE.equals(repo.get("offline")))throw RepositoryException.missing();
        var data=upstream.json(url,repo,accept);
        store.put("docs",id,Json.map("repo",repo.get("name"),"key",key,"updated",Instant.now().toString(),"data",data));
        return data;
    }
    public Map<String,Object> remoteAsset(Map<String,Object> repo,String path,String url,String expectedSha256)throws IOException,InterruptedException {
        String name=(String)repo.get("name");
        ContentStore.safePath(path);
        var old=store.asset(name,path);
        if(old!=null&&(fresh(old,repo)||!expectedSha256.isEmpty()))return old;
        if(Boolean.TRUE.equals(repo.get("offline")))throw RepositoryException.missing();
        synchronized(lock(name+"/"+path)) {
            old=store.asset(name,path);
            if(old!=null&&(fresh(old,repo)||!expectedSha256.isEmpty()))return old;
            try(var response=upstream.get(url,repo,"*/*")) {
                UpstreamClient.checkStatus(response.status());
                ContentStore.Blob blob=store.blob(response.body(),config.maxUpload());
                if(!expectedSha256.isEmpty()&&!Passwords.constantTimeEquals(blob.sha256(),expectedSha256))throw new RepositoryException(502,"CHECKSUM_MISMATCH","UpstreamClient content failed SHA-256 verification");
                return store.saveAsset(name,path,blob,response.type(),Json.map("source","proxy"),true);
            }
        }
    }
    public Map<String,Object> genericRead(Map<String,Object> repo,String path,RepositoryPrincipal p)throws IOException,InterruptedException {
        for(var leaf:leaves(repo,p)) {
            try {
                String name=(String)leaf.get("name");
                var a=store.asset(name,path);
                if(leaf.get("type").equals("hosted")) {
                    if(a!=null)return a;
                }
                else return remoteAsset(leaf,path,UpstreamClient.resolve(leaf,ProtocolIO.path(path)),"");
            } catch(RepositoryException e) {
                if(e.status!=404)throw e;
            }
        }
        throw RepositoryException.missing();
    }
    public boolean overwrite(Map<String,Object> repo,String path) {
        return Boolean.TRUE.equals(repo.get("redeploy"))||(repo.get("format").equals("maven")&&path.matches(".*maven-metadata[^/]*\\.xml(\\.(sha1|sha256|sha512|md5))?"));
    }
    public void hosted(Map<String,Object> repo) {
        if(!repo.get("type").equals("hosted"))throw new RepositoryException(405,"READ_ONLY","Publish to a hosted repository, not to a proxy or group");
    }
    public void generic(ProtocolExchange x,Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        ContentStore.safePath(path);
        String name=(String)repo.get("name"),method=x.method();
        if(ProtocolIO.read(x)) {
            String algorithm=null,base=path;
            if(repo.get("format").equals("maven"))for(String a:List.of("sha1","sha256","sha512","md5"))if(path.endsWith("."+a)) {
                algorithm=a;
                base=path.substring(0,path.length()-a.length()-1);
                break;
            }
            if(algorithm!=null) {
                var asset=genericRead(repo,base,p);
                ProtocolIO.text(x,200,"text/plain",(String)asset.get(algorithm));
            }
            else ProtocolIO.asset(x,store,genericRead(repo,path,p),true);
            return;
        }
        hosted(repo);
        if(method.equals("PUT")) {
            if(repo.get("format").equals("maven")) {
                String policy=Json.str(repo,"versionPolicy","mixed");
                boolean metadata=path.matches(".*maven-metadata.*"),snapshot=path.contains("-SNAPSHOT/");
                if(!metadata&&((policy.equals("release")&&snapshot)||(policy.equals("snapshot")&&!snapshot)))throw RepositoryException.bad("Artifact does not match this repository's Maven version policy");
            }
            ContentStore.Blob blob=store.blob(x.input(),config.maxUpload());
            store.saveAsset(name,path,blob,ProtocolIO.contentType(path),Json.map(),overwrite(repo,path));
            ProtocolIO.json(x,201,Json.map("ok",true,"sha256",blob.sha256()));
            return;
        }
        if(method.equals("DELETE")) {
            if(store.asset(name,path)==null)throw RepositoryException.missing();
            store.delete("assets",ContentStore.key(name,path));
            ProtocolIO.json(x,200,Json.map("ok",true));
            return;
        }
        ProtocolIO.method(x,"GET","HEAD","PUT","DELETE");
    }
}
