package com.example.waitlist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.domain.DropResult;
import com.example.waitlist.domain.DropResult.DroppedFrom;
import com.example.waitlist.domain.EnrollmentOutcome;
import com.example.waitlist.domain.EnrollmentResult;
import com.example.waitlist.domain.OfferingSnapshot;
import com.example.waitlist.repository.RegistrationRepository;
import com.example.waitlist.service.RegistrationException.*;

/**
 * Core business logic: enroll, drop, FIFO waitlist promotion.
 *
 * <h3>Invariants (hold whenever the lock is free)</h3>
 * <ol>
 *   <li>0 &le; enrolled &le; capacity.</li>
 *   <li>A learner is in at most one of {enrolled, waitlist}, and at most once in each.</li>
 *   <li>Only eligible learners ever enter the waitlist or enrollment.</li>
 *   <li>No open seat coexists with an eligible waitlisted learner (seats are refilled immediately).</li>
 * </ol>
 *
 * <h3>Waitlist policy: SKIP AND RETAIN</h3>
 * Promotion scans the waitlist front-to-back and promotes the earliest <em>eligible</em> learner.
 * A waitlisted learner who is no longer eligible (prerequisite record changed after joining) is
 * <b>skipped but keeps their place</b>; they are neither promoted nor removed. If their eligibility
 * is restored they are promoted automatically (see {@link #updateCompletedCourses}) and, being earlier
 * in the queue, are served before later joiners.
 *
 * <h3>Concurrency</h3>
 * One {@link ReentrantLock} serialises every operation; waiting is bounded by a timeout (see {@link #locked}). The offering is a single small aggregate, so a
 * coarse lock is the simplest approach that makes "check capacity then add" atomic.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    /**
     * Letters, digits, dot, underscore, hyphen; keeps ids safe for logs and URLs.
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final OfferingProperties offering;
    private final EligibilityPolicy policy;
    private final RegistrationRepository repo;
    private final ReentrantLock lock = new ReentrantLock(true); // fair: waiting requests run in arrival order

    public RegistrationService(OfferingProperties offering, EligibilityPolicy policy, RegistrationRepository repo) {
        this.offering = offering;
        this.policy = policy;
        this.repo = repo;
    }

    /**
     * Enrol a learner, or waitlist them when the offering is full.
     * Order of checks: input -> duplicates -> eligibility -> capacity. Every failure is raised before any
     * mutation, so a rejected request leaves state untouched.
     */
    public EnrollmentResult enroll(String rawLearnerId) {
        String learnerId = normaliseId(rawLearnerId);
        return locked(() -> {
            if (repo.isEnrolled(learnerId)) throw new AlreadyEnrolled(learnerId);
            int existing = repo.waitlistPosition(learnerId);
            if (existing > 0) throw new AlreadyWaitlisted(learnerId, existing);
            if (!policy.isEligible(repo.completedCourses(learnerId)))
                throw new PrerequisiteNotMet(learnerId, policy.prerequisite());

            if (repo.enrolledCount() < offering.capacity()) {
                repo.addEnrolled(learnerId);
                log.info("Enrolled {}", learnerId);
                return new EnrollmentResult(learnerId, EnrollmentOutcome.ENROLLED, 0);
            }
            repo.addToWaitlist(learnerId);
            int pos = repo.waitlistPosition(learnerId);
            log.info("Offering full; waitlisted {} at position {}", learnerId, pos);
            return new EnrollmentResult(learnerId, EnrollmentOutcome.WAITLISTED, pos);
        });
    }

    /**
     * Drop an enrolled learner (freeing a seat and promoting the earliest eligible waiter) or remove a
     * waitlisted learner from the queue (no seat changes). A second drop of the same learner, or a drop of
     * someone unknown, throws {@link NotRegistered} and changes nothing, so a repeated drop can never
     * free two seats.
     */
    public DropResult drop(String rawLearnerId) {
        String learnerId = normaliseId(rawLearnerId);
        return locked(() -> {
            if (repo.removeEnrolled(learnerId)) {
                List<String> promoted = fillOpenSeats();
                log.info("Dropped {}; promoted {}", learnerId, promoted);
                return new DropResult(learnerId, DroppedFrom.ENROLLMENT, promoted);
            }
            if (repo.removeFromWaitlist(learnerId)) {
                log.info("Removed {} from waitlist", learnerId);
                return new DropResult(learnerId, DroppedFrom.WAITLIST, List.of());
            }
            throw new NotRegistered(learnerId);
        });
    }

    /**
     * Replace a learner's completed-course record (stand-in for an external records system, which is out of
     * scope). Because this can make a waitlisted learner eligible while a seat is open (all earlier waiters
     * having been ineligible), it re-runs promotion to preserve invariant 4.
     *
     * @return learners promoted as a result (usually empty)
     */
    public List<String> updateCompletedCourses(String rawLearnerId, Set<String> courses) {
        String learnerId = normaliseId(rawLearnerId);
        Set<String> normalised = courses.stream()
                .map(c -> {
                    if (c == null || c.isBlank()) throw new InvalidRequest("Course codes must not be blank");
                    return c.trim().toUpperCase(Locale.ROOT);
                })
                .collect(Collectors.toUnmodifiableSet());
        return locked(() -> {
            repo.setCompletedCourses(learnerId, normalised);
            return fillOpenSeats();
        });
    }

    /**
     * Consistent view of current state.
     */
    public OfferingSnapshot snapshot() {
        return locked(() -> new OfferingSnapshot(
                offering.code(), offering.capacity(),
                offering.capacity() - repo.enrolledCount(),
                policy.prerequisite(), repo.enrolledInOrder(), repo.waitlistInOrder()));
    }

    /**
     * Fill every open seat from the waitlist. Caller MUST hold the lock.
     * Scans a copy of the queue in FIFO order, promoting eligible learners and skipping (but keeping)
     * ineligible ones, until seats run out or the queue is exhausted.
     */
    private List<String> fillOpenSeats() {
        List<String> promoted = new ArrayList<>();
        for (String candidate : repo.waitlistInOrder()) {
            if (repo.enrolledCount() >= offering.capacity()) break;
            if (policy.isEligible(repo.completedCourses(candidate))) {
                repo.removeFromWaitlist(candidate);
                repo.addEnrolled(candidate);
                promoted.add(candidate);
            } else {
                log.info("Skipping ineligible waitlisted learner {} (keeps place)", candidate);
            }
        }
        return promoted;
    }

    private String normaliseId(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidRequest("learnerId must not be blank");
        String learnerId = raw.trim();
        if (!ID_PATTERN.matcher(learnerId).matches()) {
            throw new InvalidRequest("learnerId must be 1-64 characters: letters, digits, '.', '_' or '-'");
        }
        return learnerId;
    }

    /**
     * Runs {@code action} while holding the service lock, waiting at most {@code offering.lock-timeout-millis}.
     * If the lock cannot be obtained (or the thread is interrupted while waiting) the action is NOT run and
     * {@link ServiceBusy} is thrown, so a timed-out request provably changes nothing and is safe to retry.
     */
    private <T> T locked(Supplier<T> action) {
        boolean acquired;
        try {
            acquired = lock.tryLock(offering.lockTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve the interrupt for callers
            throw new ServiceBusy();
        }
        if (!acquired) {
            log.warn("Timed out after {} ms waiting for the service lock", offering.lockTimeoutMillis());
            throw new ServiceBusy();
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
