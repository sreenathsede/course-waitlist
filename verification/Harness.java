import com.example.waitlist.config.OfferingProperties;
import com.example.waitlist.domain.*;
import com.example.waitlist.repository.RegistrationRepository;
import com.example.waitlist.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Plain-Java stand-in for the JUnit suite; run only because Maven Central is blocked in this sandbox. */
public class Harness {
    static int pass, fail;
    static void check(String name, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS " : "FAIL ") + name); }
    static <T extends Throwable> boolean throwsA(Class<T> c, Runnable r) { try { r.run(); return false; } catch (Throwable t) { return c.isInstance(t); } }
    static RegistrationService svc(int cap, String pre) {
        var p = new OfferingProperties("CS201", cap, pre, 2000L);
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

        // ---- timeout: a waiter that cannot get the lock fails with SERVICE_BUSY and changes nothing ----
        {
            var p = new OfferingProperties("OPEN1", 5, "", 150L);
            var repo = new BlockingRepo();
            var ts = new RegistrationService(p, new EligibilityPolicy(p), repo);
            var pool = Executors.newFixedThreadPool(3);
            repo.block = true;
            Future<?> holder = pool.submit(() -> ts.enroll("holder"));
            repo.entered.await(5, TimeUnit.SECONDS); repo.block = false;
            Future<?> late = pool.submit(() -> ts.enroll("late"));
            boolean busy;
            try { late.get(3, TimeUnit.SECONDS); busy = false; }
            catch (ExecutionException e) { busy = e.getCause() instanceof RegistrationException.ServiceBusy; }
            catch (TimeoutException e) { busy = false; }       // would mean the waiter blocked forever
            repo.release.countDown(); holder.get(5, TimeUnit.SECONDS); pool.shutdownNow();
            check("TIMEOUT waiter fails ServiceBusy, state untouched", busy && ts.snapshot().enrolled().equals(List.of("holder")) && ts.snapshot().waitlist().isEmpty());
        }

        // ---- mixed operations: invariants hold in every snapshot, 20 repetitions ----
        boolean mixedOk = true; String why = "";
        for (int rep = 0; rep < 20 && mixedOk; rep++) {
            var ms = svc(5, "CS101"); AtomicReference<String> bad = new AtomicReference<>(); var done = new AtomicInteger();
            Thread watcher = new Thread(() -> { while (done.get() == 0) { String v = violation(ms.snapshot(), 5); if (v != null) { bad.compareAndSet(null, v); return; } } });
            watcher.start(); List<Runnable> ops = new ArrayList<>();
            for (int t = 0; t < 8; t++) { final long seed = t * 31L + 7; ops.add(() -> {
                Random r = new Random(seed);
                for (int i = 0; i < 1500; i++) { String id = "L" + r.nextInt(30);
                    try { switch (r.nextInt(10)) { case 0, 1, 2, 3, 4 -> ms.enroll(id); case 5, 6, 7 -> ms.drop(id);
                          case 8 -> ms.updateCompletedCourses(id, Set.of("CS101")); default -> ms.updateCompletedCourses(id, Set.of()); } }
                    catch (RegistrationException ignored) { } } }); }
            race(ops); done.set(1); watcher.join(5000);
            for (int i = 0; i < 30; i++) ms.updateCompletedCourses("L" + i, Set.of("CS101"));
            var fin = ms.snapshot(); String v = bad.get() != null ? bad.get() : violation(fin, 5);
            if (v == null && !fin.waitlist().isEmpty() && fin.seatsAvailable() != 0) v = "open seat beside waiter";
            if (v != null) { mixedOk = false; why = v; }
        }
        check("CONC mixed ops keep invariants x20 " + why, mixedOk);

        System.out.println("\nRESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static String violation(OfferingSnapshot n, int cap) {
        if (n.enrolled().size() > cap) return "over capacity";
        if (n.seatsAvailable() != cap - n.enrolled().size()) return "seatsAvailable inconsistent";
        if (new HashSet<>(n.enrolled()).size() != n.enrolled().size()) return "dup enrolled";
        if (new HashSet<>(n.waitlist()).size() != n.waitlist().size()) return "dup waitlisted";
        if (!Collections.disjoint(n.enrolled(), n.waitlist())) return "both enrolled and waitlisted";
        return null;
    }

    static class BlockingRepo extends RegistrationRepository {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); volatile boolean block;
        @Override public boolean isEnrolled(String id) {
            if (block) { entered.countDown(); try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { } }
            return super.isEnrolled(id);
        }
    }

    static void race(List<Runnable> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(16); var go = new CountDownLatch(1); List<Future<?>> fs = new ArrayList<>();
        for (Runnable t : tasks) fs.add(pool.submit(() -> { go.await(); t.run(); return null; }));
        go.countDown(); for (var f : fs) f.get(20, TimeUnit.SECONDS); pool.shutdownNow();
    }
}
