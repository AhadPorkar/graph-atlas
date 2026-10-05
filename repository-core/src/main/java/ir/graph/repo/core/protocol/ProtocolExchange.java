package ir.graph.repo.core.protocol;

import java.io.*;
import java.net.URI;

/** Streaming transport port. The application adapter is Jakarta Servlet, owned by Spring MVC. */
public interface ProtocolExchange {
    String method();
    URI uri();
    String header(String name);
    InputStream input() throws IOException;
    OutputStream output() throws IOException;
    ResponseHeaders responseHeaders();
    /** Negative length means no response body; zero means streaming/chunked. */
    void respond(int status, long length) throws IOException;
    interface ResponseHeaders {
        void set(String name, String value);
        void add(String name, String value);
    }
}
