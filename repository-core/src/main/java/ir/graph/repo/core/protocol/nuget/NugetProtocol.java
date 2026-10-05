package ir.graph.repo.core.protocol.nuget;

import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.util.Versions;
import ir.graph.repo.core.storage.ContentStore;
import ir.graph.repo.core.protocol.StreamingMultipart;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.service.RepositoryService;
import ir.graph.repo.core.domain.RepositoryPrincipal;
import ir.graph.repo.core.protocol.ProtocolExchange;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** NuGet V3 discovery/flat container/registrations/search, package push and unlist. */
public final class NugetProtocol implements ir.graph.repo.core.protocol.PackageProtocol {
    @Override public Set<String> formats() {
        return Set.of("nuget");
    }
    private final RepositoryService r;
    public NugetProtocol(RepositoryService r) {
        this.r=r;
    }
    private String id(String s) {
        if(!s.matches("[A-Za-z0-9_.-]{1,100}"))throw RepositoryException.bad("Invalid NuGet package ID");
        return s.toLowerCase(Locale.ROOT);
    }
    private String base(Map<String,Object> repo) {
        return r.config().base((String)repo.get("name"));
    }
    private String pkgPath(String id,String version) {
        return "v3/flatcontainer/"+id+"/"+version+"/"+id+"."+version+".nupkg";
    }
    public void handle(ProtocolExchange x,Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        if(path.equals("index.json")||path.equals("v3/index.json")) {
            ProtocolIO.method(x,"GET","HEAD");
            String b=base(repo);
            ProtocolIO.json(x,200,Json.map("version","3.0.0","resources",List.of(
            Json.map("@id",b+"v3/flatcontainer/","@type","PackageBaseAddress/3.0.0"),
            Json.map("@id",b+"v3/registration/","@type","RegistrationsBaseUrl/3.6.0"),
            Json.map("@id",b+"v3/search","@type","SearchQueryService/3.5.0"),
            Json.map("@id",b+"v3/autocomplete","@type","SearchAutocompleteService/3.5.0"),
            Json.map("@id",b+"v2/package","@type","PackagePublish/2.0.0"))));
            return;
        }
        if(path.equals("v2/package")||path.equals("api/v2/package")) {
            ProtocolIO.method(x,"PUT");
            r.hosted(repo);
            publish(x,repo);
            return;
        }
        if(path.startsWith("v2/package/")) {
            ProtocolIO.method(x,"DELETE");
            r.hosted(repo);
            String[] parts=path.substring(11).split("/");
            if(parts.length!=2)throw RepositoryException.bad("Expected package ID and version");
            unlist(repo,id(parts[0]),Versions.nuget(parts[1]));
            ProtocolIO.json(x,200,Json.map("ok",true));
            return;
        }
        ProtocolIO.method(x,"GET","HEAD");
        if(path.equals("v3/search")||path.equals("v3/autocomplete")) {
            search(x,repo,p,path.endsWith("autocomplete"));
            return;
        }
        if(path.startsWith("v3/flatcontainer/")) {
            String relative=path.substring(17);
            String[] parts=relative.split("/");
            if(parts.length==2&&parts[1].equals("index.json")) {
                ProtocolIO.json(x,200,Json.map("versions",versions(repo,id(parts[0]),p)));
                return;
            }
            if(parts.length!=3)throw RepositoryException.missing();
            String pkg=id(parts[0]),version=Versions.nuget(parts[1]),file=parts[2].toLowerCase(Locale.ROOT);
            boolean hash=file.equals(pkg+"."+version+".nupkg.sha512");
            String filename=hash?file.substring(0,file.length()-7):file;
            if(!Set.of(pkg+"."+version+".nupkg",pkg+".nuspec").contains(filename))throw RepositoryException.missing();
            String local="v3/flatcontainer/"+pkg+"/"+version+"/"+filename;
            var a=download(repo,local,p);
            if(hash)ProtocolIO.text(x,200,"text/plain",Base64.getEncoder().encodeToString(HexFormat.of().parseHex((String)a.get("sha512"))));
            else ProtocolIO.asset(x,r.store(),a,true);
            return;
        }
        if(path.startsWith("v3/registration/")) {
            String[] parts=path.substring(16).split("/");
            if(parts.length!=2)throw RepositoryException.missing();
            String pkg=id(parts[0]);
            var entries=entries(repo,pkg,p);
            if(entries.isEmpty())throw RepositoryException.missing();
            String index=base(repo)+"v3/registration/"+pkg+"/index.json";
            if(!parts[1].equals("index.json")) {
                String version=Versions.nuget(parts[1].replaceAll("\\.json$",""));
                var e=entries.get(version);
                if(e==null)throw RepositoryException.missing();
                ProtocolIO.json(x,200,registration(repo,pkg,version,e));
                return;
            }
            List<Object> items=new ArrayList<>();
            for(var e:entries.entrySet())items.add(registration(repo,pkg,e.getKey(),e.getValue()));
            var keys=new ArrayList<>(entries.keySet());
            ProtocolIO.json(x,200,Json.map("@id",index,"count",1,"items",List.of(Json.map("@id",index,"count",items.size(),"lower",keys.getFirst(),"upper",keys.getLast(),"items",items))));
            return;
        }
        throw RepositoryException.missing();
    }
    private Map<String,Object> service(Map<String,Object> repo)throws Exception {
        return r.cachedJson(repo,"nuget-index",(String)repo.get("upstream"),"application/json");
    }
    private String resource(Map<String,Object> repo,String type)throws Exception {
        for(Object o:Json.array(service(repo).get("resources"))) {
            var resource=Json.object(o);
            if(Json.str(resource,"@type","").startsWith(type))return Json.str(resource,"@id","");
        }
        throw new RepositoryException(502,"UPSTREAM_METADATA","NuGet upstream does not advertise "+type);
    }
    private String join(String base,String suffix) {
        return (base.endsWith("/")?base:base+"/")+suffix;
    }
    private List<String> versions(Map<String,Object> repo,String pkg,RepositoryPrincipal p)throws Exception {
        Set<String> versions=new TreeSet<>(Versions::compare);
        for(var leaf:r.leaves(repo,p))try {
            if(leaf.get("type").equals("hosted")) {
                for(var a:r.store().assets((String)leaf.get("name"))) {
                    var m=Json.object(a.get("meta"));
                    if("nuget".equals(m.get("kind"))&&pkg.equals(m.get("package")))versions.add((String)m.get("version"));
                }
            }
            else {
                var json=r.cachedJson(leaf,"nuget-versions:"+pkg,join(resource(leaf,"PackageBaseAddress"),pkg+"/index.json"),"application/json");
                for(Object v:Json.array(json.get("versions")))versions.add(Versions.nuget(v.toString()));
            }
        } catch(RepositoryException e) {
            if(e.status!=404)throw e;
        }
        if(versions.isEmpty())throw RepositoryException.missing();
        return new ArrayList<>(versions);
    }
    private Map<String,Object> download(Map<String,Object> repo,String path,RepositoryPrincipal p)throws Exception {
        for(var leaf:r.leaves(repo,p))try {
            var local=r.store().asset((String)leaf.get("name"),path);
            if(leaf.get("type").equals("hosted")) {
                if(local!=null)return local;
            }
            else return r.remoteAsset(leaf,path,join(resource(leaf,"PackageBaseAddress"),ProtocolIO.path(path.substring(17))),"");
        } catch(RepositoryException e) {
            if(e.status!=404)throw e;
        }
        throw RepositoryException.missing();
    }
    private SortedMap<String,Map<String,Object>> entries(Map<String,Object> repo,String pkg,RepositoryPrincipal p)throws Exception {
        SortedMap<String,Map<String,Object>> entries=new TreeMap<>(Versions::compare);
        for(var leaf:r.leaves(repo,p))try {
            if(leaf.get("type").equals("hosted")) {
                for(var a:r.store().assets((String)leaf.get("name"))) {
                    var m=Json.object(a.get("meta"));
                    if(!"nuget".equals(m.get("kind"))||!pkg.equals(m.get("package")))continue;
                    var catalog=Json.object(m.get("catalog"));
                    catalog.put("listed",Json.bool(m,"listed",true));
                    entries.putIfAbsent((String)m.get("version"),catalog);
                }
            } else {
                var index=r.cachedJson(leaf,"nuget-registration:"+pkg,join(resource(leaf,"RegistrationsBaseUrl"),pkg+"/index.json"),"application/json");
                for(Object item:Json.array(index.getOrDefault("items",List.of()))) {
                    var page=Json.object(item);
                    if(!page.containsKey("items")) {
                        String url=Json.str(page,"@id","");
                        page=r.cachedJson(leaf,"nuget-page:"+ContentStore.digest(url),url,"application/json");
                    }
                    for(Object value:Json.array(page.getOrDefault("items",List.of()))) {
                        var v=Json.object(value);
                        Object raw=v.get("catalogEntry");
                        Map<String,Object> catalog=raw instanceof Map<?,?>?Json.object(raw):r.cachedJson(leaf,"nuget-catalog:"+ContentStore.digest(raw.toString()),raw.toString(),"application/json");
                        entries.putIfAbsent(Versions.nuget(Json.str(catalog,"version","")),catalog);
                    }
                }
            }
        } catch(RepositoryException e) {
            if(e.status!=404)throw e;
        }
        return entries;
    }
    private Map<String,Object> registration(Map<String,Object> repo,String pkg,String version,Map<String,Object> metadata) {
        var catalog=new LinkedHashMap<>(metadata);
        String leaf=base(repo)+"v3/registration/"+pkg+"/"+version+".json",content=base(repo)+pkgPath(pkg,version);
        catalog.put("@id",leaf);
        catalog.put("@type","PackageDetails");
        catalog.put("packageContent",content);
        catalog.put("version",version);
        return Json.map("@id",leaf,"catalogEntry",catalog,"packageContent",content,"listed",catalog.getOrDefault("listed",true),"registration",base(repo)+"v3/registration/"+pkg+"/index.json");
    }
    private void publish(ProtocolExchange x,Map<String,Object> repo)throws Exception {
        String type=x.header("Content-Type");
        if(type!=null&&type.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            try(var form=StreamingMultipart.parse(x.input(),type,r.store().temp(),r.config().maxUpload())) {
                var part=form.parts.stream().filter(a->!a.filename().isEmpty()).findFirst().orElseThrow(()->RepositoryException.bad("NuGet package file is missing"));
                savePackage(repo,part.file());
            }
        } else {
            Path temp=Files.createTempFile(r.store().temp(),"nuget-",".nupkg");
            try {
                try(var out=Files.newOutputStream(temp)) {
                    byte[] b=new byte[65536];
                    int n;
                    long size=0;
                    while((n=x.input().read(b))!=-1) {
                        size+=n;
                        if(r.config().maxUpload()>0&&size>r.config().maxUpload())throw new RepositoryException(413,"BODY_TOO_LARGE","NuGet upload exceeds configured per-request limit");
                        out.write(b,0,n);
                    }
                }
                savePackage(repo,temp);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        ProtocolIO.json(x,201,Json.map("ok",true));
    }
    private void savePackage(Map<String,Object> repo,Path file)throws Exception {
        byte[] nuspec=null;
        try(ZipFile zip=new ZipFile(file.toFile())) {
            for(var e=zip.entries(); e.hasMoreElements(); ) {
                var entry=e.nextElement();
                if(entry.isDirectory()||!entry.getName().toLowerCase(Locale.ROOT).endsWith(".nuspec"))continue;
                if(nuspec!=null)throw RepositoryException.bad("NuGet package contains more than one nuspec");
                try(InputStream in=zip.getInputStream(entry)) {
                    nuspec=ProtocolIO.bytes(in,4194304);
                }
            }
        } catch(ZipException e) {
            throw RepositoryException.bad("Invalid NuGet ZIP archive");
        }
        if(nuspec==null)throw RepositoryException.bad("NuGet package is missing its nuspec");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        Document doc;
        try {
            doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(nuspec));
        } catch(org.xml.sax.SAXException e) {
            throw RepositoryException.bad("Invalid or unsafe nuspec XML");
        }
        NodeList nodes=doc.getElementsByTagNameNS("*","metadata");
        if(nodes.getLength()!=1)throw RepositoryException.bad("NuGet metadata element is missing or duplicated");
        Element metadata=(Element)nodes.item(0);
        String originalId=child(metadata,"id"),pkg=id(originalId),version=Versions.nuget(child(metadata,"version"));
        List<Object> groups=new ArrayList<>();
        NodeList dependencies=metadata.getElementsByTagNameNS("*","dependencies");
        if(dependencies.getLength()>0) {
            Element root=(Element)dependencies.item(0);
            NodeList groupNodes=root.getElementsByTagNameNS("*","group");
            if(groupNodes.getLength()==0)groups.add(Json.map("dependencies",dependencyList(root)));
            else for(int i=0; i<groupNodes.getLength(); i++) {
                Element group=(Element)groupNodes.item(i);
                groups.add(Json.map("targetFramework",group.getAttribute("targetFramework"),"dependencies",dependencyList(group)));
            }
        }
        var catalog=Json.map("id",originalId,"version",version,"authors",child(metadata,"authors"),"description",child(metadata,"description"),"tags",child(metadata,"tags"),"title",child(metadata,"title"),"dependencyGroups",groups,"listed",true,"published",Instant.now().toString(),"requireLicenseAcceptance",child(metadata,"requireLicenseAcceptance").equalsIgnoreCase("true"));
        ContentStore.Blob binary;
        try(InputStream in=Files.newInputStream(file)) {
            binary=r.store().blob(in,r.config().maxUpload());
        }
        ContentStore.Blob spec=r.store().blob(nuspec);
        String name=(String)repo.get("name"),path=pkgPath(pkg,version),specPath="v3/flatcontainer/"+pkg+"/"+version+"/"+pkg+".nuspec";
        synchronized(r.lock(name+"/nuget/"+pkg)) {
            var old=r.store().asset(name,path);
            if(old!=null&&!Boolean.TRUE.equals(repo.get("redeploy")))throw new RepositoryException(409,"IMMUTABLE","NuGet package version already exists");
            var asset=r.store().assetRecord(name,path,binary,"application/octet-stream",Json.map("kind","nuget","package",pkg,"version",version,"listed",true,"catalog",catalog));
            var xml=r.store().assetRecord(name,specPath,spec,"application/xml",Json.map("kind","nuspec","package",pkg,"version",version));
            r.store().transact(List.of(ContentStore.set("assets",ContentStore.key(name,path),asset),ContentStore.set("assets",ContentStore.key(name,specPath),xml)));
        }
    }
    private String child(Element node,String name) {
        for(Node c=node.getFirstChild(); c!=null; c=c.getNextSibling())if(c instanceof Element e&&name.equals(e.getLocalName()))return e.getTextContent().trim();
        return "";
    }
    private List<Object> dependencyList(Element node) {
        List<Object> values=new ArrayList<>();
        NodeList list=node.getElementsByTagNameNS("*","dependency");
        for(int i=0; i<list.getLength(); i++) {
            Element dep=(Element)list.item(i);
            values.add(Json.map("id",dep.getAttribute("id"),"range",dep.hasAttribute("version")?dep.getAttribute("version"):"(,)"));
        }
        return values;
    }
    private void unlist(Map<String,Object> repo,String pkg,String version)throws IOException {
        String name=(String)repo.get("name"),path=pkgPath(pkg,version);
        synchronized(r.lock(name+"/nuget/"+pkg)) {
            var a=r.store().asset(name,path);
            if(a==null)throw RepositoryException.missing();
            Json.object(a.get("meta")).put("listed",false);
            r.store().put("assets",ContentStore.key(name,path),a);
        }
    }
    private void search(ProtocolExchange x,Map<String,Object> repo,RepositoryPrincipal p,boolean autocomplete)throws Exception {
        var q=ProtocolIO.query(x);
        String query=q.getOrDefault("q","").toLowerCase(Locale.ROOT);
        boolean prerelease=Boolean.parseBoolean(q.getOrDefault("prerelease","false"));
        int skip=ProtocolIO.page(q,"skip",0,Integer.MAX_VALUE),take=ProtocolIO.page(q,"take",20,1000);
        if(autocomplete&&q.containsKey("id")) {
            List<String> versions=versions(repo,id(q.get("id")),p).stream().filter(v->prerelease||!v.contains("-")).toList();
            ProtocolIO.json(x,200,Json.map("totalHits",versions.size(),"data",versions));
            return;
        }
        Set<String> names=new TreeSet<>();
        boolean limitedRemote=false;
        long remoteTotal=0;
        for(var leaf:r.leaves(repo,p)) {
            if(leaf.get("type").equals("hosted"))for(var a:r.store().assets((String)leaf.get("name"))) {
                var m=Json.object(a.get("meta"));
                if("nuget".equals(m.get("kind"))&&Json.str(m,"package","").contains(query))names.add((String)m.get("package"));
            }
            else if(!Boolean.TRUE.equals(leaf.get("offline"))) {
                String search=resource(leaf,"SearchQueryService");
                var result=r.upstream().json(search+(search.contains("?")?"&":"?")+"q="+ProtocolIO.enc(query)+"&skip=0&take="+Math.min(1000,(long)skip+take)+"&prerelease="+prerelease+"&semVerLevel=2.0.0",leaf,"application/json");
                remoteTotal+=Json.num(result,"totalHits",0);
                for(Object item:Json.array(result.getOrDefault("data",List.of())))names.add(id(Json.str(Json.object(item),"id","")));
                limitedRemote=true;
            } else for(var d:r.store().all("docs"))if(leaf.get("name").equals(d.get("repo"))&&Json.str(d,"key","").startsWith("nuget-registration:")) {
                String id=Json.str(d,"key","").substring(19);
                if(id.contains(query))names.add(id);
            }
        }
        List<Object> results=new ArrayList<>();
        List<String> matched=new ArrayList<>();
        for(String name:names) {
            var entries=entries(repo,name,p);
            entries.entrySet().removeIf(e->(!prerelease&&e.getKey().contains("-"))||Boolean.FALSE.equals(e.getValue().get("listed")));
            if(entries.isEmpty())continue;
            matched.add(name);
            int index=matched.size()-1;
            if(index<skip||results.size()>=take)continue;
            if(autocomplete) {
                results.add(name);
                continue;
            }
            String latest=entries.lastKey();
            var metadata=new LinkedHashMap<>(entries.get(latest));
            metadata.put("version",latest);
            metadata.put("@id",base(repo)+"v3/registration/"+name+"/index.json");
            metadata.put("authors",Arrays.asList(Json.str(metadata,"authors","").split(",")));
            metadata.put("versions",entries.keySet().stream().map(v->Json.map("version",v,"@id",base(repo)+"v3/registration/"+name+"/"+v+".json")).toList());
            results.add(metadata);
        }
        ProtocolIO.json(x,200,Json.map("totalHits",matched.size(),"data",results,"graphRemoteSearchWindowed",limitedRemote,"graphUpstreamReportedHits",remoteTotal));
    }
}
