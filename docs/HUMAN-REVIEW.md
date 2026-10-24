# Human review

**Reviewer:** Sreenath Sede. The five numbered comments below are the reviewer's own, quoted as given.

## Reviewer comments and resulting changes

### 1. "Why can't we use a queue instead of a set / LinkedHashSet?"
A queue *can* be used and is the more obvious data structure for FIFO. `LinkedHashSet` was chosen because the waitlist needs more than head/tail operations:
* **Duplicates are impossible by construction** (invariant 2). A plain `Queue`/`Deque` needs a parallel `HashSet` kept in sync, or an O(n) `contains` on every enrol.
* **Arbitrary removal.** A waitlisted learner can drop out of the middle of the queue. `Queue.remove(Object)` is O(n); `LinkedHashSet.remove` is O(1).
* **Skip-and-retain needs a non-destructive scan.** Promotion walks the queue front to back, skipping ineligible learners *without removing them*. `poll()` only exposes the head, so a queue would need peek-and-reinsert or iterating anyway.
* Insertion order is preserved by `LinkedHashSet`, so FIFO holds.

Trade-off accepted: position lookup is O(n) either way, which is fine at classroom scale. Decision: **keep `LinkedHashSet`**; reconsider a `Deque` plus a membership set only if profiling showed a problem.

### 2. "Timeout use cases are not handled"
Interpreted as: a request must not wait forever on the single offering lock. **Now handled:** lock acquisition uses `tryLock(timeout)` (`offering.lock-timeout-millis`, default 2000). On timeout or interrupt the request fails with `ServiceBusy` → HTTP 503 `SERVICE_BUSY` with `Retry-After`, before any state is touched, so retry is safe. The interrupt flag is restored. Tests: `RegistrationServiceTimeoutTest` (a waiter times out while another thread holds the lock; state is untouched; interrupted waiter).
**Not handled, by decision:** seat-offer expiry (holding a promoted seat for a learner to accept within a deadline). The brief forbids extra scope and promotion enrols immediately, so there is no pending offer to expire. Listed in `docs/LIMITATIONS.md`. *If the reviewer meant this second meaning, say so; it is a different feature.*

### 3. "Gate or remove the open records endpoint"
**Gated, fail-closed.** `PUT /api/learners/{id}/completed-courses` requires header `X-Admin-Key` matching `ADMIN_API_KEY`. If no key is configured the endpoint is disabled (403); a wrong or missing key is 401; comparison is constant-time. Tests: `AdminDisabledApiTest` and the key cases in `RegistrationApiTest`. Remaining limits (single shared key, clear-text HTTP, no rate limit, other endpoints still open) are recorded in `docs/LIMITATIONS.md`.

### 4. "Move the HTTP status mapping out of the service exception"
**Done.** Service exceptions now carry only an `ErrorCode`; they import nothing from Spring web. `web/HttpStatusMapper` is the only place that maps codes to statuses, using a `switch` with no default so a new code without a status does not compile. Test: `HttpStatusMapperTest`; the API tests still assert the actual statuses.

### 5. "Add a mixed-operation concurrency test"
**Done.** `RegistrationServiceConcurrencyTest.mixedEnrolDropAndRecordUpdatesPreserveInvariants`: 8 threads run 1,500 random enrol/drop/eligibility-change operations each over 30 learners while a watcher thread continuously checks snapshots for capacity overflow, duplicates and overlap; at the end all learners are re-qualified and the test asserts no seat is left open beside a waiter. The race tests are repeated (`@RepeatedTest`) so a rare interleaving has more chances to show. Known gap: the watcher checks invariant 4 only at the end (see `docs/AGENT-REVIEW.md`, finding 7).