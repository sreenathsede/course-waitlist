import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.domain.*;
import com.example.waitlist.repository.RegistrationRepository;
import com.example.waitlist.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Plain-Java stand-in for the JUnit suite; run only because Maven Central is blocked in this sandbox. */
public class Harness {
    static int pass, fail;
    static void check(String name, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS " : "FAIL ") + name); }
    static <T extends Throwable> boolean throwsA(Class<T> c, Runnable r) { try { r.run(); return false; } catch (Throwable t) { return c.isInstance(t); } }
    static RegistrationService svc(int cap, String pre) {
        var p = new OfferingProperties("CS201", cap, pre);
        return new RegistrationService(p, new EligibilityPolicy(p), new RegistrationRepository());
    }
    static void q(RegistrationService s, String... ids) { for (String i : ids) s.updateCompletedCourses(i, Set.of("CS101")); }

    public static void main(String[] a) throws Exception {
        var s = svc(2, "CS101"); q(s, "a");
        check("AC1 enrol with seat", s.enroll("a").outcome() == EnrollmentOutcome.ENROLLED && s.snapshot().seatsAvailable() == 1);

        s = svc(2, "CS101");
        var s0 = s; check("AC1/4 prereq failure not stored", throwsA(RegistrationException.PrerequisiteNotMet.class, () -> s0.enroll("x")) && s0.snapshot().enrolled().isEmpty() && s0.snapshot().waitlist().isEmpty());

        s = svc(2, "CS101"); q(s, "a", "b", "c", "d"); s.enroll("a"); s.enroll("b");
        var c = s.enroll("c"); var d = s.enroll("d");
        check("AC2 waitlisted with positions", c.outcome() == EnrollmentOutcome.WAITLISTED && c.position() == 1 && d.position() == 2);
        var s1 = s; check("AC2 duplicate enrol/waitlist rejected, no dup record",
            throwsA(RegistrationException.AlreadyEnrolled.class, () -> s1.enroll("a")) && throwsA(RegistrationException.AlreadyWaitlisted.class, () -> s1.enroll("c")) && s1.snapshot().waitlist().equals(List.of("c", "d")));
        var dr = s.drop("a");
        check("AC3 FIFO promotion", dr.promotedLearners().equals(List.of("c")) && s.snapshot().enrolled().equals(List.of("b", "c")) && s.snapshot().waitlist().equals(List.of("d")));
        var s2 = s; check("AC5 repeated drop -> NotRegistered, seat count unchanged", throwsA(RegistrationException.NotRegistered.class, () -> s2.drop("a")) && s2.snapshot().seatsAvailable() == 0);
        check("AC5 unknown drop", throwsA(RegistrationException.NotRegistered.class, () -> s2.drop("ghost")));
        check("waitlisted learner can leave", s.drop("d").droppedFrom() == DropResult.DroppedFrom.WAITLIST && s.snapshot().waitlist().isEmpty());

        // skip-and-retain
        s = svc(2, "CS101"); q(s, "a", "b", "c", "d"); for (String i : List.of("a", "b", "c", "d")) s.enroll(i);
        s.updateCompletedCourses("c", Set.of());
        dr = s.drop("a");
        check("AC4 ineligible skipped, next promoted", dr.promotedLearners().equals(List.of("d")));
        check("AC4 ineligible retained", s.snapshot().waitlist().equals(List.of("c")));

        s = svc(2, "CS101"); q(s, "a", "b", "c"); s.enroll("a"); s.enroll("b"); s.enroll("c");
        s.updateCompletedCourses("c", Set.of()); s.drop("a");
        boolean open = s.snapshot().seatsAvailable() == 1;
        var promoted = s.updateCompletedCourses("c", Set.of("cs101"));
        check("seat stays open then restored eligibility promotes", open && promoted.equals(List.of("c")) && s.snapshot().enrolled().equals(List.of("b", "c")));

        s = svc(2, "CS101"); q(s, "a", "b", "c", "d"); s.enroll("a"); s.enroll("b"); s.enroll("c");
        s.updateCompletedCourses("c", Set.of()); s.enroll("d"); s.updateCompletedCourses("c", Set.of("CS101"));
        check("retained learner keeps priority over later joiner", s.drop("a").promotedLearners().equals(List.of("c")));

        // invalid ids
        var s3 = svc(2, "CS101");
        boolean allBad = true;
        for (String bad : new String[]{null, "", "  ", "bad id", "a/b", "x<script>", "x".repeat(65)})
            allBad &= throwsA(RegistrationException.InvalidRequest.class, () -> s3.enroll(bad)) && throwsA(RegistrationException.InvalidRequest.class, () -> s3.drop(bad));
        check("AC5 invalid ids rejected", allBad && s3.snapshot().enrolled().isEmpty());
        check("blank course code rejected", throwsA(RegistrationException.InvalidRequest.class, () -> s3.updateCompletedCourses("a", Set.of(" "))));
        var open1 = svc(1, ""); check("no-prerequisite offering", open1.enroll("any").outcome() == EnrollmentOutcome.ENROLLED && open1.enroll("two").outcome() == EnrollmentOutcome.WAITLISTED);
        var s4 = svc(2, "CS101"); q(s4, "a"); s4.enroll("a"); check("ids trimmed", throwsA(RegistrationException.AlreadyEnrolled.class, () -> s4.enroll(" a ")));

        // concurrency 1: capacity never exceeded
        var cs = svc(5, "CS101"); List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 60; i++) { String id = "L" + i; q(cs, id); tasks.add(() -> cs.enroll(id)); }
        race(tasks);
        var sn = cs.snapshot();
        check("CONC 60 racing enrols -> 5 enrolled / 55 waitlisted, no dup/overlap",
            sn.enrolled().size() == 5 && sn.waitlist().size() == 55 && new HashSet<>(sn.enrolled()).size() == 5 && new HashSet<>(sn.waitlist()).size() == 55 && Collections.disjoint(sn.enrolled(), sn.waitlist()));

        // concurrency 2: same learner
        var cs2 = svc(5, "CS101"); q(cs2, "same"); AtomicInteger ok = new AtomicInteger(); tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) tasks.add(() -> { try { cs2.enroll("same"); ok.incrementAndGet(); } catch (RegistrationException.AlreadyEnrolled e) { } });
        race(tasks);
        check("CONC same learner succeeds exactly once", ok.get() == 1 && cs2.snapshot().enrolled().equals(List.of("same")));

        // concurrency 3: racing duplicate drops
        var cs3 = svc(5, "CS101"); for (int i = 0; i < 60; i++) { q(cs3, "L" + i); cs3.enroll("L" + i); }
        AtomicInteger drops = new AtomicInteger(); tasks = new ArrayList<>();
        for (String id : cs3.snapshot().enrolled()) for (int k = 0; k < 3; k++) tasks.add(() -> { try { cs3.drop(id); drops.incrementAndGet(); } catch (RegistrationException.NotRegistered e) { } });
        race(tasks);
        sn = cs3.snapshot();
        check("CONC racing duplicate drops: 5 successes, FIFO promoted L5..L9, head L10",
            drops.get() == 5 && new HashSet<>(sn.enrolled()).equals(Set.of("L5", "L6", "L7", "L8", "L9")) && sn.waitlist().size() == 50 && sn.waitlist().get(0).equals("L10"));

        System.out.println("\nRESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void race(List<Runnable> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(16); var go = new CountDownLatch(1); List<Future<?>> fs = new ArrayList<>();
        for (Runnable t : tasks) fs.add(pool.submit(() -> { go.await(); t.run(); return null; }));
        go.countDown(); for (var f : fs) f.get(20, TimeUnit.SECONDS); pool.shutdownNow();
    }
}
