package com.example.waitlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.domain.DropResult.DroppedFrom;
import com.example.waitlist.domain.EnrollmentOutcome;
import com.example.waitlist.repository.RegistrationRepository;
import com.example.waitlist.service.RegistrationException.*;

/** Pure unit tests (no Spring context): capacity 2, prerequisite CS101. One test per acceptance-criteria behaviour. */
class RegistrationServiceTest {

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        OfferingProperties props = new OfferingProperties("CS201", 2, "CS101");
        service = new RegistrationService(props, new EligibilityPolicy(props), new RegistrationRepository());
    }

    /** Make a learner eligible for the prerequisite. */
    private void qualify(String... ids) {
        for (String id : ids) service.updateCompletedCourses(id, Set.of("CS101"));
    }

    // ---- AC1: available capacity ----
    @Test
    void enrolsEligibleLearnerWhenSeatsRemain() {
        qualify("a");
        var r = service.enroll("a");
        assertThat(r.outcome()).isEqualTo(EnrollmentOutcome.ENROLLED);
        assertThat(service.snapshot().enrolled()).containsExactly("a");
        assertThat(service.snapshot().seatsAvailable()).isEqualTo(1);
    }

    @Test
    void prerequisiteMatchIsCaseInsensitive() {
        service.updateCompletedCourses("a", Set.of("cs101"));
        assertThat(service.enroll("a").outcome()).isEqualTo(EnrollmentOutcome.ENROLLED);
    }

    // ---- AC1/AC4: prerequisite failure ----
    @Test
    void ineligibleLearnerIsNeitherEnrolledNorWaitlisted() {
        assertThatThrownBy(() -> service.enroll("nope")).isInstanceOf(PrerequisiteNotMet.class);
        assertThat(service.snapshot().enrolled()).isEmpty();
        assertThat(service.snapshot().waitlist()).isEmpty();
    }

    @Test
    void ineligibleLearnerIsRejectedEvenWhenFull() {
        qualify("a", "b");
        service.enroll("a"); service.enroll("b");
        assertThatThrownBy(() -> service.enroll("nope")).isInstanceOf(PrerequisiteNotMet.class);
        assertThat(service.snapshot().waitlist()).isEmpty();
    }

    // ---- AC2: full capacity & duplicates ----
    @Test
    void eligibleLearnerJoinsWaitlistWhenFullWithPosition() {
        qualify("a", "b", "c", "d");
        service.enroll("a"); service.enroll("b");
        var c = service.enroll("c");
        var d = service.enroll("d");
        assertThat(c.outcome()).isEqualTo(EnrollmentOutcome.WAITLISTED);
        assertThat(c.position()).isEqualTo(1);
        assertThat(d.position()).isEqualTo(2);
        assertThat(service.snapshot().enrolled()).hasSize(2);
    }

    @Test
    void duplicateEnrollmentDoesNotCreateSecondRecord() {
        qualify("a");
        service.enroll("a");
        assertThatThrownBy(() -> service.enroll("a")).isInstanceOf(AlreadyEnrolled.class);
        assertThat(service.snapshot().enrolled()).containsExactly("a");
    }

    @Test
    void duplicateWaitlistRequestDoesNotCreateSecondRecord() {
        qualify("a", "b", "c");
        service.enroll("a"); service.enroll("b"); service.enroll("c");
        assertThatThrownBy(() -> service.enroll("c")).isInstanceOf(AlreadyWaitlisted.class);
        assertThat(service.snapshot().waitlist()).containsExactly("c");
    }

    // ---- AC3: FIFO promotion ----
    @Test
    void dropPromotesEarliestWaitlistedLearner() {
        qualify("a", "b", "c", "d");
        service.enroll("a"); service.enroll("b"); service.enroll("c"); service.enroll("d");
        var result = service.drop("a");
        assertThat(result.droppedFrom()).isEqualTo(DroppedFrom.ENROLLMENT);
        assertThat(result.promotedLearners()).containsExactly("c");
        var snap = service.snapshot();
        assertThat(snap.enrolled()).containsExactly("b", "c");
        assertThat(snap.waitlist()).containsExactly("d");
        assertThat(snap.seatsAvailable()).isZero();
    }

    @Test
    void dropWithEmptyWaitlistJustOpensSeat() {
        qualify("a");
        service.enroll("a");
        var result = service.drop("a");
        assertThat(result.promotedLearners()).isEmpty();
        assertThat(service.snapshot().seatsAvailable()).isEqualTo(2);
    }

    // ---- AC4: skip-and-retain policy ----
    @Test
    void ineligibleWaiterIsSkippedButRetainsPlace() {
        qualify("a", "b", "c", "d");
        service.enroll("a"); service.enroll("b"); service.enroll("c"); service.enroll("d");
        service.updateCompletedCourses("c", Set.of());            // c loses the prerequisite while waiting
        var result = service.drop("a");
        assertThat(result.promotedLearners()).containsExactly("d"); // c skipped, d promoted
        assertThat(service.snapshot().waitlist()).containsExactly("c"); // c retained, not removed
        assertThat(service.snapshot().enrolled()).containsExactly("b", "d");
    }

    @Test
    void restoredEligibilityPromotesRetainedLearnerIntoOpenSeat() {
        qualify("a", "b", "c");
        service.enroll("a"); service.enroll("b"); service.enroll("c");
        service.updateCompletedCourses("c", Set.of());
        service.drop("a");                                         // seat opens, only waiter is ineligible
        assertThat(service.snapshot().seatsAvailable()).isEqualTo(1);
        var promoted = service.updateCompletedCourses("c", Set.of("CS101"));
        assertThat(promoted).containsExactly("c");
        assertThat(service.snapshot().enrolled()).containsExactly("b", "c");
        assertThat(service.snapshot().waitlist()).isEmpty();
    }

    @Test
    void retainedLearnerKeepsPriorityOverLaterJoiners() {
        qualify("a", "b", "c", "d");
        service.enroll("a"); service.enroll("b"); service.enroll("c");
        service.updateCompletedCourses("c", Set.of());
        service.enroll("d");                                       // joins behind c
        service.updateCompletedCourses("c", Set.of("CS101"));
        var result = service.drop("a");
        assertThat(result.promotedLearners()).containsExactly("c"); // c first, FIFO preserved
    }

    // ---- AC5/AC6: invalid and repeated drops ----
    @Test
    void repeatedDropFailsAndDoesNotFreeExtraSeat() {
        qualify("a", "b", "c");
        service.enroll("a"); service.enroll("b"); service.enroll("c");
        service.drop("a");                                         // c promoted, full again
        assertThatThrownBy(() -> service.drop("a")).isInstanceOf(NotRegistered.class);
        var snap = service.snapshot();
        assertThat(snap.enrolled()).containsExactly("b", "c");
        assertThat(snap.seatsAvailable()).isZero();
    }

    @Test
    void droppingUnknownLearnerFailsCleanly() {
        assertThatThrownBy(() -> service.drop("ghost")).isInstanceOf(NotRegistered.class);
        assertThat(service.snapshot().enrolled()).isEmpty();
    }

    @Test
    void waitlistedLearnerCanLeaveWithoutChangingSeats() {
        qualify("a", "b", "c", "d");
        service.enroll("a"); service.enroll("b"); service.enroll("c"); service.enroll("d");
        var result = service.drop("c");
        assertThat(result.droppedFrom()).isEqualTo(DroppedFrom.WAITLIST);
        assertThat(service.snapshot().waitlist()).containsExactly("d");
        assertThat(service.snapshot().enrolled()).containsExactly("a", "b");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "bad id", "a/b", "x<script>"})
    void invalidLearnerIdsAreRejectedBeforeAnyStateChange(String id) {
        assertThatThrownBy(() -> service.enroll(id)).isInstanceOf(InvalidRequest.class);
        assertThatThrownBy(() -> service.drop(id)).isInstanceOf(InvalidRequest.class);
        assertThat(service.snapshot().enrolled()).isEmpty();
    }

    @Test
    void learnerIdLongerThan64CharactersIsRejected() {
        assertThatThrownBy(() -> service.enroll("x".repeat(65))).isInstanceOf(InvalidRequest.class);
        qualify("y".repeat(64));
        assertThat(service.enroll("y".repeat(64)).outcome()).isEqualTo(EnrollmentOutcome.ENROLLED);
    }

    @Test
    void blankCourseCodeIsRejected() {
        assertThatThrownBy(() -> service.updateCompletedCourses("a", Set.of(" "))).isInstanceOf(InvalidRequest.class);
    }

    @Test
    void offeringWithoutPrerequisiteAcceptsAnyone() {
        OfferingProperties props = new OfferingProperties("OPEN1", 1, "");
        var open = new RegistrationService(props, new EligibilityPolicy(props), new RegistrationRepository());
        assertThat(open.enroll("anyone").outcome()).isEqualTo(EnrollmentOutcome.ENROLLED);
        assertThat(open.enroll("second").outcome()).isEqualTo(EnrollmentOutcome.WAITLISTED);
    }

    @Test
    void idsAreTrimmedSoPaddedDuplicatesAreCaught() {
        qualify("a");
        service.enroll("a");
        assertThatThrownBy(() -> service.enroll(" a ")).isInstanceOf(AlreadyEnrolled.class);
        assertThat(List.of("a")).isEqualTo(service.snapshot().enrolled());
    }
}
