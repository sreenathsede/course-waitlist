package com.example.waitlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.RepeatedTest;

import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.repository.RegistrationRepository;

/**
 * Focused concurrency checks (AC6). Threads are released together via a latch to maximise contention.
 * Without the service lock these tests would intermittently over-enrol or duplicate waitlist entries.
 */
class RegistrationServiceConcurrencyTest {

    private static final int CAPACITY = 5;
    private static final int LEARNERS = 60;

    private RegistrationService newService() {
        OfferingProperties props = new OfferingProperties("CS201", CAPACITY, "CS101", 2000L);
        return new RegistrationService(props, new EligibilityPolicy(props), new RegistrationRepository());
    }

    /**
     * Runs all tasks simultaneously on a thread pool and waits for completion.
     */
    private void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable t : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                t.run();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    @RepeatedTest(25)
    void manyConcurrentEnrollmentsNeverExceedCapacityOrDuplicate() throws Exception {
        RegistrationService service = newService();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < LEARNERS; i++) {
            String id = "learner-" + i;
            service.updateCompletedCourses(id, Set.of("CS101"));
            tasks.add(() -> service.enroll(id));
        }
        runConcurrently(tasks);

        var snap = service.snapshot();
        assertThat(snap.enrolled()).hasSize(CAPACITY).doesNotHaveDuplicates();
        assertThat(snap.waitlist()).hasSize(LEARNERS - CAPACITY).doesNotHaveDuplicates();
        assertThat(snap.enrolled()).doesNotContainAnyElementsOf(snap.waitlist());
    }

    @RepeatedTest(25)
    void sameLearnerRacingWithItselfSucceedsExactlyOnce() throws Exception {
        RegistrationService service = newService();
        service.updateCompletedCourses("same", Set.of("CS101"));
        AtomicInteger successes = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(() -> {
                try {
                    service.enroll("same");
                    successes.incrementAndGet();
                } catch (RegistrationException.AlreadyEnrolled expected) { /* losers get a clean 409-style failure */ }
            });
        }
        runConcurrently(tasks);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(service.snapshot().enrolled()).containsExactly("same");
    }

    @RepeatedTest(25)
    void concurrentDropsPromoteEachWaiterExactlyOnceInFifoOrder() throws Exception {
        RegistrationService service = newService();
        for (int i = 0; i < LEARNERS; i++) {
            service.updateCompletedCourses("learner-" + i, Set.of("CS101"));
            service.enroll("learner-" + i);   // sequential: first 5 enrolled, rest waitlisted in order
        }
        List<String> seated = service.snapshot().enrolled();
        List<Runnable> tasks = new ArrayList<>();
        // every seated learner is dropped by 3 racing threads; only the first drop of each may succeed
        AtomicInteger successfulDrops = new AtomicInteger();
        for (String id : seated) {
            for (int k = 0; k < 3; k++) {
                tasks.add(() -> {
                    try {
                        service.drop(id);
                        successfulDrops.incrementAndGet();
                    } catch (RegistrationException.NotRegistered expected) { /* repeated drop */ }
                });
            }
        }
        runConcurrently(tasks);

        assertThat(successfulDrops.get()).isEqualTo(CAPACITY);
        var snap = service.snapshot();
        // learners 5..9 (the first five waiters) are now seated; nobody was promoted twice or skipped
        assertThat(snap.enrolled()).containsExactlyInAnyOrder("learner-5", "learner-6", "learner-7", "learner-8", "learner-9");
        assertThat(snap.waitlist()).hasSize(LEARNERS - 2 * CAPACITY);
        assertThat(snap.waitlist().get(0)).isEqualTo("learner-10");
    }

    /**
     * Mixed operations: enrol, drop and records updates (grant / revoke the prerequisite) race from 8 threads while a
     * watcher thread repeatedly snapshots the offering. Every snapshot (taken under the lock) must satisfy the
     * invariants; after everyone is re-qualified, no seat may be open beside a waiter. Seeds are fixed per thread so
     * a failure can be re-run, though thread interleaving itself is not deterministic.
     */
    @RepeatedTest(20)
    void mixedEnrolDropAndRecordUpdatesPreserveInvariants() throws Exception {
        RegistrationService service = newService();
        int learners = 30;
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<String> violation = new AtomicReference<>();

        Thread watcher = new Thread(() -> {
            while (!done.get()) {
                String v = invariantViolation(service.snapshot());
                if (v != null) {
                    violation.compareAndSet(null, v);
                    return;
                }
            }
        });
        watcher.start();

        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            final long seed = t * 31L + 7;
            tasks.add(() -> {
                Random r = new Random(seed);
                for (int i = 0; i < 1500; i++) {
                    String id = "L" + r.nextInt(learners);
                    try {
                        switch (r.nextInt(10)) {
                            case 0, 1, 2, 3, 4 -> service.enroll(id);
                            case 5, 6, 7 -> service.drop(id);
                            case 8 -> service.updateCompletedCourses(id, Set.of("CS101"));
                            default -> service.updateCompletedCourses(id, Set.of());
                        }
                    } catch (RegistrationException expectedBusinessRejection) {
                        // duplicates, missing prerequisite, nothing-to-drop are normal outcomes here
                    }
                }
            });
        }
        runConcurrently(tasks);
        done.set(true);
        watcher.join(5000);
        assertThat(violation.get()).as("invariant violated during the run").isNull();

        for (int i = 0; i < learners; i++) service.updateCompletedCourses("L" + i, Set.of("CS101"));
        var snap = service.snapshot();
        assertThat(invariantViolation(snap)).isNull();
        if (!snap.waitlist().isEmpty()) {
            assertThat(snap.seatsAvailable()).as("open seat beside an eligible waiter").isZero();
        }
    }

    /**
     * @return a description of the first broken invariant in the snapshot, or null when it is consistent
     */
    private static String invariantViolation(com.example.waitlist.domain.OfferingSnapshot snap) {
        if (snap.enrolled().size() > CAPACITY) return "over capacity: " + snap.enrolled();
        if (snap.seatsAvailable() != CAPACITY - snap.enrolled().size()) return "seatsAvailable inconsistent";
        if (new java.util.HashSet<>(snap.enrolled()).size() != snap.enrolled().size()) return "duplicate enrolled";
        if (new java.util.HashSet<>(snap.waitlist()).size() != snap.waitlist().size()) return "duplicate waitlisted";
        if (!java.util.Collections.disjoint(snap.enrolled(), snap.waitlist()))
            return "learner both enrolled and waitlisted";
        return null;
    }
}
