package ir.graph.repo.core.protocol.pypi;

import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.util.Versions;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.StreamingMultipart;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.service.UpstreamClient;
import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.security.Passwords;
import ir.graph.repo.core.protocol.ProtocolExchange;

import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Python Simple API (HTML + JSON), legacy multipart upload, proxy cache and groups. */
public final class PypiProtocol implements ir.graph.repo.core.protocol.PackageProtocol {
    @Override public Set<String> formats() {
        return Set.of("pypi");
    }
    private final RepositoryService r;
    public PypiProtocol(RepositoryService r) {
        this.r=r;
    }
    public void handle(ProtocolExchange x,Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        if(x.method().equals("POST")&&(path.isEmpty()||path.equals("legacy")||path.equals("legacy/"))) {
            publish(x,repo);
            return;
        }
        ProtocolIO.method(x,"GET","HEAD");
        if(path.startsWith("files/")) {
            ContentStore.safePath(path);
            for(var leaf:r.leaves(repo,p)) {
                String name=(String)leaf.get("name");
                var a=r.store().asset(name,path);
                if(leaf.get("type").equals("proxy")) {
                    var record=r.store().get("docs",name+"\npypi-file:"+path);
                    if(record==null)continue;
                    var map=Json.object(record.get("data"));
                    a=r.remoteAsset(leaf,path,(String)map.get("url"),Json.str(map,"sha256",""));
                }
                if(a!=null) {
                    ProtocolIO.asset(x,r.store(),a,true);
                    return;
                }
            }
            throw RepositoryException.missing();
        }
        if(path.equals("simple")||path.equals("simple/")||path.isEmpty()) {
            Set<String> names=new TreeSet<>();
            for(var leaf:r.leaves(repo,p)) {
                for(var a:r.store().assets((String)leaf.get("name"))) {
                    var m=Json.object(a.get("meta"));
                    if("pypi".equals(m.get("kind")))names.add((String)m.get("package"));
                }
                for(var d:r.store().all("docs"))if(leaf.get("name").equals(d.get("repo"))&&Json.str(d,"key","").startsWith("pypi:"))names.add(Json.str(d,"key","").substring(5));
            }
            StringBuilder html=new StringBuilder("<!doctype html><html><head><meta name=\"pypi:repository-version\" content=\"1.0\"></head><body>");
            for(String name:names)html.append("<a href=\"").append(r.config().base((String)repo.get("name"))).append("simple/").append(name).append("/\">").append(ProtocolIO.html(name)).append("</a>\n");
            if(jsonRequested(x))ProtocolIO.json(x,200,Json.map("meta",Json.map("api-version","1.0"),"projects",names.stream().map(n->Json.map("name",n)).toList()));
            else ProtocolIO.text(x,200,"text/html; charset=utf-8",html.append("</body></html>").toString());
            return;
        }
        if(!path.startsWith("simple/"))throw RepositoryException.missing();
        String pkg=Versions.pythonName(path.substring(7).replaceAll("/$",""));
        Map<String,Map<String,Object>> files=new LinkedHashMap<>();
        boolean found=false;
        for(var leaf:r.leaves(repo,p))try {
            List<Map<String,Object>> items=project(leaf,pkg);
            if(!items.isEmpty())found=true;
            for(var item:items)files.putIfAbsent((String)item.get("filename"),item);
        } catch(RepositoryException e) {
            if(e.status!=404)throw e;
        }
        if(!found)throw RepositoryException.missing();
        if(jsonRequested(x)) {
            ProtocolIO.text(x,200,"application/vnd.pypi.simple.v1+json",Json.stringify(Json.map("meta",Json.map("api-version","1.0"),"name",pkg,"files",new ArrayList<>(files.values()))));
            return;
        }
        StringBuilder html=new StringBuilder("<!doctype html><html><head><meta name=\"pypi:repository-version\" content=\"1.0\"><title>").append(ProtocolIO.html(pkg)).append("</title></head><body>");
        for(var file:files.values()) {
            String hash=Json.str(Json.object(file.getOrDefault("hashes",Json.map())),"sha256","");
            String url=Json.str(file,"url","")+(hash.isEmpty()?"":"#sha256="+hash);
            html.append("<a href=\"").append(ProtocolIO.html(url)).append('"');
            String requires=Json.str(file,"requires-python","");
            if(!requires.isEmpty())html.append(" data-requires-python=\"").append(ProtocolIO.html(requires)).append('"');
            Object yanked=file.get("yanked");
            if(yanked!=null&&!Boolean.FALSE.equals(yanked))html.append(" data-yanked=\"").append(ProtocolIO.html(yanked instanceof String?yanked:"")).append('"');
            html.append('>').append(ProtocolIO.html(file.get("filename"))).append("</a>\n");
        }
        ProtocolIO.text(x,200,"text/html; charset=utf-8",html.append("</body></html>").toString());
    }
    private boolean jsonRequested(ProtocolExchange x) {
        String a=x.header("Accept");
        return a!=null&&a.contains("application/vnd.pypi.simple.v1+json");
    }
    private void publish(ProtocolExchange x,Map<String,Object> repo)throws Exception {
        r.hosted(repo);
        try(var form=StreamingMultipart.parse(x.input(),x.header("Content-Type"),r.store().temp(),r.config().maxUpload())) {
            if(!form.text(":action","file_upload").equals("file_upload"))throw RepositoryException.bad("Unsupported Python upload action");
            String pkg=Versions.pythonName(form.text("name","")),version=form.text("version","");
            if(version.isBlank()||version.length()>200)throw RepositoryException.bad("Python package version is missing or invalid");
            var part=form.file("content");
            String file=part.filename();
            if(file.contains("/")||file.contains("\\")||!(file.endsWith(".whl")||file.endsWith(".tar.gz")||file.endsWith(".zip")))throw RepositoryException.bad("Unsupported Python distribution filename");
            ContentStore.safePath(file);
            String path="files/"+pkg+"/"+file;
            ContentStore.Blob blob;
            try(InputStream in=Files.newInputStream(part.file())) {
                blob=r.store().blob(in,r.config().maxUpload());
            }
            String digest=form.text("sha256_digest","");
            if(!digest.isEmpty()&&!Passwords.constantTimeEquals(digest,blob.sha256()))throw RepositoryException.bad("Python distribution checksum mismatch");
            var meta=Json.map("kind","pypi","package",pkg,"version",version,"requires-python",form.text("requires_python",""),"filename",file,"yanked",false);
            r.store().saveAsset((String)repo.get("name"),path,blob,ProtocolIO.contentType(file),meta,Boolean.TRUE.equals(repo.get("redeploy")));
            ProtocolIO.json(x,200,Json.map("ok",true,"sha256",blob.sha256()));
        }
    }
    private List<Map<String,Object>> project(Map<String,Object> repo,String pkg)throws Exception {
        String name=(String)repo.get("name");
        List<Map<String,Object>> files=new ArrayList<>();
        if(repo.get("type").equals("hosted")) {
            for(var a:r.store().assets(name)) {
                var meta=Json.object(a.get("meta"));
                if(!"pypi".equals(meta.get("kind"))||!pkg.equals(meta.get("package")))continue;
                files.add(Json.map("filename",meta.get("filename"),"url",r.config().base(name)+ProtocolIO.path((String)a.get("path")),"hashes",Json.map("sha256",a.get("sha256")),"requires-python",Json.str(meta,"requires-python",""),"yanked",meta.getOrDefault("yanked",false)));
            }
            return files;
        }
        String key=name+"\npypi:"+pkg;
        var cached=r.store().get("docs",key);
        Map<String,Object> data;
        String url=UpstreamClient.resolve(repo,ProtocolIO.enc(pkg)+"/");
        if(cached!=null&&r.fresh(cached,repo))data=Json.object(cached.get("data"));
        else {
            if(Boolean.TRUE.equals(repo.get("offline")))throw RepositoryException.missing();
            try(var response=r.upstream().get(url,repo,"application/vnd.pypi.simple.v1+json, text/html;q=0.9")) {
                UpstreamClient.checkStatus(response.status());
                String text=new String(ProtocolIO.bytes(response.body(),r.config().maxJson()),java.nio.charset.StandardCharsets.UTF_8);
                data=response.type().contains("json")?Json.obj(text):parseHtml(text,url);
                r.store().put("docs",key,Json.map("repo",name,"key","pypi:"+pkg,"updated",Instant.now().toString(),"data",data));
            }
        }
        for(Object value:Json.array(data.getOrDefault("files",List.of()))) {
            var file=Json.object(value);
            URI uri=URI.create(url).resolve(Json.str(file,"url",""));
            String fragment=uri.getFragment();
            URI clean=new URI(uri.getScheme(),uri.getAuthority(),uri.getPath(),uri.getQuery(),null);
            String original=clean.toString();
            var hashes=Json.object(file.getOrDefault("hashes",Json.map()));
            String digest=Json.str(hashes,"sha256","");
            if(digest.isEmpty()&&fragment!=null&&fragment.startsWith("sha256="))digest=fragment.substring(7);
            if(!digest.isEmpty()&&!digest.matches("[a-fA-F0-9]{64}"))throw new RepositoryException(502,"UPSTREAM_METADATA","Invalid SHA-256 in Python index");
            String filename=Json.str(file,"filename",uri.getPath().replaceAll(".*/",""));
            ContentStore.safePath(filename);
            if(filename.contains("/"))throw RepositoryException.bad("Invalid distribution filename");
            String local="files/"+ContentStore.digest(original)+"/"+filename;
            var mapping=Json.map("url",original,"sha256",digest.toLowerCase(Locale.ROOT));
            String mapKey=name+"\npypi-file:"+local;
            var previous=r.store().get("docs",mapKey);
            if(previous==null||!Json.stringify(mapping).equals(Json.stringify(previous.get("data"))))r.store().put("docs",mapKey,Json.map("repo",name,"key","pypi-file:"+local,"updated",Instant.now().toString(),"data",mapping));
            files.add(Json.map("filename",filename,"url",r.config().base(name)+ProtocolIO.path(local),"hashes",digest.isEmpty()?Json.map():Json.map("sha256",digest),"requires-python",Json.str(file,"requires-python",""),"yanked",file.getOrDefault("yanked",false)));
        }
        return files;
    }
    private Map<String,Object> parseHtml(String text,String base)throws IOException {
        List<Object> files=new ArrayList<>();
        new ParserDelegator().parse(new StringReader(text),new HTMLEditorKit.ParserCallback() {
            public void handleStartTag(HTML.Tag tag,MutableAttributeSet attributes,int position) {
                if(tag!=HTML.Tag.A)return; Object href=attributes.getAttribute(HTML.Attribute.HREF); if(href==null)return;
                try {
                    URI url=URI.create(base).resolve(href.toString()); String file=url.getPath().replaceAll(".*/",""); Map<String,Object> item=Json.map("filename",file,"url",url.toString(),"hashes",Json.map());
                    for(var e=attributes.getAttributeNames(); e.hasMoreElements(); ) {
                        Object key=e.nextElement(); if(key.toString().equals("data-requires-python"))item.put("requires-python",attributes.getAttribute(key).toString()); if(key.toString().equals("data-yanked"))item.put("yanked",attributes.getAttribute(key).toString());
                    }
                    files.add(item);
                } catch(IllegalArgumentException ignored) {
                    /* Ignore malformed anchors, never execute index HTML. */
                }
            }
        },true);
        return Json.map("files",files);
    }
}
