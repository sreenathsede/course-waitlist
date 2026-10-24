# Limitations and follow-up risks
* The seeded-bug (mutant) checks run only against the offline harness, not the JUnit suite.
* State is in memory: lost on restart, correct for one instance only. A multi-instance deployment needs a database with row locking or a version column, and the same invariants re-tested.
* The completed-courses endpoint is gated by a single shared `X-Admin-Key` (disabled when unset), sent in clear over HTTP with no rate limiting. It is a stand-in for a records system, not real authentication.
* Authorisation is asymmetric: `GET /api/offering` lists all enrolled and waitlisted ids, and `DELETE /api/offering/enrollments/{id}` lets any caller drop any learner, both with no authentication. Identity is out of scope per the brief.
* Body validation runs before the admin gate, so an unauthenticated caller with a malformed body sees 400, not 401.
* The completed-courses response echoes the submitted codes, not the trimmed/upper-cased codes actually stored.
* Test gaps: the "racing drops" test checks the promoted set, not promotion order; the mixed-operation watcher checks invariants 1-2 continuously and invariant 4 only at the end; no MockMvc test exercises a real 503/`Retry-After`.
* Seat-offer expiry (a promoted learner confirming within a deadline) is not implemented; promotion enrols immediately.
* Retained-but-ineligible learners can hold queue places indefinitely; there is no expiry.
* Learner ids are validated by format only; there is no identity check.
* Waitlist position lookup is O(n); fine at classroom scale.
