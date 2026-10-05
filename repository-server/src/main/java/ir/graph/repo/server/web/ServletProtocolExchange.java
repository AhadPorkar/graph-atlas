package ir.graph.repo.server.web;

import ir.graph.repo.core.protocol.ProtocolExchange;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;

/** No secondary listener/server: all I/O is owned by the Spring MVC servlet request. */
public final class ServletProtocolExchange implements ProtocolExchange {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    public ServletProtocolExchange(HttpServletRequest request, HttpServletResponse response) {
        this.request = request; this.response = response;
    }
    @Override public String method() { return request.getMethod(); }
    @Override public URI uri() {
        String query = request.getQueryString();
        return URI.create(request.getRequestURI() + (query == null ? "" : "?" + query));
    }
    @Override public String header(String name) { return request.getHeader(name); }
    @Override public InputStream input() throws IOException { return request.getInputStream(); }
    @Override public OutputStream output() throws IOException { return response.getOutputStream(); }
    @Override public ResponseHeaders responseHeaders() {
        return new ResponseHeaders() {
            public void set(String name, String value) { response.setHeader(name, value); }
            public void add(String name, String value) { response.addHeader(name, value); }
        };
    }
    @Override public void respond(int status, long length) {
        response.setStatus(status);
        if (length > 0) response.setContentLengthLong(length);
        else if (length < 0 && !method().equals("HEAD") && status != 204 && status != 304
                && !response.containsHeader("Content-Length")) response.setContentLengthLong(0);
    }
}
