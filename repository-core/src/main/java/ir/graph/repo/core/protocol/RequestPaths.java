package ir.graph.repo.core.protocol;

import ir.graph.repo.core.domain.RepositoryException;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.charset.*;
import java.util.Locale;

/** Decode exactly once, preserve '+', and reject ambiguous/traversing paths before routing. */
public final class RequestPaths {
    private RequestPaths() { }
    public static String decode(String raw) {
        if (raw == null || raw.length() > 8192 || !raw.startsWith("/")) throw RepositoryException.bad("Invalid request path");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length()) throw RepositoryException.bad("Invalid percent escape");
                int a = Character.digit(raw.charAt(++i), 16), b = Character.digit(raw.charAt(++i), 16);
                if (a < 0 || b < 0) throw RepositoryException.bad("Invalid percent escape");
                bytes.write((a << 4) | b);
            } else {
                if (c > 127) throw RepositoryException.bad("URI paths must percent-encode non-ASCII text");
                bytes.write(c);
            }
        }
        String path;
        try { path = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString(); }
        catch (CharacterCodingException e) { throw RepositoryException.bad("Invalid UTF-8 path"); }
        if (path.indexOf('%') >= 0 || path.indexOf('\\') >= 0 || path.indexOf(';') >= 0 || path.contains("//")
                || path.chars().anyMatch(c -> c < 32 || c == 127)) throw RepositoryException.bad("Ambiguous request path");
        for (String segment : path.split("/", -1))
            if (segment.equals(".") || segment.equals("..")) throw RepositoryException.bad("Traversal is not allowed");
        if (raw.toLowerCase(Locale.ROOT).contains("%2f")) {
            if (!raw.startsWith("/repository/")) throw RepositoryException.bad("Encoded slash is only supported in package paths");
            String rest = raw.substring("/repository/".length());
            int slash = rest.indexOf('/');
            if (slash < 0 || rest.substring(0, slash).contains("%")) throw RepositoryException.bad("Invalid repository segment");
        }
        return path;
    }
}
