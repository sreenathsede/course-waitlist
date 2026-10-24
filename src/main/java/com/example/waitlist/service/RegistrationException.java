package com.example.waitlist.service;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, business-level failures. Each carries a stable machine-readable
 * {@code code} and the HTTP status it maps to, so the web layer needs just one handler.
 * Thrown BEFORE any state is changed, which is how invalid requests never corrupt state.
 */
public abstract class RegistrationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    protected RegistrationException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    /**
     * Malformed input such as a blank or illegal learner id (400).
     */
    public static class InvalidRequest extends RegistrationException {
        public InvalidRequest(String message) {
            super("INVALID_REQUEST", HttpStatus.BAD_REQUEST, message);
        }
    }

    /**
     * Learner already holds a seat (409).
     */
    public static class AlreadyEnrolled extends RegistrationException {
        public AlreadyEnrolled(String id) {
            super("ALREADY_ENROLLED", HttpStatus.CONFLICT, "Learner '" + id + "' is already enrolled");
        }
    }

    /**
     * Learner is already queued; no second waitlist record is created (409).
     */
    public static class AlreadyWaitlisted extends RegistrationException {
        public AlreadyWaitlisted(String id, int position) {
            super("ALREADY_WAITLISTED", HttpStatus.CONFLICT,
                    "Learner '" + id + "' is already on the waitlist at position " + position);
        }
    }

    /**
     * Prerequisite not satisfied; the learner is neither enrolled nor queued (422).
     */
    public static class PrerequisiteNotMet extends RegistrationException {
        public PrerequisiteNotMet(String id, String prerequisite) {
            super("PREREQUISITE_NOT_MET", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Learner '" + id + "' has not completed prerequisite " + prerequisite);
        }
    }

    /**
     * Drop for a learner that is neither enrolled nor waitlisted, including a repeated drop (404).
     */
    public static class NotRegistered extends RegistrationException {
        public NotRegistered(String id) {
            super("NOT_REGISTERED", HttpStatus.NOT_FOUND,
                    "Learner '" + id + "' is not enrolled or waitlisted (nothing to drop)");
        }
    }
}
