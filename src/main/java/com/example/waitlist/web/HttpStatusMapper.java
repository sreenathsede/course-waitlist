package com.example.waitlist.web;

import org.springframework.http.HttpStatus;

import com.example.waitlist.service.ErrorCode;

/**
 * The ONLY place that knows which HTTP status each business failure maps to. The switch has no default branch, so
 * adding an {@link ErrorCode} without deciding its status is a compile error.
 */
public final class HttpStatusMapper {
    private HttpStatusMapper() {
    }

    public static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case ALREADY_ENROLLED, ALREADY_WAITLISTED -> HttpStatus.CONFLICT;
            case PREREQUISITE_NOT_MET -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_REGISTERED -> HttpStatus.NOT_FOUND;
            case SERVICE_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case ADMIN_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ADMIN_DISABLED -> HttpStatus.FORBIDDEN;
        };
    }
}
