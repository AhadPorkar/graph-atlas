package ir.graph.repo.core.protocol.npm;

import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.util.Versions;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.service.UpstreamClient;
import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.core.protocol.ProtocolExchange;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** npm packuments, scoped packages, publish, tarballs, dist-tags, local search and groups. */
public final class NpmProtocol implements ir.graph.repo.core.protocol.PackageProtocol {
    @Override public Set<String> formats() {
        return Set.of("npm");
    }
    private final RepositoryService r;
    public NpmProtocol(RepositoryService repositories) {
        r=repositories;
    }
    private String name(String name) {
        if(!name.matches("(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*")||name.length()>214)throw RepositoryException.bad("Invalid npm package name");
        return name;
    }
    private String docKey(String repo,String name) {
        return repo+"\nnpm:"+name;
    }
    public void handle(ProtocolExchange x,Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        if(path.equals("-/ping")) {
            ProtocolIO.method(x,"GET","HEAD");
            ProtocolIO.json(x,200,Json.map("ok",true));
            return;
        }
        if(path.equals("-/whoami")) {
            ProtocolIO.method(x,"GET");
            if(p==null)r.permissions().unauthorized(x);
            ProtocolIO.json(x,200,Json.map("username",p.username()));
            return;
        }
        if(path.equals("-/v1/search")) {
            ProtocolIO.method(x,"GET");
            search(x,repo,p);
            return;
        }
        if(path.startsWith("-/package/")) {
            tags(x,repo,path,p);
            return;
        }
        if(path.startsWith("-/"))throw new RepositoryException(501,"NOT_IMPLEMENTED","This npm endpoint is not implemented; use an API token for authentication");
        if(path.contains("/-/")) {
            ProtocolIO.method(x,"GET","HEAD");
            ContentStore.safePath(path);
            for(var leaf:r.leaves(repo,p)) {
                var a=r.store().asset((String)leaf.get("name"),path);
                if(leaf.get("type").equals("proxy")) {
                    var mapping=r.store().get("docs",leaf.get("name")+"\nnpm-file:"+path);
                    if(mapping==null)continue;
                    var data=Json.object(mapping.get("data"));
                    a=r.remoteAsset(leaf,path,(String)data.get("url"),"");
                    String sha1=Json.str(data,"sha1","");
                    if(!sha1.isEmpty()&&!Passwords.constantTimeEquals(sha1,(String)a.get("sha1")))throw new RepositoryException(502,"CHECKSUM_MISMATCH","npm tarball failed SHA-1 verification");
                }
                if(a!=null) {
                    ProtocolIO.asset(x,r.store(),a,true);
                    return;
                }
            }
            throw RepositoryException.missing();
        }
        int rev=path.indexOf("/-rev/");
        if(rev>=0) {
            String pkg=name(path.substring(0,rev));
            ProtocolIO.method(x,"DELETE");
            r.hosted(repo);
            deletePackage(repo,pkg);
            ProtocolIO.json(x,200,Json.map("ok",true));
            return;
        }
        String[] parts=path.split("/");
        int nameParts=path.startsWith("@")?2:1;
        if(parts.length<nameParts)throw RepositoryException.bad("Invalid npm package path");
        String pkg=name(String.join("/",Arrays.copyOfRange(parts,0,nameParts)));
        if(ProtocolIO.read(x)) {
            var packument=packument(repo,pkg,p);
            if(parts.length>nameParts) {
                String version=parts[nameParts];
                var tags=Json.object(packument.getOrDefault("dist-tags",Json.map()));
                version=Json.str(tags,version,version);
                Object v=Json.object(packument.get("versions")).get(version);
                if(v==null)throw RepositoryException.missing();
                ProtocolIO.json(x,200,v);
            }
            else ProtocolIO.json(x,200,packument);
            return;
        }
        ProtocolIO.method(x,"PUT","DELETE");
        r.hosted(repo);
        if(x.method().equals("DELETE")) {
            deletePackage(repo,pkg);
            ProtocolIO.json(x,200,Json.map("ok",true));
            return;
        }
        if(parts.length!=nameParts)throw RepositoryException.bad("Publish at the package endpoint");
        publish(x,repo,pkg);
    }
    private Map<String,Object> leafDoc(Map<String,Object> repo,String pkg)throws Exception {
        String repoName=(String)repo.get("name");
        Map<String,Object> data;
        if(repo.get("type").equals("proxy"))data=r.cachedJson(repo,"npm:"+pkg,UpstreamClient.resolve(repo,ProtocolIO.enc(pkg)),"application/json");
        else {
            var doc=r.store().get("docs",docKey(repoName,pkg));
            if(doc==null)throw RepositoryException.missing();
            data=Json.object(doc.get("data"));
        }
        var versions=Json.object(data.getOrDefault("versions",Json.map()));
        for(var entry:versions.entrySet()) {
            var v=Json.object(entry.getValue());
            var dist=Json.object(v.getOrDefault("dist",Json.map()));
            String tarball=Json.str(dist,"tarball","");
            if(tarball.isEmpty())continue;
            String local;
            if(repo.get("type").equals("proxy")) {
                URI url=URI.create(tarball);
                String file=url.getPath().replaceAll(".*/","");
                local=pkg+"/-/"+file;
                ContentStore.safePath(local);
                String key=repoName+"\nnpm-file:"+local;
                var mapping=Json.map("url",tarball,"sha1",Json.str(dist,"shasum",""));
                var previous=r.store().get("docs",key);
                if(previous==null||!Json.stringify(mapping).equals(Json.stringify(previous.get("data"))))r.store().put("docs",key,Json.map("repo",repoName,"key","npm-file:"+local,"updated",Instant.now().toString(),"data",mapping));
            } else local=tarball;
            dist.put("tarball",r.config().base(repoName)+ProtocolIO.path(local));
            v.put("dist",dist);
        }
        data.remove("_attachments");
        return data;
    }
    public Map<String,Object> packument(Map<String,Object> repo,String pkg,RepositoryPrincipal p)throws Exception {
        Map<String,Object> merged=null;
        Map<String,Object> versions=new LinkedHashMap<>(),tags=new LinkedHashMap<>();
        for(var leaf:r.leaves(repo,p))try {
            var doc=leafDoc(leaf,pkg);
            if(merged==null)merged=new LinkedHashMap<>(doc);
            Json.object(doc.getOrDefault("versions",Json.map())).forEach(versions::putIfAbsent);
            Json.object(doc.getOrDefault("dist-tags",Json.map())).forEach(tags::putIfAbsent);
        } catch(RepositoryException e) {
            if(e.status!=404)throw e;
        }
        if(merged==null)throw RepositoryException.missing();
        merged.put("versions",versions);
        merged.put("dist-tags",tags);
        return merged;
    }
    private void publish(ProtocolExchange x,Map<String,Object> repo,String pkg)throws Exception {
        var incoming=ProtocolIO.bodyJson(x,r.config());
        String repoName=(String)repo.get("name");
        if(!pkg.equals(Json.str(incoming,"name",pkg)))throw RepositoryException.bad("npm package name does not match the request path");
        var incomingVersions=Json.object(incoming.get("versions"));
        var attachments=Json.object(incoming.get("_attachments"));
        if(incomingVersions.isEmpty()||attachments.isEmpty())throw RepositoryException.bad("Publishing requires versions and tarball attachments");
        synchronized(r.lock(repoName+"/npm/"+pkg)) {
            var old=r.store().get("docs",docKey(repoName,pkg));
            var doc=old==null?Json.map("_id",pkg,"name",pkg,"versions",Json.map(),"dist-tags",Json.map(),"time",Json.map()):Json.object(old.get("data"));
            var versions=Json.object(doc.get("versions"));
            List<Map<String,Object>> ops=new ArrayList<>();
            for(var entry:incomingVersions.entrySet()) {
                String version=entry.getKey();
                if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?(?:\\+[A-Za-z0-9.-]+)?"))throw RepositoryException.bad("Invalid npm semantic version");
                if(versions.containsKey(version)&&!Boolean.TRUE.equals(repo.get("redeploy")))throw new RepositoryException(409,"IMMUTABLE","This npm version already exists");
                var value=Json.object(entry.getValue());
                if(!pkg.equals(Json.str(value,"name",pkg))||!version.equals(Json.str(value,"version",version)))throw RepositoryException.bad("npm version identity mismatch");
                var dist=Json.object(value.getOrDefault("dist",Json.map()));
                String original=Json.str(dist,"tarball","");
                String file=original.isEmpty()?"":URI.create(original).getPath().replaceAll(".*/","");
                if(!attachments.containsKey(file)) {
                    String wanted=file;
                    var matches=attachments.keySet().stream().filter(k->k.equals(wanted)||k.endsWith("/"+wanted)).toList();
                    if(matches.size()==1)file=matches.getFirst();
                    else if(file.isEmpty()&&attachments.size()==1)file=attachments.keySet().iterator().next();
                }
                Object attachment=attachments.get(file);
                if(attachment==null)throw RepositoryException.bad("Missing tarball attachment for version "+version);
                byte[] bytes=Base64.getDecoder().decode(Json.str(Json.object(attachment),"data",""));
                ContentStore.Blob blob=r.store().blob(bytes);
                String expected=Json.str(dist,"shasum","");
                if(!expected.isEmpty()&&!Passwords.constantTimeEquals(blob.sha1(),expected))throw RepositoryException.bad("npm tarball checksum mismatch");
                String local=ContentStore.safePath(pkg+"/-/"+file);
                var asset=r.store().assetRecord(repoName,local,blob,"application/gzip",Json.map("kind","npm","package",pkg,"version",version));
                var oldAsset=r.store().asset(repoName,local);
                if(oldAsset!=null&&!Boolean.TRUE.equals(repo.get("redeploy"))&&!blob.sha256().equals(oldAsset.get("sha256")))throw new RepositoryException(409,"IMMUTABLE","npm tarball filename already exists");
                ops.add(ContentStore.set("assets",ContentStore.key(repoName,local),asset));
                dist.put("tarball",local);
                dist.put("shasum",blob.sha1());
                dist.put("integrity","sha512-"+Base64.getEncoder().encodeToString(HexFormat.of().parseHex(blob.sha512())));
                value.put("dist",dist);
                value.put("name",pkg);
                value.put("version",version);
                versions.put(version,value);
                Json.object(doc.get("time")).put(version,Instant.now().toString());
            }
            var tags=Json.object(doc.get("dist-tags"));
            var incomingTags=Json.object(incoming.getOrDefault("dist-tags",Json.map()));
            for(var e:incomingTags.entrySet()) {
                if(!versions.containsKey(e.getValue().toString()))throw RepositoryException.bad("Tag points to a missing version");
                tags.put(e.getKey(),e.getValue());
            }
            if(tags.isEmpty())tags.put("latest",versions.keySet().stream().max(Versions::compare).orElseThrow());
            doc.put("description",Json.str(incoming,"description",Json.str(doc,"description","")));
            doc.put("readme",Json.str(incoming,"readme",Json.str(doc,"readme","")));
            doc.put("_rev",UUID.randomUUID().toString());
            Json.object(doc.get("time")).put("modified",Instant.now().toString());
            ops.add(ContentStore.set("docs",docKey(repoName,pkg),Json.map("repo",repoName,"key","npm:"+pkg,"updated",Instant.now().toString(),"data",doc)));
            r.store().transact(ops);
            ProtocolIO.json(x,201,Json.map("ok",true,"id",pkg,"rev",doc.get("_rev")));
        }
    }
    private void tags(ProtocolExchange x,Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        String tail=path.substring("-/package/".length());
        int pos=tail.indexOf("/dist-tags");
        if(pos<0)throw RepositoryException.missing();
        String pkg=name(tail.substring(0,pos));
        String tag=tail.substring(pos+"/dist-tags".length()).replaceFirst("^/","");
        if(ProtocolIO.read(x)) {
            ProtocolIO.json(x,200,packument(repo,pkg,p).get("dist-tags"));
            return;
        }
        ProtocolIO.method(x,"PUT","DELETE");
        r.hosted(repo);
        if(!tag.matches("[A-Za-z][A-Za-z0-9._-]*"))throw RepositoryException.bad("Invalid distribution tag");
        String repoName=(String)repo.get("name");
        synchronized(r.lock(repoName+"/npm/"+pkg)) {
            var record=r.store().get("docs",docKey(repoName,pkg));
            if(record==null)throw RepositoryException.missing();
            var doc=Json.object(record.get("data"));
            var tags=Json.object(doc.get("dist-tags"));
            if(x.method().equals("DELETE"))tags.remove(tag);
            else {
                Object value=Json.parse(new String(ProtocolIO.body(x,r.config().maxJson()),StandardCharsets.UTF_8));
                if(!(value instanceof String)||!Json.object(doc.get("versions")).containsKey(value))throw RepositoryException.bad("Tag points to a missing version");
                tags.put(tag,value);
            }
            record.put("updated",Instant.now().toString());
            r.store().put("docs",docKey(repoName,pkg),record);
            ProtocolIO.json(x,200,Json.map("ok",true));
        }
    }
    private void deletePackage(Map<String,Object> repo,String pkg)throws IOException {
        String name=(String)repo.get("name");
        synchronized(r.lock(name+"/npm/"+pkg)) {
            if(r.store().get("docs",docKey(name,pkg))==null)throw RepositoryException.missing();
            List<Map<String,Object>> ops=new ArrayList<>();
            for(var a:r.store().assets(name))if(pkg.equals(Json.object(a.get("meta")).get("package")))ops.add(ContentStore.del("assets",ContentStore.key(name,(String)a.get("path"))));
            ops.add(ContentStore.del("docs",docKey(name,pkg)));
            r.store().transact(ops);
        }
    }
    private void search(ProtocolExchange x,Map<String,Object> repo,RepositoryPrincipal p)throws Exception {
        var q=ProtocolIO.query(x);
        String term=q.getOrDefault("text","").toLowerCase(Locale.ROOT);
        Set<String> names=new TreeSet<>();
        Set<String> repos=new HashSet<>();
        for(var leaf:r.leaves(repo,p))repos.add((String)leaf.get("name"));
        for(var doc:r.store().all("docs"))if(repos.contains(doc.get("repo"))&&Json.str(doc,"key","").startsWith("npm:")) {
            String name=Json.str(doc,"key","").substring(4);
            if(name.contains(term))names.add(name);
        }
        List<Object> objects=new ArrayList<>();
        int from=ProtocolIO.page(q,"from",0,Integer.MAX_VALUE),size=ProtocolIO.page(q,"size",20,1000),i=0;
        for(String name:names) {
            if(i++<from)continue;
            if(objects.size()>=size)break;
            var doc=packument(repo,name,p);
            String version=Json.str(Json.object(doc.get("dist-tags")),"latest","");
            objects.add(Json.map("package",Json.map("name",name,"version",version,"description",Json.str(doc,"description","")),"searchScore",1));
        }
        ProtocolIO.json(x,200,Json.map("objects",objects,"total",names.size(),"time",Instant.now().toString()));
    }
}
