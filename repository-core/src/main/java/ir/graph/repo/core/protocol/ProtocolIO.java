package ir.graph.repo.core.protocol;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;
import ir.graph.repo.core.storage.ContentStore;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ProtocolIO {
    private ProtocolIO() {
    }
    public static boolean read(ProtocolExchange x) {
        return x.method().equals("GET")||x.method().equals("HEAD");
    }
    public static void method(ProtocolExchange x,String... allowed) {
        if(!List.of(allowed).contains(x.method())) {
            x.responseHeaders().set("Allow",String.join(", ",allowed));
            throw new RepositoryException(405,"METHOD_NOT_ALLOWED","HTTP method is not supported for this endpoint");
        }
    }
    public static byte[] body(ProtocolExchange x,long max)throws IOException {
        return bytes(x.input(),max);
    }
    public static byte[] bytes(InputStream in,long max)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[65536];
        int n;
        long size=0;
        while((n=in.read(b))!=-1) {
            size+=n;
            if(max>0&&size>max)throw new RepositoryException(413,"BODY_TOO_LARGE","Request exceeds the configured per-request parsing limit");
            out.write(b,0,n);
        }
        return out.toByteArray();
    }
    public static Map<String,Object> bodyJson(ProtocolExchange x,RepositorySettings config)throws IOException {
        String type=x.header("Content-Type");
        if(type==null||!type.split(";", 2)[0].strip().equalsIgnoreCase("application/json"))throw new RepositoryException(415,"CONTENT_TYPE","Content-Type must be application/json");
        return Json.obj(new String(body(x,config.maxJson()),StandardCharsets.UTF_8));
    }
    public static void json(ProtocolExchange x,int code,Object value)throws IOException {
        send(x,code,"application/json; charset=utf-8",Json.stringify(value).getBytes(StandardCharsets.UTF_8));
    }
    public static void text(ProtocolExchange x,int code,String type,String value)throws IOException {
        send(x,code,type,value.getBytes(StandardCharsets.UTF_8));
    }
    public static void send(ProtocolExchange x,int code,String type,byte[] data)throws IOException {
        x.responseHeaders().set("Content-Type",type);
        if(x.method().equals("HEAD")) {
            x.responseHeaders().set("Content-Length",String.valueOf(data.length));
            x.respond(code,-1);
            return;
        }
        if(data.length==0||code==204||code==304) {
            x.respond(code,-1);
            return;
        }
        x.respond(code,data.length);
        x.output().write(data);
    }
    public static Map<String,String> query(ProtocolExchange x) {
        return query(x.uri().getRawQuery());
    }
    public static Map<String,String> query(String query) {
        Map<String,String> values=new LinkedHashMap<>();
        if(query==null||query.isEmpty())return values;
        for(String pair:query.split("&")) {
            String[] p=pair.split("=",2);
            String k=URLDecoder.decode(p[0],StandardCharsets.UTF_8);
            String v=p.length==1?"":URLDecoder.decode(p[1],StandardCharsets.UTF_8);
            if(values.putIfAbsent(k,v)!=null)throw RepositoryException.bad("Duplicate query parameter: "+k);
        }
        return values;
    }
    public static String enc(String s) {
        return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");
    }
    public static String path(String s) {
        return Arrays.stream(s.split("/",-1)).map(ProtocolIO::enc).reduce((a,b)->a+"/"+b).orElse("");
    }
    public static String html(Object v) {
        return Objects.toString(v,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    public static int page(Map<String,String> query,String name,int fallback,int max) {
        int n=Integer.parseInt(query.getOrDefault(name,String.valueOf(fallback)));
        if(n<0||n>max)throw RepositoryException.bad("Invalid pagination parameter");
        return n;
    }
    public static String contentType(String name) {
        String s=name.toLowerCase(Locale.ROOT);
        if(s.endsWith(".json"))return "application/json";
        if(s.endsWith(".xml")||s.endsWith(".pom")||s.endsWith(".nuspec"))return "application/xml";
        if(s.endsWith(".tgz")||s.endsWith(".gz"))return "application/gzip";
        if(s.endsWith(".jar"))return "application/java-archive";
        if(s.endsWith(".zip")||s.endsWith(".nupkg")||s.endsWith(".whl"))return "application/zip";
        if(s.matches(".*\\.(sha1|sha256|sha512|md5|txt)$"))return "text/plain";
        return "application/octet-stream";
    }
    public static void asset(ProtocolExchange x,ContentStore store,Map<String,Object> asset,boolean attachment)throws IOException {
        method(x,"GET","HEAD");
        String digest=(String)asset.get("sha256");
        store.assertDownloadAllowed(digest);
        Path file=store.blobPath(digest);
        if(!Files.isRegularFile(file))throw new RepositoryException(500,"BLOB_MISSING","Blob is missing; restore the repository backup");
        long size=Files.size(file);
        String etag="\"sha256:"+digest+"\"";
        var h=x.responseHeaders();
        h.set("ETag",etag);
        h.set("Accept-Ranges","bytes");
        h.set("Content-Type",Json.str(asset,"contentType","application/octet-stream"));
        h.set("Cache-Control","private, max-age=0, must-revalidate");
        h.set("X-Checksum-Sha256",digest);
        h.set("Content-Security-Policy","sandbox; default-src 'none'");
        String lastModified=DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.parse((String)asset.get("updated")).atZone(ZoneOffset.UTC));
        h.set("Last-Modified",lastModified);
        if(attachment) {
            String name=((String)asset.get("path")).replaceAll(".*/","");
            h.set("Content-Disposition","attachment; filename*=UTF-8''"+enc(name));
        }
        String match=x.header("If-None-Match");
        if(match!=null&&Arrays.stream(match.split(",")).map(String::trim).anyMatch(t->t.equals("*")||t.equals(etag)||t.equals("W/"+etag))) {
            x.respond(304,-1);
            return;
        }
        long start=0,end=size-1;
        int status=200;
        String range=x.header("Range");
        String ifRange=x.header("If-Range");
        if(range!=null&&(ifRange==null||ifRange.equals(etag)||ifRange.equals(lastModified))) {
            try {
                if(!range.startsWith("bytes=")||range.contains(",")||size==0)throw new IllegalArgumentException();
                String[] p=range.substring(6).split("-",-1);
                if(p.length!=2)throw new IllegalArgumentException();
                if(p[0].isEmpty()) {
                    long suffix=Long.parseLong(p[1]);
                    if(suffix<=0)throw new IllegalArgumentException();
                    start=Math.max(0,size-suffix);
                }
                else {
                    start=Long.parseLong(p[0]);
                    if(!p[1].isEmpty())end=Math.min(size-1,Long.parseLong(p[1]));
                }
                if(start<0||start>=size||end<start)throw new IllegalArgumentException();
                status=206;
                h.set("Content-Range","bytes "+start+"-"+end+"/"+size);
            } catch(IllegalArgumentException e) {
                h.set("Content-Range","bytes */"+size);
                throw new RepositoryException(416,"RANGE_INVALID","Requested byte range cannot be satisfied");
            }
        }
        long length=size==0?0:end-start+1;
        h.set("Content-Length",Long.toString(length));
        if(x.method().equals("HEAD")||length==0) {
            x.respond(status,-1);
            return;
        }
        x.respond(status,length);
        try(InputStream in=Files.newInputStream(file)) {
            in.skipNBytes(start);
            byte[] buffer=new byte[65536];
            long remaining=length;
            while(remaining>0) {
                int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));
                if(n==-1)throw new EOFException();
                x.output().write(buffer,0,n);
                remaining-=n;
            }
        }
    }
}
