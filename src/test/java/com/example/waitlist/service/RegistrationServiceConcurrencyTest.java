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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

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
        OfferingProperties props = new OfferingProperties("CS201", CAPACITY, "CS101");
        return new RegistrationService(props, new EligibilityPolicy(props), new RegistrationRepository());
    }

    /** Runs all tasks simultaneously on a thread pool and waits for completion. */
    private void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable t : tasks) {
            futures.add(pool.submit(() -> { start.await(); t.run(); return null; }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    @Test
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

    @Test
    void sameLearnerRacingWithItselfSucceedsExactlyOnce() throws Exception {
        RegistrationService service = newService();
        service.updateCompletedCourses("same", Set.of("CS101"));
        AtomicInteger successes = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(() -> {
                try { service.enroll("same"); successes.incrementAndGet(); }
                catch (RegistrationException.AlreadyEnrolled expected) { /* losers get a clean 409-style failure */ }
            });
        }
        runConcurrently(tasks);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(service.snapshot().enrolled()).containsExactly("same");
    }

    @Test
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
                    try { service.drop(id); successfulDrops.incrementAndGet(); }
                    catch (RegistrationException.NotRegistered expected) { /* repeated drop */ }
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
}
