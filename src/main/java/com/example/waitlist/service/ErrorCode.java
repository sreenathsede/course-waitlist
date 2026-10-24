package com.example.waitlist.service;

/**
 * Stable, transport-independent identifiers for every expected failure.
 * The service layer knows nothing about HTTP; {@code web.HttpStatusMapper} maps each code to a status.
 */
public enum ErrorCode {
    INVALID_REQUEST,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    PREREQUISITE_NOT_MET,
    NOT_REGISTERED,
    /**
     * The service lock could not be obtained within the configured timeout; nothing was changed.
     */
    SERVICE_BUSY,
    /**
     * Records endpoint called while no admin key is configured.
     */
    ADMIN_DISABLED,
    /**
     * Records endpoint called with a missing or wrong admin key.
     */
    ADMIN_UNAUTHORIZED
}
