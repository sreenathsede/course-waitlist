"""Applies one deliberate bug ("mutant") to a copy of the service. Usage: mutate.py <mutant> <file>"""
import sys
m, path = sys.argv[1], sys.argv[2]
s = open(path).read()
def rep(old, new):
    global s
    assert old in s, f"mutation target not found: {old!r}"
    s = s.replace(old, new, 1)
if m == "nolock":      # remove mutual exclusion
    rep("acquired = lock.tryLock(offering.lockTimeoutMillis(), TimeUnit.MILLISECONDS);",
        "if (System.nanoTime() == Long.MIN_VALUE) throw new InterruptedException(); acquired = true;")
    rep("lock.unlock();", "")
elif m == "notimeout":   # wait for the lock forever instead of failing with SERVICE_BUSY
    rep("acquired = lock.tryLock(offering.lockTimeoutMillis(), TimeUnit.MILLISECONDS);", "lock.lockInterruptibly(); acquired = true;")
elif m == "lifo":      # promote latest instead of earliest
    rep("for (String candidate : repo.waitlistInOrder()) {",
        "List<String> rev = repo.waitlistInOrder(); java.util.Collections.reverse(rev);\n        for (String candidate : rev) {")
elif m == "noeligibility":   # promote without checking eligibility
    rep("if (policy.isEligible(repo.completedCourses(candidate))) {", "if (true) {")
elif m == "nocapacity":      # enrol without checking capacity
    rep("if (repo.enrolledCount() < offering.capacity()) {", "if (true) {")
elif m == "dropnocheck":     # repeated drop silently succeeds and refills
    rep("throw new NotRegistered(id);", "return new DropResult(id, DroppedFrom.WAITLIST, fillOpenSeats());")
elif m == "removeineligible":  # wrong policy: ineligible waiter is removed instead of retained
    rep('log.info("Skipping ineligible waitlisted learner {} (keeps place)", candidate);',
        'repo.removeFromWaitlist(candidate);')
else:
    sys.exit("unknown mutant " + m)
open(path, "w").write(s)
