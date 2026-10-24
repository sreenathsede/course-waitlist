package com.example.waitlist.domain;

/**
 * @param learnerId normalised learner id
 * @param outcome   ENROLLED or WAITLISTED
 * @param position  1-based waitlist position when WAITLISTED, otherwise 0
 */
public record EnrollmentResult(String learnerId, EnrollmentOutcome outcome, int position) {
}
