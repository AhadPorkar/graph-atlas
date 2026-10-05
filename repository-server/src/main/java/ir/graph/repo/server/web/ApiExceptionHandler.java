package ir.graph.repo.server.web;

import ir.graph.repo.core.domain.RepositoryException;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.io.IOException;

@RestControllerAdvice
public final class ApiExceptionHandler {
    private final ErrorResponder errors;
    public ApiExceptionHandler(ErrorResponder errors) { this.errors = errors; }
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(RepositoryException.class)
    public void repository(RepositoryException e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        errors.write(req, res, e.status, e.code, e.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public void invalid(Exception e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        errors.write(req, res, 400, "BAD_REQUEST", e.getMessage());
    }
    @ExceptionHandler(AuthenticationException.class)
    public void authentication(AuthenticationException e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        errors.write(req, res, 401, "UNAUTHORIZED", "Invalid credentials");
    }
    @ExceptionHandler(AccessDeniedException.class)
    public void forbidden(AccessDeniedException e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        errors.write(req, res, 403, "FORBIDDEN", "Permission denied");
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void method(HttpRequestMethodNotSupportedException e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (e.getSupportedMethods() != null) res.setHeader("Allow", String.join(", ", e.getSupportedMethods()));
        errors.write(req, res, 405, "METHOD_NOT_ALLOWED", "HTTP method is not supported");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public void missing(NoResourceFoundException e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        errors.write(req, res, 404, "NOT_FOUND", "Resource not found");
    }
    @ExceptionHandler(Exception.class)
    public void unexpected(Exception e, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt(); errors.write(req, res, 503, "INTERRUPTED", "Operation interrupted"); return;
        }
        LOG.error("Repository request failed requestId={}", req.getAttribute(ErrorResponder.REQUEST_ID), e);
        errors.write(req, res, 500, "INTERNAL", "Internal operation failed; inspect the server log using the request ID");
    }
}
