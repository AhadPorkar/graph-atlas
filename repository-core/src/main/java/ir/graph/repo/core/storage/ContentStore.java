package ir.graph.repo.core.storage;

import ir.graph.repo.core.config.RepositorySettings;
import ir.graph.repo.core.util.Json;
import ir.graph.repo.core.domain.RepositoryException;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
* Single-node durable store. Immutable SHA-256 blobs + fsynced, transactional JSONL WAL.
* A whole metadata transaction is committed in one line before any in-memory mutation.
* The last incomplete WAL line is discarded on recovery; corruption elsewhere fails closed.
* No component count, user count, request/day, repository count or license quota exists.
*/
public final class ContentStore implements AutoCloseable {
    private final RepositorySettings config;
    private final Path journalPath;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private FileChannel journal;
    private final Map<String,Map<String,Object>> tables=new ConcurrentHashMap<>();
    private boolean failed;
    public final AtomicLong requests=new AtomicLong();
    public final Instant started=Instant.now();
    public ContentStore(RepositorySettings config) throws IOException {
        this.config=config;
        Files.createDirectories(config.home());
        try {
            Files.setPosixFilePermissions(config.home(),PosixFilePermissions.fromString("rwx------"));
        } catch(UnsupportedOperationException ignored) {
        }
        FileChannel channel = FileChannel.open(config.home().resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = channel.tryLock();
            if (acquired == null) throw new IOException("Data directory is already in use");
        } catch (IOException | RuntimeException e) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw new IOException("Cannot lock the data directory; another server may be using it", e);
        }
        lockChannel = channel;
        lock = acquired;
        journalPath = config.home().resolve("metadata.jsonl");
        try {
            for (String table : List.of("repos", "users", "tokens", "assets", "docs", "releases", "holds")) tables.put(table, new ConcurrentHashMap<>());
            Files.createDirectories(config.home().resolve("blobs"));
            Files.createDirectories(temp());
            Path snapshot = config.home().resolve("snapshot.jsonl");
            if (Files.exists(snapshot)) replay(snapshot, false);
            if (Files.exists(journalPath)) replay(journalPath, true);
            journal = FileChannel.open(journalPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            try {
                lock.release();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            try {
                lockChannel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }
    public synchronized boolean failed() {
        return failed;
    }
    public synchronized int count(String table) {
        return table(table).size();
    }
    public Path home() {
        return config.home();
    }
    public Path temp() {
        return config.home().resolve("tmp");
    }
    public synchronized Map<String,Object> get(String table,String key) {
        Object v=table(table).get(key);
        return v==null?null:Json.object(Json.copy(v));
    }
    public synchronized List<Map<String,Object>> all(String table) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Object v:table(table).values())result.add(Json.object(Json.copy(v)));
        return result;
    }
    private Map<String,Object> table(String name) {
        var t=tables.get(name);
        if(t==null)throw new IllegalArgumentException("Unknown table");
        return t;
    }
    public static Map<String,Object> set(String table,String key,Map<String,Object> value) {
        return Json.map("table",table,"key",key,"value",value);
    }
    public static Map<String,Object> del(String table,String key) {
        return Json.map("table",table,"key",key,"delete",true);
    }
    public synchronized void put(String table,String key,Map<String,Object> value) throws IOException {
        transact(List.of(set(table,key,value)));
    }
    public synchronized void delete(String table,String key) throws IOException {
        transact(List.of(del(table,key)));
    }
    public synchronized void transact(List<Map<String,Object>> ops) throws IOException {
        if(failed)throw new IOException("Metadata store needs recovery after an I/O failure");
        Set<String> newRepos=new HashSet<>();
        for(var op:ops)if("repos".equals(op.get("table"))&&!Boolean.TRUE.equals(op.get("delete")))newRepos.add((String)op.get("key"));
        for(var op:ops) {
            String tableName=(String)op.get("table");
            table(tableName);
            if(!(op.get("key") instanceof String))throw new IllegalArgumentException("Invalid key");
            if(!Boolean.TRUE.equals(op.get("delete"))&&Set.of("assets","docs").contains(tableName)) {
                var value=Json.object(op.get("value"));
                String repo=Json.str(value,"repo","");
                if(!repo.isEmpty()&&!newRepos.contains(repo)&&get("repos",repo)==null)throw RepositoryException.missing();
            }
        }
        // Copy before writing: callers cannot mutate committed state by retaining references.
        Map<String,Object> tx=Json.obj(Json.stringify(Json.map("ops",ops)));
        byte[] bytes=(Json.stringify(tx)+"\n").getBytes(StandardCharsets.UTF_8);
        try {
            writeFully(journal,ByteBuffer.wrap(bytes));
            journal.force(true);
            apply(tx);
        }
        catch(IOException e) {
            failed=true;
            throw e;
        }
    }
    private void apply(Map<String,Object> tx) {
        for(Object item:Json.array(tx.get("ops"))) {
            var op=Json.object(item);
            var t=table((String)op.get("table"));
            String key=(String)op.get("key");
            if(Boolean.TRUE.equals(op.get("delete")))t.remove(key);
            else t.put(key,op.get("value"));
        }
    }
    private void replay(Path path,boolean truncateTail) throws IOException {
        long valid=0,position=0;
        try(InputStream in=new BufferedInputStream(Files.newInputStream(path))) {
            ByteArrayOutputStream line=new ByteArrayOutputStream();
            int b;
            while((b=in.read())!=-1) {
                position++;
                if(b!='\n') {
                    line.write(b);
                    continue;
                }
                if(line.size()==0)throw new IOException("Empty metadata record at "+position);
                try {
                    apply(Json.obj(line.toString(StandardCharsets.UTF_8)));
                } catch(RuntimeException e) {
                    throw new IOException("Corrupt metadata record at "+position,e);
                }
                line.reset();
                valid=position;
            }
            if(line.size()>0&&!truncateTail)throw new IOException("Incomplete snapshot");
        }
        if(truncateTail&&valid<position) {
            try(FileChannel ch=FileChannel.open(path,StandardOpenOption.WRITE)) {
                ch.truncate(valid);
                ch.force(true);
            }
        }
    }
    /** Offline/administrative checkpoint. All WAL operations are idempotent set/delete operations. */
    public synchronized void compact() throws IOException {
        if(failed)throw new IOException("Metadata store needs recovery");
        Path temporary=Files.createTempFile(config.home(),"snapshot-",".tmp");
        try {
            try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                for(var table:tables.entrySet())for(var row:table.getValue().entrySet()) {
                    byte[] line=(Json.stringify(Json.map("ops",List.of(set(table.getKey(),row.getKey(),Json.object(row.getValue())))))+"\n").getBytes(StandardCharsets.UTF_8);
                    writeFully(out,ByteBuffer.wrap(line));
                }
                out.force(true);
            }
            atomicMove(temporary,config.home().resolve("snapshot.jsonl"));
            forceDirectory(config.home());
            journal.truncate(0);
            journal.force(true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    public record Blob(String sha256,String sha1,String md5,String sha512,long size) {
        public Map<String,Object> fields() {
            return Json.map("sha256",sha256,"sha1",sha1,"md5",md5,"sha512",sha512,"size",size);
        }
    }
    public Blob blob(InputStream source,long limit) throws IOException {
        Path tmp=Files.createTempFile(temp(),"blob-",".part");
        var sha=hash("SHA-256");
        var sha1=hash("SHA-1");
        var md5=hash("MD5");
        var sha512=hash("SHA-512");
        long length=0;
        try {
            try(var output=FileChannel.open(tmp,StandardOpenOption.WRITE)) {
                byte[] buffer=new byte[65536];
                int n;
                while((n=source.read(buffer))!=-1) {
                    length+=n;
                    if(limit>0&&length>limit)throw new RepositoryException(413,"BODY_TOO_LARGE","Upload exceeds the administrator-configured per-request limit");
                    sha.update(buffer,0,n);
                    sha1.update(buffer,0,n);
                    md5.update(buffer,0,n);
                    sha512.update(buffer,0,n);
                    writeFully(output,ByteBuffer.wrap(buffer,0,n));
                }
                output.force(true);
            }
            String digest=HexFormat.of().formatHex(sha.digest());
            Path target=blobPath(digest);
            Files.createDirectories(target.getParent());
            try {
                Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
            } catch(FileAlreadyExistsException e) {
                Files.deleteIfExists(tmp);
            }
            catch(AtomicMoveNotSupportedException e) {
                try {
                    Files.move(tmp,target);
                } catch(FileAlreadyExistsException exists) {
                    Files.deleteIfExists(tmp);
                }
            }
            forceDirectory(target.getParent());
            return new Blob(digest,HexFormat.of().formatHex(sha1.digest()),HexFormat.of().formatHex(md5.digest()),HexFormat.of().formatHex(sha512.digest()),length);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
    public Blob blob(byte[] bytes) throws IOException {
        return blob(new ByteArrayInputStream(bytes),config.maxUpload());
    }
    public Path blobPath(String digest) {
        if(digest==null||!digest.matches("[a-f0-9]{64}"))throw RepositoryException.bad("Invalid SHA-256 digest");
        return config.home().resolve("blobs").resolve(digest.substring(0,2)).resolve(digest);
    }
    public static String key(String repo,String path) {
        return repo+"\n"+safePath(path);
    }
    public Map<String,Object> asset(String repo,String path) {
        return get("assets",key(repo,path));
    }
    public Map<String,Object> assetRecord(String repo,String path,Blob blob,String type,Map<String,Object> meta) {
        var m=blob.fields();
        m.putAll(Json.map("repo",repo,"path",safePath(path),"contentType",type,"updated",Instant.now().toString(),"meta",meta));
        return m;
    }
    public synchronized Map<String,Object> saveAsset(String repo,String path,Blob blob,String type,Map<String,Object> meta,boolean overwrite)throws IOException {
        String key=key(repo,path);
        var old=get("assets",key);
        if(old!=null&&!overwrite) {
            if(blob.sha256().equals(old.get("sha256")))return old;
            throw new RepositoryException(409,"IMMUTABLE","This artifact already exists; redeployment is disabled");
        }
        var a=assetRecord(repo,path,blob,type,meta);
        put("assets",key,a);
        return a;
    }
    public List<Map<String,Object>> assets(String repo) {
        return all("assets").stream().filter(a->repo.equals(a.get("repo"))).toList();
    }
    public boolean isHeld(String digest) {
        var hold = get("holds", digest); return hold != null && Boolean.TRUE.equals(hold.get("active"));
    }
    public void assertDownloadAllowed(String digest) {
        if (isHeld(digest)) throw new RepositoryException(423, "CONTENT_HELD", "This content digest is quarantined; contact your repository administrator");
    }
    /** Releases pin immutable blobs even when live paths are overwritten or deleted. */
    public synchronized List<Map<String,Object>> retainedAssets() {
        List<Map<String,Object>> values = new ArrayList<>(all("assets"));
        for (var release : all("releases")) for (Object item : Json.array(release.get("manifest"))) values.add(Json.object(item));
        return values;
    }
    public synchronized Map<String,Object> garbageCollect(boolean dryRun)throws IOException {
        Set<String> referenced=new HashSet<>();
        for(var a:retainedAssets())referenced.add((String)a.get("sha256"));
        long files=0,bytes=0;
        Instant cutoff=Instant.now().minus(Duration.ofHours(24));
        try(var stream=Files.walk(config.home().resolve("blobs"))) {
            for(Path p:stream.filter(Files::isRegularFile).toList()) {
                if(!referenced.contains(p.getFileName().toString())&&Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                    files++;
                    bytes+=Files.size(p);
                    if(!dryRun)Files.delete(p);
                }
            }
        }
        return Json.map("dryRun",dryRun,"files",files,"bytes",bytes,"graceHours",24);
    }
    public static String safePath(String path) {
        if(path==null||path.isEmpty()||path.startsWith("/")||path.length()>4096||path.indexOf('\\')>=0
        ||path.chars().anyMatch(c->c<32||c==127)||path.contains("%"))throw RepositoryException.bad("Invalid artifact path");
        for(String s:path.split("/",-1))if(s.equals(".")||s.equals("..")||s.isEmpty())throw RepositoryException.bad("Invalid artifact path segment");
        return path;
    }
    public static MessageDigest hash(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    public static String digest(String s) {
        return HexFormat.of().formatHex(hash("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
    private static void writeFully(FileChannel ch,ByteBuffer b)throws IOException {
        while(b.hasRemaining())ch.write(b);
    }
    private static void atomicMove(Path from,Path to)throws IOException {
        try {
            Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(AtomicMoveNotSupportedException e) {
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void forceDirectory(Path path) {
        try(var ch=FileChannel.open(path,StandardOpenOption.READ)) {
            ch.force(true);
        } catch(IOException|UnsupportedOperationException ignored) {
        }
    }
    public synchronized void close()throws IOException {
        if(journal!=null)journal.close();
        lock.release();
        lockChannel.close();
    }
}
