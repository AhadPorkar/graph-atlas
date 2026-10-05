package ir.graph.repo.core.protocol.oci;

import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.protocol.ProtocolExchange;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Hosted Docker Registry V2 / OCI content workflows. Not a claim of OCI conformance certification. */
public final class OciProtocol implements ir.graph.repo.core.protocol.PackageProtocol {
    @Override public Set<String> formats() {
        return Set.of("docker");
    }
    private final RepositoryService r;
    private static final Set<String> MEDIA=Set.of("application/vnd.oci.image.manifest.v1+json","application/vnd.oci.image.index.v1+json","application/vnd.docker.distribution.manifest.v2+json","application/vnd.docker.distribution.manifest.list.v2+json");
    public OciProtocol(RepositoryService r) {
        this.r=r;
    }
    private String image(String name) {
        if(!name.matches("[a-z0-9]+(?:(?:[._]|__|[-]+)[a-z0-9]+)*(?:/[a-z0-9]+(?:(?:[._]|__|[-]+)[a-z0-9]+)*)*")||name.length()>255)throw RepositoryException.bad("Invalid OCI repository name");
        return name;
    }
    private String digest(String value) {
        if(value==null||!value.matches("sha256:[a-f0-9]{64}"))throw new RepositoryException(400,"DIGEST_INVALID","This registry accepts SHA-256 content digests");
        return value;
    }
    private String root(Map<String,Object> repo,String image) {
        return r.config().publicUrl()+"/v2/"+repo.get("name")+"/"+image;
    }
    private String path(String image,String kind,String reference) {
        return "oci/"+image+"/"+kind+"/"+reference;
    }
    public void handle(ProtocolExchange x,Map<String,Object> repo,String tail,RepositoryPrincipal p)throws Exception {
        x.responseHeaders().set("Docker-Distribution-Api-Version","registry/2.0");
        int separator=tail.lastIndexOf("/blobs/");
        if(separator>=0) {
            String image=image(tail.substring(0,separator)),suffix=tail.substring(separator+7);
            if(suffix.startsWith("uploads")) {
                uploads(x,repo,image,suffix,p);
                return;
            }
            String digest=digest(suffix),local=path(image,"blobs",digest);
            var a=r.store().asset((String)repo.get("name"),local);
            if(a==null)throw new RepositoryException(404,"BLOB_UNKNOWN","Blob not found");
            if(x.method().equals("DELETE")) {
                for(var manifest:r.store().assets((String)repo.get("name"))) {
                    var meta=Json.object(manifest.get("meta"));
                    if("oci-manifest".equals(meta.get("kind"))&&image.equals(meta.get("image"))&&Json.array(meta.getOrDefault("references",List.of())).contains(digest))throw new RepositoryException(409,"BLOB_REFERENCED","Delete referring manifests before deleting this blob");
                }
                r.store().delete("assets",ContentStore.key((String)repo.get("name"),local));
                ProtocolIO.send(x,202,"application/json",new byte[0]);
                return;
            }
            x.responseHeaders().set("Docker-Content-Digest",digest);
            ProtocolIO.asset(x,r.store(),a,false);
            return;
        }
        separator=tail.lastIndexOf("/manifests/");
        if(separator>=0) {
            manifests(x,repo,image(tail.substring(0,separator)),tail.substring(separator+11));
            return;
        }
        if(tail.endsWith("/tags/list")) {
            ProtocolIO.method(x,"GET");
            tags(x,repo,image(tail.substring(0,tail.length()-10)));
            return;
        }
        throw RepositoryException.missing();
    }
    private void uploads(ProtocolExchange x,Map<String,Object> repo,String image,String suffix,RepositoryPrincipal p)throws Exception {
        String method=x.method(),repoName=(String)repo.get("name");
        if(suffix.equals("uploads")||suffix.equals("uploads/")) {
            ProtocolIO.method(x,"POST");
            String id=UUID.randomUUID().toString();
            Path file=r.store().temp().resolve("oci-"+id+".part");
            Files.createFile(file);
            var upload=Json.map("repo",repoName,"image",image,"owner",p.username(),"id",id,"offset",0,"updated",Instant.now().toString());
            r.store().put("docs",repoName+"\nupload:"+id,upload);
            x.responseHeaders().set("Location",root(repo,image)+"/blobs/uploads/"+id);
            x.responseHeaders().set("Docker-Upload-UUID",id);
            x.responseHeaders().set("Range","0-0");
            ProtocolIO.send(x,202,"application/json",new byte[0]);
            return;
        }
        String id=suffix.substring("uploads/".length());
        if(!id.matches("[a-f0-9-]{36}"))throw RepositoryException.bad("Invalid upload ID");
        String key=repoName+"\nupload:"+id;
        synchronized(r.lock(key)) {
            var upload=r.store().get("docs",key);
            if(upload==null||!image.equals(upload.get("image")))throw new RepositoryException(404,"BLOB_UPLOAD_UNKNOWN","Upload not found");
            if(p==null||(!p.admin()&&!p.username().equals(upload.get("owner"))))throw new RepositoryException(403,"DENIED","Upload belongs to another account");
            Path file=r.store().temp().resolve("oci-"+id+".part");
            if(!Files.exists(file))throw new RepositoryException(404,"BLOB_UPLOAD_UNKNOWN","Upload data is missing");
            long committed=Json.num(upload,"offset",0);
            try(var ch=FileChannel.open(file,StandardOpenOption.WRITE)) {
                if(ch.size()>committed)ch.truncate(committed);
                if(ch.size()<committed)throw new RepositoryException(500,"UPLOAD_CORRUPT","Upload data is shorter than its committed offset");
            }
            if(method.equals("DELETE")) {
                r.store().delete("docs",key);
                Files.deleteIfExists(file);
                ProtocolIO.send(x,204,"application/json",new byte[0]);
                return;
            }
            if(method.equals("GET")) {
                uploadHeaders(x,repo,image,id,committed);
                ProtocolIO.send(x,204,"application/json",new byte[0]);
                return;
            }
            ProtocolIO.method(x,"PATCH","PUT");
            String range=x.header("Content-Range");
            Long declaredEnd=null;
            if(range!=null) {
                String[] parts=range.replaceFirst("^bytes ","").split("-");
                if(parts.length!=2||Long.parseLong(parts[0])!=committed) {
                    x.responseHeaders().set("Range","0-"+Math.max(0,committed-1));
                    throw new RepositoryException(416,"RANGE_INVALID","Upload chunk does not start at the committed offset");
                }
                declaredEnd=Long.parseLong(parts[1]);
            }
            long size=committed;
            boolean saved=false;
            try {
                try(var out=FileChannel.open(file,StandardOpenOption.WRITE)) {
                    out.position(committed);
                    byte[] buffer=new byte[65536];
                    int n;
                    while((n=x.input().read(buffer))!=-1) {
                        size+=n;
                        if(r.config().maxUpload()>0&&size>r.config().maxUpload())throw new RepositoryException(413,"BODY_TOO_LARGE","OCI blob exceeds configured per-upload limit");
                        ByteBuffer b=ByteBuffer.wrap(buffer,0,n);
                        while(b.hasRemaining())out.write(b);
                    }
                    out.force(true);
                }
                if(declaredEnd!=null&&declaredEnd!=size-1)throw new RepositoryException(416,"RANGE_INVALID","Upload chunk length does not match Content-Range");
                upload.put("offset",size);
                upload.put("updated",Instant.now().toString());
                r.store().put("docs",key,upload);
                saved=true;
            } finally {
                if(!saved)try(var ch=FileChannel.open(file,StandardOpenOption.WRITE)) {
                    ch.truncate(committed);
                    ch.force(true);
                }
            }
            if(method.equals("PATCH")) {
                uploadHeaders(x,repo,image,id,size);
                ProtocolIO.send(x,202,"application/json",new byte[0]);
                return;
            }
            String expected=digest(ProtocolIO.query(x).get("digest"));
            ContentStore.Blob blob;
            try(var in=Files.newInputStream(file)) {
                blob=r.store().blob(in,r.config().maxUpload());
            }
            if(!expected.equals("sha256:"+blob.sha256()))throw new RepositoryException(400,"DIGEST_INVALID","Uploaded blob digest does not match");
            String local=path(image,"blobs",expected);
            var a=r.store().assetRecord(repoName,local,blob,"application/octet-stream",Json.map("kind","oci-blob","image",image));
            r.store().transact(List.of(ContentStore.set("assets",ContentStore.key(repoName,local),a),ContentStore.del("docs",key)));
            Files.deleteIfExists(file);
            x.responseHeaders().set("Location",root(repo,image)+"/blobs/"+expected);
            x.responseHeaders().set("Docker-Content-Digest",expected);
            ProtocolIO.send(x,201,"application/json",new byte[0]);
        }
    }
    private void uploadHeaders(ProtocolExchange x,Map<String,Object> repo,String image,String id,long size) {
        x.responseHeaders().set("Location",root(repo,image)+"/blobs/uploads/"+id);
        x.responseHeaders().set("Docker-Upload-UUID",id);
        x.responseHeaders().set("Range","0-"+Math.max(0,size-1));
    }
    private void manifests(ProtocolExchange x,Map<String,Object> repo,String image,String ref)throws Exception {
        String name=(String)repo.get("name"),method=x.method();
        boolean byDigest=ref.startsWith("sha256:");
        if(byDigest)digest(ref);
        else if(!ref.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"))throw RepositoryException.bad("Invalid OCI tag");
        String tagKey=name+"\noci-tag:"+image+":"+ref;
        if(method.equals("PUT")) {
            String type=x.header("Content-Type");
            if(type!=null)type=type.split(";",2)[0];
            if(type==null||!MEDIA.contains(type))throw new RepositoryException(415,"MANIFEST_INVALID","Unsupported OCI manifest media type");
            byte[] bytes=ProtocolIO.body(x,r.config().maxJson());
            var manifest=Json.obj(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            if(Json.num(manifest,"schemaVersion",0)!=2)throw new RepositoryException(400,"MANIFEST_INVALID","schemaVersion must be 2");
            List<Object> refs=new ArrayList<>();
            List<Object> descriptors=new ArrayList<>();
            if(manifest.get("config")!=null)descriptors.add(manifest.get("config"));
            descriptors.addAll(Json.array(manifest.getOrDefault("layers",List.of())));
            descriptors.addAll(Json.array(manifest.getOrDefault("manifests",List.of())));
            boolean index=manifest.containsKey("manifests");
            if(!index&&(!manifest.containsKey("config")||!manifest.containsKey("layers")))throw new RepositoryException(400,"MANIFEST_INVALID","Image manifest requires config and layers");
            for(Object item:descriptors) {
                var descriptor=Json.object(item);
                String d=digest(Json.str(descriptor,"digest",""));
                String kind=index?"manifests":"blobs";
                var a=r.store().asset(name,path(image,kind,d));
                if(a==null)throw new RepositoryException(400,"MANIFEST_BLOB_UNKNOWN","Referenced content is missing: "+d);
                if(Json.num(descriptor,"size",-1)!=Json.num(a,"size",0))throw new RepositoryException(400,"MANIFEST_INVALID","Descriptor size does not match stored content");
                refs.add(d);
            }
            ContentStore.Blob blob=r.store().blob(bytes);
            String d="sha256:"+blob.sha256();
            if(byDigest&&!ref.equals(d))throw new RepositoryException(400,"DIGEST_INVALID","Manifest digest does not match request reference");
            String local=path(image,"manifests",d);
            var a=r.store().assetRecord(name,local,blob,type,Json.map("kind","oci-manifest","image",image,"references",refs));
            List<Map<String,Object>> ops=new ArrayList<>();
            ops.add(ContentStore.set("assets",ContentStore.key(name,local),a));
            if(!byDigest)ops.add(ContentStore.set("docs",tagKey,Json.map("repo",name,"key","oci-tag:"+image+":"+ref,"image",image,"tag",ref,"digest",d,"updated",Instant.now().toString())));
            r.store().transact(ops);
            x.responseHeaders().set("Location",root(repo,image)+"/manifests/"+d);
            x.responseHeaders().set("Docker-Content-Digest",d);
            ProtocolIO.send(x,201,type,new byte[0]);
            return;
        }
        String d=ref;
        if(!byDigest) {
            var tag=r.store().get("docs",tagKey);
            if(tag==null)throw new RepositoryException(404,"MANIFEST_UNKNOWN","Manifest tag not found");
            d=(String)tag.get("digest");
        }
        String local=path(image,"manifests",d);
        var asset=r.store().asset(name,local);
        if(asset==null)throw new RepositoryException(404,"MANIFEST_UNKNOWN","Manifest not found");
        if(method.equals("DELETE")) {
            if(!byDigest) {
                r.store().delete("docs",tagKey);
                ProtocolIO.send(x,202,"application/json",new byte[0]);
                return;
            }
            List<Map<String,Object>> ops=new ArrayList<>();
            ops.add(ContentStore.del("assets",ContentStore.key(name,local)));
            for(var tag:r.store().all("docs"))if(name.equals(tag.get("repo"))&&image.equals(tag.get("image"))&&d.equals(tag.get("digest")))ops.add(ContentStore.del("docs",name+"\n"+tag.get("key")));
            r.store().transact(ops);
            ProtocolIO.send(x,202,"application/json",new byte[0]);
            return;
        }
        x.responseHeaders().set("Docker-Content-Digest",d);
        ProtocolIO.asset(x,r.store(),asset,false);
    }
    private void tags(ProtocolExchange x,Map<String,Object> repo,String image)throws IOException {
        var q=ProtocolIO.query(x);
        int n=ProtocolIO.page(q,"n",100,1000);
        String last=q.getOrDefault("last","");
        SortedSet<String> all=new TreeSet<>();
        for(var tag:r.store().all("docs"))if(repo.get("name").equals(tag.get("repo"))&&image.equals(tag.get("image"))&&tag.containsKey("tag"))all.add((String)tag.get("tag"));
        List<String> tags=all.stream().filter(t->t.compareTo(last)>0).limit(n).toList();
        if(n>0&&all.stream().filter(t->t.compareTo(last)>0).count()>tags.size())x.responseHeaders().set("Link","<"+root(repo,image)+"/tags/list?n="+n+"&last="+ProtocolIO.enc(tags.getLast())+">; rel=\"next\"");
        ProtocolIO.json(x,200,Json.map("name",repo.get("name")+"/"+image,"tags",tags));
    }
}
