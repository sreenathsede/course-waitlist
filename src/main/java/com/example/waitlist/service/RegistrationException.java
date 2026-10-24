package com.example.waitlist.service;

/**
 * Base class for expected, business-level failures. Carries only an {@link ErrorCode}; mapping to a transport
 * status (HTTP) is the web layer's job. Thrown BEFORE any state is changed, so rejected requests never corrupt state.
 */
public abstract class RegistrationException extends RuntimeException {
    private final ErrorCode errorCode;

    protected RegistrationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Machine-readable code used in API error bodies.
     */
    public String code() {
        return errorCode.name();
    }

    /**
     * Malformed input such as a blank or illegal learner id.
     */
    public static class InvalidRequest extends RegistrationException {
        public InvalidRequest(String message) {
            super(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /**
     * Learner already holds a seat.
     */
    public static class AlreadyEnrolled extends RegistrationException {
        public AlreadyEnrolled(String id) {
            super(ErrorCode.ALREADY_ENROLLED, "Learner '" + id + "' is already enrolled");
        }
    }

    /**
     * Learner is already queued; no second waitlist record is created.
     */
    public static class AlreadyWaitlisted extends RegistrationException {
        public AlreadyWaitlisted(String id, int position) {
            super(ErrorCode.ALREADY_WAITLISTED, "Learner '" + id + "' is already on the waitlist at position " + position);
        }
    }

    /**
     * Prerequisite not satisfied; the learner is neither enrolled nor queued.
     */
    public static class PrerequisiteNotMet extends RegistrationException {
        public PrerequisiteNotMet(String id, String prerequisite) {
            super(ErrorCode.PREREQUISITE_NOT_MET, "Learner '" + id + "' has not completed prerequisite " + prerequisite);
        }
    }

    /**
     * Drop for a learner that is neither enrolled nor waitlisted, including a repeated drop.
     */
    public static class NotRegistered extends RegistrationException {
        public NotRegistered(String id) {
            super(ErrorCode.NOT_REGISTERED, "Learner '" + id + "' is not enrolled or waitlisted (nothing to drop)");
        }
    }

    /**
     * The request waited longer than the lock timeout (or was interrupted). Safe to retry: nothing changed.
     */
    public static class ServiceBusy extends RegistrationException {
        public ServiceBusy() {
            super(ErrorCode.SERVICE_BUSY, "The service is busy; no change was made, please retry");
        }
    }

    /**
     * The admin (records) endpoint is switched off because no admin key is configured.
     */
    public static class AdminDisabled extends RegistrationException {
        public AdminDisabled() {
            super(ErrorCode.ADMIN_DISABLED, "Records endpoint is disabled: no admin key is configured");
        }
    }

    /**
     * Missing or incorrect admin key.
     */
    public static class AdminUnauthorized extends RegistrationException {
        public AdminUnauthorized() {
            super(ErrorCode.ADMIN_UNAUTHORIZED, "Missing or invalid X-Admin-Key");
        }
    }
}
