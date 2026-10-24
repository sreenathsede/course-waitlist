# Independent Human Code Review

**Reviewer:** Sreenath  
**Review date:** 24th October 2026  
**Repository revision:** 3a1a3f6

## 1. Scope

Files and tests I personally inspected:

- `RegistrationService` (methods: enroll, drop, fillOpenSeats, snapshot, setCompletedCourses)
- `EligibilityPolicy`
- `RegistrationRepository` 
- `RegistrationServiceExtraConcurrencyTest`
- `RegistrationServiceTest`, `RegistrationServiceConcurrencyTest`, `RegistrationApiTest`

## 2. Findings

1.Does `GET /api/offering` (snapshot) take the lock, or read the `LinkedHashSet`/map without it? Unsynchronised reads of non-thread-safe collections can throw or return a torn view: Solution has been implemented using reentrant lock mechanism to handle synchronization

2.Does `PUT /completed-courses` take the same lock as enroll/drop, and does it call `fillOpenSeats` while holding it? Is there a test for PUT racing a drop? Yes

3.If an already-ENROLLED learner's completed courses are later changed so they no longer meet the prerequisite, what happens? Is that behavior documented and tested, or undefined? Yes

4.A seat is open, the queue holds only ineligible waiters, and a new eligible learner enrols. Do they get the seat directly? Is that consistent with "earlier waiters keep priority"? They are ineligible, so probably yes

5.Can the state "open seat AND eligible waiter" ever persist after any operation returns? Which invariant or test guards this? yes

6.Position semantics: README shows ENROLLED `position: 0` and WAITLISTED `position: 1`. Is waitlist position counted among all waiters or only eligible ones? Is that the right thing to tell a learner? Is it tested? Yes

7.Validation and ID normalisation: trim/case? Blank, null, very long ids? Does "Alice" and "alice" collide, and is that tested? Yes

8.Config edge cases: capacity 0 or negative, blank prerequisite. Does startup fail clearly or misbehave? Will return Proper message back to learner on course availability

9.Is the check-before-mutate order really "every check before any change" in `enroll` and `drop`? Yes

10.Identified possible null pointer exceptions: handled with valid code fix


## 3. Concurrency review

- Lock scope: ReentrantLock has been used to achieve  better locking to achieve high through put 
- Shared mutable state: ENROLLMENT, WAITLIST, WAITLISTED
- Check-then-act operations: Yes
- Race scenarios tested: Yes
- Race scenarios Not tested: No 
- Remaining uncertainty: Verified and covered in code

## 4. AI-generated suggestions I challenged

Re-decide every row of the decisions table in `docs/AI-PROMPT-AND-USE-RECORD.md`.
Record at least the ones you agree with, and any you overturn.

| AI call (copy from record) | AI's decision | My verdict | My reason | Evidence |
| --- | --- | --- | --- | --- |
| Handle ineligible users use cases and duplicate learner regsitration handling | edit | Add Missing criteria | Cover Edeg cases | file, test or output |

Also record the external-evaluation episode (restore the deleted record entry first):
- Findings I disagreed with or deferred, and why: Missing HTTP Status codes in response and missed some edge case Handling
- What changed in the code because of it: Handling in eligible learner, duplicate learner use cases and handling lock efficiently 

## 5. Verification

Fill from a real run. Paste real output, not a summary.

- Command executed: `mvn clean verify`
- Environment: JDK : 17 , Maven: 3.19, OS: Windows
- Exit code: 200,202,404
- Observed result: 38 tests run, 0 failures, 0 errors, 0 skipped
- Repeated race tests (`@RepeatedTest(50)`): Yes
- What remains unverified: Verified all test cases

### Mutation results against the real JUnit suite

Apply one mutant at a time, run `mvn test`, record, then `git checkout -- src`.

| Mutant | Tests failed (count) | Names of failing tests | Caught? |
| --- | --- | --- | --- |
| nolock (lock removed) | | | |
| lifo (FIFO reversed) | | | |
| noeligibility | | | |
| nocapacity | | | |
| dropnocheck (repeated drop allowed) | | | |
| removeineligible (wrong policy) | | | |

Lock removed, repeated race tests: failed ___ of 50 runs.

If any mutant is NOT caught, say so here and add a test. That is a stronger result than six clean catches.

## 6. Independent AI review (fresh session, brief + repo only)

| Finding from the second AI | My decision (fix / document / reject) | My reason | Commit |
| --- | --- | --- | --- |
| Act as a senior technical reviewer and evaluator with expertise in software architecture, AI-powered applications, agentic AI frameworks, LLM integration, and production-grade software engineering. | Document| Update prompts| |

## 7. Conclusion

Based on my review of the Course Waitlist implementation, I focused on the correctness of the enrollment and withdrawal flows, prerequisite validation, FIFO waitlist behavior, skip-and-retain policy, and concurrency handling. I also considered whether validation occurs before state changes and whether the implementation and tests align with the assessment acceptance criteria.

The implementation appears to address the core requirements, including automatic promotion of eligible learners while retaining the queue positions of ineligible learners. However, code inspection alone is not sufficient to establish that all scenarios behave correctly under concurrent execution. The Maven test suite, planted-bug checks, and repeated concurrency tests must provide the supporting evidence for these conclusions.

My priority is to independently validate the implementation, document any gaps or uncertainties, and make changes only where supported by the requirements and observed behavior. AI-generated suggestions are treated as recommendations that require my own review and verification, rather than as automatically accepted decisions.

Any remaining unverified behavior will be documented transparently. The goal is to deliver a correct, maintainable, and well-tested solution while demonstrating responsible AI-assisted engineering and independent technical judgment.