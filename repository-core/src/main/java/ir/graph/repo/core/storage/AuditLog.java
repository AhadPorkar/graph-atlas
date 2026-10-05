package ir.graph.repo.core.storage;

import ir.graph.repo.core.util.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
public final class AuditLog {
    private final Path file;
    public AuditLog(Path home) {
        file=home.resolve("audit.jsonl");
    }
    public synchronized void record(String request,String user,String method,String path,int status) {
        try {
            Files.writeString(file,Json.stringify(Json.map("time",Instant.now().toString(),"requestId",request,"user",user,"method",method,"path",path,"status",status))+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
        catch(IOException e) {
            System.err.println("AUDIT_WRITE_FAILED request="+request);
        }
    }
    public synchronized List<Object> tail(int count)throws IOException {
        ArrayDeque<Object> result=new ArrayDeque<>();
        if(!Files.exists(file)||count==0)return List.of();
        try(BufferedReader in=Files.newBufferedReader(file)) {
            String line;
            while((line=in.readLine())!=null) {
                try {
                    result.addLast(Json.parse(line));
                    if(result.size()>count)result.removeFirst();
                } catch(IllegalArgumentException ignored) {
                }
            }
        }
        List<Object> list=new ArrayList<>(result);
        Collections.reverse(list);
        return list;
    }
}
