package com.example.waitlist.domain;

/** Result of a successful enrollment request. */
public enum EnrollmentOutcome {
    /** A seat was available; the learner now holds it. */
    ENROLLED,
    /** The offering was full; the learner was appended to the FIFO waitlist. */
    WAITLISTED
}
