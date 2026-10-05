package ir.graph.repo.server.web;

import ir.graph.repo.core.i18n.SupportedLanguages;
import ir.graph.repo.core.protocol.ProtocolIO;
import ir.graph.repo.core.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One error contract for MVC and security filters.
 * Administrative messages follow Accept-Language; package protocols retain English diagnostics
 * and unchanged machine-readable fields. Technical detail is intentionally language-neutral.
 */
@Component
public final class ErrorResponder {
    public static final String REQUEST_ID = "graph.requestId";
    public static final String ACTOR = "graph.actor";
    private final MessageSource messages;

    public ErrorResponder(MessageSource messages) {
        this.messages = messages;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status,
                      String code, String detail) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        String path = request.getRequestURI();
        boolean oci = path.equals("/v2") || path.startsWith("/v2/");
        boolean protocol = oci || path.startsWith("/repository/");
        Locale locale = protocol ? SupportedLanguages.DEFAULT :
                SupportedLanguages.resolve(request.getHeader("Accept-Language"));
        String generic = messages.getMessage("error.generic", null, "The operation could not be completed.", locale);
        String fallback = messages.getMessage("http." + status, null, generic, locale);
        String message = messages.getMessage("error." + code, null, fallback, locale);
        String id = Objects.toString(request.getAttribute(REQUEST_ID), "");

        response.setHeader("Content-Language", locale.toLanguageTag());
        response.addHeader("Vary", "Accept-Language");
        if (status == 401 && protocol) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"GraphAtlas\", charset=\"UTF-8\"");
        }
        Object body;
        if (oci) {
            response.setHeader("Docker-Distribution-Api-Version", "registry/2.0");
            body = Json.map("errors", List.of(Json.map("code", code, "message", detail == null ? message : detail,
                    "detail", Json.map("requestId", id))));
        } else {
            body = Json.map("error", code, "message", message, "detail", detail == null ? "" : detail, "requestId", id);
        }
        ProtocolIO.json(new ServletProtocolExchange(request, response), status, body);
    }
}
