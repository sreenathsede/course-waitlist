# Design specification

## Service boundary
One course offering. In scope: enroll, drop, FIFO waitlist promotion, and a learner-completed-courses stand-in feeding the prerequisite rule. Out of scope (per brief): frontend, catalog, payments, identity, multi-term, deployment, a database, a configurable policy engine.

## Layers
`web` (controller, DTOs, exception handler) → `service` (`RegistrationService`, `EligibilityPolicy`, `RegistrationException`) → `repository` (`RegistrationRepository`, in-memory) with `domain` result records and `config.OfferingProperties`.

## Data
`enrolled`: ordered set of learner ids. `waitlist`: insertion-ordered set (FIFO, no duplicates). `completedCourses`: learner → course codes. Capacity, offering code and prerequisite come from `application.yml`.

## Invariants (hold whenever the lock is free)
1. 0 ≤ |enrolled| ≤ capacity.
2. A learner is in at most one of enrolled/waitlist, at most once.
3. Only eligible learners enter enrollment or the waitlist.
4. No open seat coexists with an eligible waitlisted learner (seats are refilled immediately, including after a records update).
5. Every failed request is rejected before any mutation, so errors never corrupt state.

## Waitlist-promotion rule
Scan the waitlist front to back; promote each eligible learner until seats are full or the queue ends. **Ineligible learners are skipped and retain their place.** Rationale: eligibility is enforced at join time, so ineligibility at the front can only come from a later records change. Removing the learner would punish a possibly temporary data change and silently lose their place; retaining is reversible and keeps FIFO fairness.

## Worked example: a waitlisted learner becomes ineligible before reaching the front
Capacity 2, prerequisite CS101. `a`, `b` enrolled; `c`, `d` waitlisted (positions 1, 2).
1. The records feed removes CS101 from `c`. Nothing moves yet: `c` stays at position 1.
2. `a` drops. Promotion scans: `c` ineligible, so skipped and retained; `d` eligible, so promoted. Result: enrolled `b, d`, waitlist `c`.
3. Case A: `c` regains CS101 while the seat is full. `c` stays first in line and gets the next seat.
   Case B: instead `b` drops with `c` still ineligible, so the seat stays open. When `c` regains CS101, `updateCompletedCourses` re-runs promotion and `c` is seated immediately (invariant 4).
Tests: `ineligibleWaiterIsSkippedButRetainsPlace`, `restoredEligibilityPromotesRetainedLearnerIntoOpenSeat`, `retainedLearnerKeepsPriorityOverLaterJoiners`.

## Concurrency
All operations take one fair `ReentrantLock`. The aggregate is tiny, so a coarse lock is simplest and makes check-then-act (count, then add) atomic; the repository is deliberately not thread-safe. **Timeouts:** lock acquisition is bounded (`offering.lock-timeout-millis`). A request that cannot get the lock fails with `SERVICE_BUSY` (503, `Retry-After`) before touching any state, so a stuck holder cannot pile up unbounded request threads and a timed-out request is safe to retry. Seat-offer expiry (holding a promoted seat for a learner to accept) is out of scope: promotion enrols immediately. Alternatives rejected: per-learner locks or concurrent collections (capacity check spans several structures), optimistic versioning (needs a persistence layer we do not have).

## API
See README. Status mapping lives only in `web/HttpStatusMapper` (the service layer throws `ErrorCode`s and knows no HTTP): 201 enrolled · 202 waitlisted · 400 invalid · 401 bad admin key · 403 records endpoint disabled · 404 nothing to drop · 409 duplicate · 422 prerequisite not met · 503 busy.

## Trade-offs
In-memory state is lost on restart and single-instance only. Dropping a waitlisted learner is allowed so people can leave the queue. Duplicate enrol requests return 409 rather than an idempotent 200 so callers get an unambiguous signal.
