package com.example.waitlist.service;


import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.RepeatedTest;

/**
 * Extra concurrency evidence. Every test builds a FRESH service per repetition.
 * Only the ADAPTER section at the bottom touches your real API: edit that to fit.
 * <p>
 * Assumed setup: capacity 3, prerequisite CS101 (match your test config).
 */
class RegistrationServiceExtraConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int REPS = 50;

    // ---------- 1. Same learner dropped by many threads: exactly one wins ----------
    @RepeatedTest(REPS)
    void repeatedDropOfSameLearnerSucceedsExactlyOnce() throws Exception {
        Object svc = newService();
        grantPrereq(svc, "a");
        enroll(svc, "a");

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger notRegistered = new AtomicInteger();
        runTogether(8, i -> {
            try {
                drop(svc, "a");
                ok.incrementAndGet();
            } catch (RuntimeException e) {
                if (isNotRegistered(e)) notRegistered.incrementAndGet();
                else throw e;
            }
        });

        assertEquals(1, ok.get(), "exactly one drop must succeed");
        assertEquals(7, notRegistered.get(), "the rest must fail with NOT_REGISTERED");
    }

    // ---------- 2. Eligibility restored while a drop frees a seat ----------
    @RepeatedTest(REPS)
    void eligibilityRestoreRacingDropPromotesWaiterExactlyOnce() throws Exception {
        Object svc = newService();
        fillSeatsWithEligibleLearners(svc);          // e1..e3 enrolled
        grantPrereq(svc, "w");
        enroll(svc, "w");                            // w waitlisted (eligible)
        revokePrereq(svc, "w");                      // w now ineligible but retained

        runTogether(2, i -> {
            if (i == 0) drop(svc, "e1");
            else grantPrereq(svc, "w");
        });

        List<String> enrolled = enrolled(svc);
        assertEquals(1, Collections.frequency(enrolled, "w"), "w promoted exactly once");
        assertTrue(enrolled.size() <= CAPACITY);
        assertFalse(waitlist(svc).contains("w"), "w must not be in both lists");
    }

    // ---------- 3. Snapshot never throws or shows an impossible state ----------
    @RepeatedTest(REPS)
    void snapshotDuringMutationsIsAlwaysConsistent() throws Exception {
        Object svc = newService();
        for (int i = 0; i < 20; i++) grantPrereq(svc, "L" + i);

        List<Throwable> problems = Collections.synchronizedList(new ArrayList<>());
        runTogether(12, i -> {
            try {
                if (i < 6) {
                    enroll(svc, "L" + i);
                } else if (i < 9) {
                    try {
                        drop(svc, "L" + (i - 6));
                    } catch (RuntimeException ignore) {
                    }
                } else {
                    for (int k = 0; k < 200; k++) {
                        List<String> en = enrolled(svc);
                        List<String> wl = waitlist(svc);
                        assertTrue(en.size() <= CAPACITY, "capacity exceeded: " + en);
                        assertEquals(en.size(), new HashSet<>(en).size(), "dup in enrolled");
                        assertEquals(wl.size(), new HashSet<>(wl).size(), "dup in waitlist");
                        for (String s : en) assertFalse(wl.contains(s), s + " in both lists");
                    }
                }
            } catch (Throwable t) {
                problems.add(t);
            }
        });
        assertTrue(problems.isEmpty(), "snapshot inconsistency: " + problems);
    }

    // ---------- 4. Many distinct enrols: capacity respected, no duplicates ----------
    @RepeatedTest(REPS)
    void manyConcurrentEnrolsFillExactlyCapacityAndWaitlistRest() throws Exception {
        Object svc = newService();
        int n = 25;
        for (int i = 0; i < n; i++) grantPrereq(svc, "L" + i);

        runTogether(n, i -> enroll(svc, "L" + i));

        List<String> en = enrolled(svc);
        List<String> wl = waitlist(svc);
        assertEquals(CAPACITY, en.size());
        assertEquals(n - CAPACITY, wl.size());
        Set<String> all = new HashSet<>(en);
        all.addAll(wl);
        assertEquals(n, all.size(), "every learner appears exactly once");
    }

    // ---------- 5. Invariant after a mixed storm: no open seat with an eligible waiter ----------
    @RepeatedTest(REPS)
    void afterMixedOperationsNoOpenSeatWhileEligibleWaiterExists() throws Exception {
        Object svc = newService();
        for (int i = 0; i < 12; i++) grantPrereq(svc, "L" + i);

        runTogether(12, i -> {
            enroll(svc, "L" + i);
        });
        runTogether(6, i -> {
            try {
                drop(svc, "L" + i);
            } catch (RuntimeException ignore) {
            }
        });

        List<String> en = enrolled(svc);
        List<String> wl = waitlist(svc);
        boolean eligibleWaiter = wl.stream().anyMatch(id -> isEligible(svc, id));
        if (en.size() < CAPACITY) {
            assertFalse(eligibleWaiter, "open seat while eligible waiter exists: " + en + " / " + wl);
        }
        assertTrue(en.size() <= CAPACITY);
    }

    // =====================================================================
    // Harness
    // =====================================================================

    /**
     * Runs the task on n threads released at the same instant; rethrows the first failure.
     */
    private static void runTogether(int n, ThrowingIntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                task.accept(idx);
                return null;
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS), "threads did not start");
        go.countDown();
        try {
            for (Future<?> f : futures) f.get(10, TimeUnit.SECONDS); // timeout also catches deadlock
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int i) throws Exception;
    }

    private void fillSeatsWithEligibleLearners(Object svc) {
        for (int i = 1; i <= CAPACITY; i++) {
            grantPrereq(svc, "e" + i);
            enroll(svc, "e" + i);
        }
    }

    // =====================================================================
    // ADAPTER: EDIT THIS SECTION to match your real RegistrationService API.
    // =====================================================================

    private Object newService() {
        // TODO e.g. return new RegistrationService(new OfferingConfig("CS201", CAPACITY, "CS101"));
        throw new UnsupportedOperationException("wire to your service");
    }

    private void grantPrereq(Object svc, String id) {
        // TODO e.g. ((RegistrationService) svc).setCompletedCourses(id, Set.of("CS101"));
    }

    private void revokePrereq(Object svc, String id) {
        // TODO e.g. ((RegistrationService) svc).setCompletedCourses(id, Set.of());
    }

    private void enroll(Object svc, String id) {
        // TODO e.g. ((RegistrationService) svc).enroll(id);
    }

    private void drop(Object svc, String id) {
        // TODO e.g. ((RegistrationService) svc).drop(id);
    }

    private List<String> enrolled(Object svc) {
        // TODO e.g. return ((RegistrationService) svc).snapshot().enrolled();
        return List.of();
    }

    private List<String> waitlist(Object svc) {
        // TODO e.g. return ((RegistrationService) svc).snapshot().waitlist();
        return List.of();
    }

    private boolean isEligible(Object svc, String id) {
        // TODO e.g. use your EligibilityPolicy / completed-courses lookup
        return true;
    }

    private boolean isNotRegistered(RuntimeException e) {
        // TODO e.g. return e instanceof NotRegisteredException;
        return false;
    }
}