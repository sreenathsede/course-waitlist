# Limitations and follow-up risks
* The seeded-bug (mutant) checks run only against the offline harness, not the JUnit suite.
* State is in memory: lost on restart, correct for one instance only. A multi-instance deployment needs a database with row locking or a version column, and the same invariants re-tested.
* The completed-courses endpoint is unauthenticated and trusts the caller; it stands in for a records system.
* Retained-but-ineligible learners can hold queue places indefinitely; there is no expiry.
* Learner ids are validated by format only; there is no identity check.
* Waitlist position lookup is O(n); fine at classroom scale.
