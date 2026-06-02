package com.cryptocopilot.web;

import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns uncaught exceptions from the {@code /api/**} controllers into a clean JSON
 * {@link ApiError} ({@code {error, message, status}}) instead of a Spring/Tomcat stack-trace
 * page (Stage 7 hardening). Expected client errors map to 4xx; everything unexpected is logged
 * server-side and surfaced to the client as a generic 500 (we never leak a stack trace).
 *
 * <p>This advice only intercepts handler methods on {@code @RestController}s — Swagger UI,
 * the OpenAPI docs and the actuator endpoints are unaffected.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bad client input: an unknown symbol/timeframe, an empty series, a malformed param. → 400. */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** A missing/unparseable request body. → 400, without echoing the parser internals. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Malformed or missing request body.");
    }

    /** Nothing to return yet (e.g. the account before its first reset). → 404. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Resource not found.";
        return body(HttpStatus.NOT_FOUND, msg);
    }

    /** Preserve a status a controller (or framework) chose deliberately. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> statusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String msg = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return body(status, msg);
    }

    /**
     * Anything unexpected (e.g. the RAG retriever failing because the local Ollama is down) →
     * 500 with a clean, generic message. The full stack trace goes to the server log only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled exception serving an /api request", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. If this involved chat or the Analyst summary, "
                        + "the local LLM (Ollama) may be unavailable — the deterministic paths "
                        + "still work. See the server log for details.");
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String message) {
        String detail = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status)
                .body(new ApiError(status.getReasonPhrase(), detail, status.value()));
    }
}
