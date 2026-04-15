package zac.demo.api.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handling for all @RestController beans.
 *
 *   - 404 (no handler / no static resource) -> rendered HTML page
 *     styled with Tailwind (loaded from /tailwinds.css runtime JIT).
 *   - Bean-validation failures -> JSON with per-field errors.
 *   - All other errors -> consistent JSON envelope.
 */
@Slf4j
@ControllerAdvice
public class GlobalControllerAdvice {

    // ---------------------------------------------------------------------
    // 404 -> HTML page
    // ---------------------------------------------------------------------

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<String> handleNotFound(Exception ex) {
        log.warn("404 not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .body(notFoundHtml());
    }

    private String notFoundHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>404 — Page Not Found</title>
                    <script src="/tailwinds.css"></script>
                </head>
                <body class="min-h-screen bg-gradient-to-br from-slate-50 to-slate-200 flex items-center justify-center px-6 font-sans">
                    <div class="max-w-xl w-full text-center">
                        <p class="text-9xl font-extrabold text-slate-300 tracking-tight select-none">404</p>
                        <h1 class="mt-4 text-3xl font-bold text-slate-800 sm:text-4xl">
                            Page not found
                        </h1>
                        <p class="mt-4 text-base text-slate-600">
                            Sorry, we couldn't find the page you were looking for.
                            It may have been moved, renamed, or never existed.
                        </p>
                        <div class="mt-8 flex flex-wrap items-center justify-center gap-4">
                            <a href="/"
                               class="inline-flex items-center rounded-md bg-blue-600 px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2 transition">
                                Go home
                            </a>
                            <a href="/swagger-ui.html"
                               class="text-sm font-medium text-slate-700 hover:text-slate-900 underline underline-offset-4">
                                View API docs &rarr;
                            </a>
                        </div>
                    </div>
                </body>
                </html>
                """;
    }

    // ---------------------------------------------------------------------
    // JSON error handlers
    // ---------------------------------------------------------------------

    /**
     * Triggered when @Valid on a controller argument fails.
     * Returns a 400 with a per-field map of error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);

        Map<String, Object> body = baseEnvelope(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(baseEnvelope(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(baseEnvelope(HttpStatus.BAD_REQUEST, "Malformed JSON request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(baseEnvelope(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error"));
    }

    private Map<String, Object> baseEnvelope(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
  