# AI prompt and use record

**Assistant:** Claude (Anthropic), used as an engineering assistant for analysis, design, code and test generation.
**Sensitive data:** no credentials or personal data were placed in prompts or recorded here.

> **Candidate: review before submitting.** This record was drafted by the assistant from what actually happened in the session. The decisions below were made by the assistant while generating the work; amend any you disagree with and append your own prompts, so the record reflects your independent review.

## 1. Prompts, responses and decisions

### Prompt 1: the task
*Prompt (summary):* Given the assessment PDF, build a complete Java/Spring Boot Maven project from scratch with tests, README, requirement mapping and a ZIP; treat "AICDL and agentic framework" as a high-priority scoring item; use real framework usage, not placeholders; verify before claiming success.

*Response (summary):* Extracted the PDF text. Observed that (a) neither AICDL nor an agentic framework is defined, (b) the only AI requirement is AC7, (c) no starter project exists so an in-memory store is allowed, (d) a rubric file is referenced but not supplied.

| Suggestion | Decision | Reason |
|---|---|---|
| Embed an LLM/agent in the running service | **Rejected** | Not required; scope boundaries forbid extras; acceptance criteria are deterministic; it would need credentials and could not be verified offline. A stub would misrepresent the work. |
| Database / JPA | **Rejected** | Brief permits in-memory and says no new database. |
| OpenAPI/Swagger | **Rejected** | Optional; README documents the API; avoids an extra dependency. |
| Spring Boot 3.3.5, Java 17, in-memory repository | **Accepted** | Matches the brief. |
*Informed:* `pom.xml`, overall architecture, this record.

### Prompt 2 (assistant's own design question): ineligible learner at the front
*Response (summary):* Compared remove-on-skip with skip-and-retain; chose skip-and-retain, and noted a seat could stay open beside a newly eligible waiter unless a records update re-runs promotion.

| Suggestion | Decision |
|---|---|
| Skip-and-retain | **Accepted** |
| Re-run promotion when completed courses change | **Accepted** (invariant 4) |
| Remove ineligible learner from queue | **Rejected** |
*Informed:* `RegistrationService.fillOpenSeats`, `updateCompletedCourses`, DESIGN.md.

### Prompt 3: concurrency approach
| Suggestion | Decision | Reason |
|---|---|---|
| One fair `ReentrantLock` around every operation | **Accepted** | Simplest way to make check-then-act atomic. |
| Per-learner locks / concurrent collections | **Rejected** | Capacity check spans several structures. |
*Informed:* `RegistrationService`, `RegistrationServiceConcurrencyTest`.

### Review pass on generated code
| Finding | Decision |
|---|---|
| Catch-all `@ExceptionHandler(Exception.class)` would turn framework 404/405/415 errors into 500s | **Edited**: framework errors now keep their status (`GlobalExceptionHandler.unexpected`). |

## 2. Verification evidence
| What | Result |
|---|---|
| `mvn test` / `mvn package` | **Not run.** Maven Central returned HTTP 403 (policy) in the authoring environment. |
| Core classes (config, domain, repository, service) compiled with `javac` against stub Spring/SLF4J/Jakarta annotations | Compiled cleanly. |
| 19-check plain-Java harness mirroring the unit and concurrency tests (AC1-AC5, skip-and-retain, 60 racing enrolments, same-learner race, racing duplicate drops) | **19 passed, 0 failed.** |
| Controller, exception handler, JUnit and MockMvc tests | **Not compiled or executed.** Reviewed by reading only. |

## 3. Process incident (candid note)
While building, the assistant wrote into a working folder that already held an earlier attempt at the same assessment, overwriting about nine same-named files (pom.xml, application.yml, main class, config and four domain records) before noticing. Nothing was deleted; the assistant moved only its own files into a clean folder and left the remaining earlier files untouched.
