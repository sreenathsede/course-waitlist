# Instructions for AI coding agents working in this repo

Scope: ONE course offering. Enrol, drop, FIFO waitlist promotion. Do not add a frontend, catalogue, payments,
identity, a database, or a configurable policy engine.

## Invariants you must preserve (see docs/DESIGN.md)
1. 0 <= enrolled <= capacity.
2. A learner is in at most one of enrolled/waitlist, at most once.
3. Only eligible learners enter enrolment or the waitlist.
4. No open seat coexists with an eligible waitlisted learner.
5. Rejected requests change nothing (validate and throw BEFORE mutating).

## Policy: skip-and-retain
Ineligible waitlisted learners are skipped during promotion but keep their place. Never remove them.

## Concurrency
Every public RegistrationService operation runs inside `locked(...)`. The repository is deliberately not thread-safe.
Do not add a second lock or concurrent collections without re-reading the design note.

## Before reporting done
1. `mvn clean verify` must pass; quote the actual result.
2. `verification/run.sh` must print `38 passed, 0 failed`, and each mutant (`nolock lifo noeligibility nocapacity
   dropnocheck removeineligible`) must make it FAIL. A change that lets a mutant pass has weakened the tests.
3. If you cannot run a check, say so. Never claim a result you did not observe.

## Record keeping
Append every AI-assisted change to docs/AI-PROMPT-AND-USE-RECORD.md: prompt, response summary, accepted/edited/rejected,
and how it was verified. Do not put secrets in prompts.
