# Course Waitlist: enrollment, drop and FIFO promotion for one offering

Java 17 · Spring Boot 3.3.5 · Maven · in-memory store (explicitly permitted: no starter project was supplied).

| Document | Purpose |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Service boundary, API, invariants, promotion rule, concurrency, assumptions |
| [docs/AI-PROMPT-AND-USE-RECORD.md](docs/AI-PROMPT-AND-USE-RECORD.md) | Prompts, responses, accepted/edited/rejected decisions, verification evidence |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Limitations and follow-up risks |

## Prerequisites
JDK 17+ and Maven 3.9+ with access to Maven Central for the first build. No database, credentials or environment variables.

## Build, test, run
```bash
mvn clean verify            # compile + all tests (unit, concurrency, MockMvc API)
mvn test                    # tests only
mvn -Dtest=RegistrationServiceTest test   # one class
mvn spring-boot:run         # http://localhost:8080
```
IntelliJ: *File > Open* > select `pom.xml` > run `WaitlistApplication`, or run the test classes directly.

## Configuration (`src/main/resources/application.yml`)
```yaml
offering:
  code: CS201
  capacity: 3
  prerequisite: CS101    # blank = no prerequisite
```

## Assumptions (stated per the assessment)
* **Prerequisite rule:** a learner is eligible if the offering has no prerequisite, or the learner's completed courses contain it (case-insensitive). Completed courses are set through `PUT /api/learners/{id}/completed-courses`, a stand-in for a records system (identity/records integration is out of scope). A learner with no record has completed nothing.
* **Only eligible learners ever join the queue.** An ineligible request gets 422 and nothing is stored.
* **Skip-and-retain:** if a waitlisted learner later becomes ineligible, promotion skips them but they keep their place. When eligibility is restored they are promoted into any open seat automatically and, being earlier, are served before later joiners.
* A waitlisted learner may drop to leave the queue (no seat change).

## API
| Method & path | Success | Failure codes |
|---|---|---|
| `POST /api/offering/enrollments` `{"learnerId":"a"}` | 201 `ENROLLED` or 202 `WAITLISTED` (+`position`) | 400 invalid · 409 `ALREADY_ENROLLED` / `ALREADY_WAITLISTED` · 422 `PREREQUISITE_NOT_MET` |
| `DELETE /api/offering/enrollments/{learnerId}` | 200 `{droppedFrom, promotedLearners}` | 400 invalid · 404 `NOT_REGISTERED` (unknown or repeated drop) |
| `GET /api/offering` | 200 snapshot | |
| `PUT /api/learners/{id}/completed-courses` `{"completedCourses":["CS101"]}` | 200 `{..., promotedLearners}` | 400 invalid |

```bash
curl -X PUT localhost:8080/api/learners/alice/completed-courses -H 'Content-Type: application/json' -d '{"completedCourses":["CS101"]}'
curl -X POST localhost:8080/api/offering/enrollments -H 'Content-Type: application/json' -d '{"learnerId":"alice"}'
# 201 {"learnerId":"alice","outcome":"ENROLLED","position":0}
# when full: 202 {"learnerId":"dave","outcome":"WAITLISTED","position":1}
curl -X DELETE localhost:8080/api/offering/enrollments/alice
# 200 {"learnerId":"alice","droppedFrom":"ENROLLMENT","promotedLearners":["dave"]}
```
Error body (all failures): `{"timestamp":"...","status":422,"code":"PREREQUISITE_NOT_MET","message":"Learner 'bob' has not completed prerequisite CS101","path":"/api/offering/enrollments"}`

## Acceptance criteria → implementation → tests
| AC | Implementation | Tests |
|---|---|---|
| 1 Enrol when capacity and prerequisite met | `RegistrationService.enroll`, `EligibilityPolicy` | `enrolsEligibleLearnerWhenSeatsRemain`, `prerequisiteMatchIsCaseInsensitive`, `RegistrationApiTest.enrolReturns201...` |
| 2 Full → waitlist once; no duplicates | `enroll` (duplicate checks precede mutation; `LinkedHashSet`) | `eligibleLearnerJoinsWaitlist...`, `duplicateEnrollment...`, `duplicateWaitlistRequest...`, `duplicateEnrollmentReturns409` |
| 3 Drop promotes earliest eligible | `drop` → `fillOpenSeats` | `dropPromotesEarliestWaitlistedLearner`, `dropPromotesFifoAndRepeatedDropReturns404` |
| 4 Ineligible never enrolled/promoted; policy consistent | skip-and-retain in `fillOpenSeats`; 422 at join | `ineligibleLearner...`, `ineligibleWaiterIsSkippedButRetainsPlace`, `restoredEligibility...`, `retainedLearnerKeepsPriority...` |
| 5 Invalid/duplicate drops are clear and safe | validation + `NotRegistered` before mutation; `GlobalExceptionHandler` | `repeatedDropFails...`, `droppingUnknownLearner...`, `invalidLearnerIds...`, API 400/404 tests |
| 6 Tests incl. concurrency | single fair `ReentrantLock` | `RegistrationServiceTest`, `RegistrationServiceConcurrencyTest` (3 scenarios), `RegistrationApiTest` |
| 7 AI use recorded | n/a | [docs/AI-PROMPT-AND-USE-RECORD.md](docs/AI-PROMPT-AND-USE-RECORD.md) |

## "AICDL / agentic framework"
The assessment defines neither. Its only AI requirement is AC7: use AI as an *engineering assistant* and record it. No LLM or agent runs inside the service: the brief forbids extra scope, and the acceptance criteria are deterministic. See the AI record for the reasoning.

## Verification status: read this
Maven Central was unreachable in the environment that produced this project, so **`mvn test` has not been run**. What was run: the config/domain/repository/service classes compiled against stub annotations and exercised by a 19-check plain-Java harness (all passed, including the concurrency cases). The controller, exception handler and JUnit/MockMvc tests have **not been compiled or executed**. Please run `mvn clean verify` and fix anything it reports.
