package com.example.waitlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.repository.RegistrationRepository;

/**
 * Timeout behaviour: a request that cannot get the lock in time fails cleanly and changes nothing.
 */
class RegistrationServiceTimeoutTest {

    /**
     * Repository whose isEnrolled() parks the calling thread (while it holds the service lock) until released.
     */
    static class BlockingRepository extends RegistrationRepository {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean block;

        @Override
        public boolean isEnrolled(String learnerId) {
            if (block) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.isEnrolled(learnerId);
        }
    }

    @Test
    void requestWaitingLongerThanLockTimeoutFailsWithServiceBusyAndChangesNothing() throws Exception {
        OfferingProperties props = new OfferingProperties("OPEN1", 5, "", 150L);
        BlockingRepository repo = new BlockingRepository();
        RegistrationService service = new RegistrationService(props, new EligibilityPolicy(props), repo);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            repo.block = true;
            Future<?> holder = pool.submit(() -> service.enroll("holder")); // takes the lock, then parks
            assertThat(repo.entered.await(5, TimeUnit.SECONDS)).isTrue();
            repo.block = false;                                              // later calls must not park

            assertThatThrownBy(() -> service.enroll("late")).isInstanceOfSatisfying(RegistrationException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICE_BUSY));
            assertThatThrownBy(() -> service.drop("holder")).isInstanceOf(RegistrationException.ServiceBusy.class);
            assertThatThrownBy(service::snapshot).isInstanceOf(RegistrationException.ServiceBusy.class);

            repo.release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            // the timed-out requests left no trace; only the holder's own enrolment exists
            assertThat(service.snapshot().enrolled()).containsExactly("holder");
            assertThat(service.snapshot().waitlist()).isEmpty();
        } finally {
            repo.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void interruptedWaiterFailsWithServiceBusyAndKeepsItsInterruptFlag() throws Exception {
        OfferingProperties props = new OfferingProperties("OPEN1", 5, "", 5000L);
        BlockingRepository repo = new BlockingRepository();
        RegistrationService service = new RegistrationService(props, new EligibilityPolicy(props), repo);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            repo.block = true;
            pool.submit(() -> service.enroll("holder"));
            assertThat(repo.entered.await(5, TimeUnit.SECONDS)).isTrue();
            repo.block = false;

            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> service.enroll("waiter")).isInstanceOf(RegistrationException.ServiceBusy.class);
            assertThat(Thread.interrupted()).as("interrupt flag preserved").isTrue();
        } finally {
            repo.release.countDown();
            pool.shutdownNow();
        }
    }
}
