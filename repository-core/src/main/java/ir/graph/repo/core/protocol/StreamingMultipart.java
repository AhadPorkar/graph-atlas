package ir.graph.repo.core.protocol;

import ir.graph.repo.core.domain.RepositoryException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Streaming multipart/form-data parser: part payloads go to temporary files, not heap arrays. */
public final class StreamingMultipart implements AutoCloseable {
    public record Part(String name,String filename,String type,Path file) {
        public String text()throws IOException {
            if(Files.size(file)>1048576)throw RepositoryException.bad("StreamingMultipart metadata field is too large");
            return Files.readString(file);
        }
    }
    public final List<Part> parts=new ArrayList<>();
    private final List<Path> temporary=new ArrayList<>();
    private static final Pattern BOUNDARY=Pattern.compile("(?:^|;)\\s*boundary=(?:\"([^\"]+)\"|([^;\\s]+))",Pattern.CASE_INSENSITIVE);
    public static StreamingMultipart parse(InputStream stream,String contentType,Path temporary,long max)throws IOException {
        StreamingMultipart result=new StreamingMultipart();
        try {
            result.read(stream,contentType,temporary,max);
            return result;
        } catch(Throwable e) {
            result.close();
            throw e;
        }
    }
    public Part file(String name) {
        return parts.stream().filter(p->p.name().equals(name)&&!p.filename().isEmpty()).findFirst().orElseThrow(()->RepositoryException.bad("Missing multipart file field: "+name));
    }
    public String text(String name,String fallback)throws IOException {
        for(Part p:parts)if(p.name().equals(name)&&p.filename().isEmpty())return p.text();
        return fallback;
    }
    private void read(InputStream source,String contentType,Path tmp,long max)throws IOException {
        if(contentType==null||!contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data"))throw new RepositoryException(415,"CONTENT_TYPE","Expected multipart/form-data");
        Matcher match=BOUNDARY.matcher(contentType);
        if(!match.find())throw RepositoryException.bad("StreamingMultipart boundary is missing");
        String boundary=match.group(1)!=null?match.group(1):match.group(2);
        if(boundary.length()>200||boundary.chars().anyMatch(c->c<32||c>126))throw RepositoryException.bad("Invalid multipart boundary");
        PushbackInputStream in=new PushbackInputStream(new BufferedInputStream(new FilterInputStream(source) {
            long total; public int read()throws IOException {
                int b=super.read(); if(b>=0)check(1); return b;
            }
            public int read(byte[] b,int off,int len)throws IOException {
                int n=this.in.read(b,off,len); if(n>0)check(n); return n;
            }
            void check(int n) {
                total+=n; if(max>0&&total>max)throw new RepositoryException(413,"BODY_TOO_LARGE","StreamingMultipart request exceeds the configured per-request limit");
            }
        },65536),2);
        if(!line(in).equals("--"+boundary))throw RepositoryException.bad("Malformed multipart opening boundary");
        byte[] delimiter=("\r\n--"+boundary).getBytes(StandardCharsets.US_ASCII);
        boolean done=false;
        while(!done) {
            Map<String,String> headers=new HashMap<>();
            int headerBytes=0;
            while(true) {
                String l=line(in);
                headerBytes+=l.length();
                if(headerBytes>32768)throw RepositoryException.bad("StreamingMultipart headers are too large");
                if(l.isEmpty())break;
                int colon=l.indexOf(':');
                if(colon<1)throw RepositoryException.bad("Invalid multipart header");
                headers.put(l.substring(0,colon).trim().toLowerCase(Locale.ROOT),l.substring(colon+1).trim());
            }
            String disposition=headers.getOrDefault("content-disposition","");
            if(!disposition.toLowerCase(Locale.ROOT).startsWith("form-data"))throw RepositoryException.bad("Invalid multipart disposition");
            String name=parameter(disposition,"name"),filename=parameter(disposition,"filename");
            if(name.isEmpty())throw RepositoryException.bad("StreamingMultipart field name is missing");
            Path file=Files.createTempFile(tmp,"multipart-",".part");
            temporary.add(file);
            try(OutputStream out=new BufferedOutputStream(Files.newOutputStream(file),65536)) {
                copyUntil(in,out,delimiter);
            }
            int a=in.read(),b=in.read();
            if(a=='-'&&b=='-')done=true;
            else if(a!='\r'||b!='\n')throw RepositoryException.bad("Malformed multipart closing boundary");
            parts.add(new Part(name,filename,headers.getOrDefault("content-type","application/octet-stream"),file));
        }
    }
    private static String parameter(String value,String name) {
        Matcher m=Pattern.compile("(?:^|;)\\s*"+name+"=(?:\"([^\"]*)\"|([^;\\s]*))",Pattern.CASE_INSENSITIVE).matcher(value);
        if(!m.find())return "";
        return m.group(1)==null?m.group(2):m.group(1);
    }
    private static String line(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        while(true) {
            int b=in.read();
            if(b==-1)throw RepositoryException.bad("Truncated multipart header");
            if(b=='\r') {
                if(in.read()!='\n')throw RepositoryException.bad("Malformed multipart line ending");
                return out.toString(StandardCharsets.UTF_8);
            }
            out.write(b);
            if(out.size()>8192)throw RepositoryException.bad("StreamingMultipart header line is too long");
        }
    }
    private static void copyUntil(PushbackInputStream in,OutputStream out,byte[] delimiter)throws IOException {
        byte[] pending=new byte[delimiter.length];
        int used=0,start=0;
        while(true) {
            int b=in.read();
            if(b<0)throw RepositoryException.bad("Truncated multipart payload");
            pending[(start+used)%pending.length]=(byte)b;
            used++;
            if(used<pending.length)continue;
            boolean equal=true;
            for(int i=0; i<pending.length; i++)if(pending[(start+i)%pending.length]!=delimiter[i]) {
                equal=false;
                break;
            }
            if(equal) {
                int a=in.read(),next=in.read();
                if(a>=0&&next>=0)in.unread(new byte[] {
                    (byte)a,(byte)next
                });
                else if(a>=0)in.unread(a);
                if((a=='-'&&next=='-')||(a=='\r'&&next=='\n'))return;
            }
            out.write(pending[start]);
            start=(start+1)%pending.length;
            used--;
        }
    }
    public void close()throws IOException {
        IOException first=null;
        for(Path p:temporary)try {
            Files.deleteIfExists(p);
        } catch(IOException e) {
            if(first==null)first=e;
        }
        if(first!=null)throw first;
    }
}
