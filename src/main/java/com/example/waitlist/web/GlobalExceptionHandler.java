package com.example.waitlist.web;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.waitlist.service.RegistrationException;
import com.example.waitlist.web.Dtos.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Centralised mapping of failures to a uniform {@link ErrorResponse}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Business failures: the code comes from the exception, the HTTP status from {@link HttpStatusMapper}.
     */
    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ErrorResponse> business(RegistrationException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatusMapper.statusFor(ex.errorCode());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) response.header("Retry-After", "1");
        return response.body(new ErrorResponse(Instant.now(), status.value(), ex.code(), ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Bean Validation failures on request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", msg, req);
    }

    /**
     * Missing/malformed JSON body.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is missing or malformed JSON", req);
    }

    /**
     * Last resort. Framework errors that already carry an HTTP status (405 wrong method, 404 unknown URL,
     * 415 wrong media type...) keep that status; anything else is a 500 with details logged, not returned.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return build(status, "HTTP_ERROR", status.getReasonPhrase(), req);
        }
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String msg, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), code, msg, req.getRequestURI()));
    }
}
