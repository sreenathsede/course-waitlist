package com.example.waitlist.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

/**
 * In-memory store for the single offering (explicitly allowed by the assessment when no starter exists).
 *
 * <p><b>Not thread-safe by design:</b> every call must happen while holding the lock owned by
 * {@code RegistrationService}. Keeping synchronisation in one place avoids check-then-act races
 * spanning several repository calls (e.g. "count enrolled, then add").
 *
 * <p>{@link LinkedHashSet} gives us O(1) membership tests <em>and</em> insertion order, which is
 * exactly the FIFO order required for the waitlist; a learner can never appear twice.
 */
@Repository
public class RegistrationRepository {

    private final Set<String> enrolled = new LinkedHashSet<>();
    private final Set<String> waitlist = new LinkedHashSet<>();
    /**
     * learnerId -> completed course codes (upper-case). Absent means "nothing completed".
     */
    private final Map<String, Set<String>> completedCourses = new HashMap<>();

    public boolean isEnrolled(String learnerId) {
        return enrolled.contains(learnerId);
    }

    public boolean isWaitlisted(String learnerId) {
        return waitlist.contains(learnerId);
    }

    public int enrolledCount() {
        return enrolled.size();
    }

    public void addEnrolled(String learnerId) {
        enrolled.add(learnerId);
    }

    public boolean removeEnrolled(String learnerId) {
        return enrolled.remove(learnerId);
    }

    public void addToWaitlist(String learnerId) {
        waitlist.add(learnerId);
    }

    public boolean removeFromWaitlist(String learnerId) {
        return waitlist.remove(learnerId);
    }

    /**
     * @return 1-based position in the waitlist, or 0 when not waitlisted
     */
    public int waitlistPosition(String learnerId) {
        int pos = 1;
        for (String id : waitlist) {
            if (id.equals(learnerId)) return pos;
            pos++;
        }
        return 0;
    }

    /**
     * @return defensive copy of the waitlist in FIFO order (safe to iterate while mutating the store)
     */
    public List<String> waitlistInOrder() {
        return new ArrayList<>(waitlist);
    }

    public List<String> enrolledInOrder() {
        return new ArrayList<>(enrolled);
    }

    public Set<String> completedCourses(String learnerId) {
        return Set.copyOf(completedCourses.getOrDefault(learnerId, Set.of()));
    }

    public void setCompletedCourses(String learnerId, Set<String> courses) {
        completedCourses.put(learnerId, Set.copyOf(courses));
    }
}
