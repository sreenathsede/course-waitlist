# AI prompt and use record

**Assistant:** analysis, design, code, tests, verification tooling.  
**Sensitive data:** no credentials or personal data in prompts or here.

## 1. Prompts, responses, decisions

### P1: build the assessment end to end
*Prompt :* Read the assessment PDF; build a complete Java 17 / Spring Boot / Maven project from scratch with validation, centralized errors, tests (incl. every acceptance criterion), README with requirement mapping, ZIP; treat  agentic framework as high priority but without placeholder or simulated integrations; run the build and do not claim results not observed.

*Response (summary):* Extracted the PDF text; found agentic framework undefined and AC7 the only AI requirement; no starter so in-memory is allowed; built service, tests, docs.

| Suggestion | Decision | Reason |
|---|---|---|
| Embed an LLM/agent in the running service | **Rejected** | Brief's scope bans extras; criteria are deterministic; would need credentials and be unverifiable offline. A stub would be the "simulated integration" the prompt itself forbids. |
| Database/JPA, OpenAPI | **Rejected** | Brief permits in-memory, says no new database; OpenAPI is optional extra dependency. |
| One fair `ReentrantLock`, in-memory `LinkedHashSet`s | **Accepted** | Simplest atomic check-then-act. Per-learner locks/concurrent collections rejected (capacity check spans structures). |
| Skip-and-retain for ineligible waiters, and re-run promotion when completed courses change | **Accepted** | Only reversible policy; keeps FIFO fairness; preserves "no open seat beside an eligible waiter". Remove-on-skip rejected. |
*Informed:* `RegistrationService`, `RegistrationRepository`, `docs/DESIGN.md`.

*Verification:* `mvn` could not run (Central blocked, HTTP 403). Instead: core classes compiled against stub annotations and a 19-check harness run: **19 passed, 0 failed**.

## 2. Verification evidence (real runs, `verification/run.sh`, JDK 17 sandbox)
Each mutant is a deliberate bug applied to a copy of `RegistrationService`; the checks must fail.

| Run | Outcome |
|---|---|
| Unmodified | **19 passed, 0 failed** |
| `nolock` (lock removed) | `ConcurrentModificationException` from the racing-enrol check, then process hung until timeout (exit 124): **caught** |
| `lifo` (promote latest) | 4 failed (FIFO promotion, waiter leaves, retained priority, racing drops): **caught** |
| `noeligibility` (promote without checking) | 3 failed (AC4 skip, retain, open-seat restore): **caught** |
| `nocapacity` (ignore capacity) | many failed (AC2, AC3, AC5...): **caught** |
| `dropnocheck` (repeated drop succeeds) | 3 failed (repeated drop, unknown drop, racing drops): **caught** |
| `removeineligible` (wrong policy) | 2 failed (retained, open-seat restore): **caught** |

### Maven build (run by the candidate on their own machine)
`mvn clean verify` reported: `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`. This covers the unit, concurrency and MockMvc API tests, including `businessFailuresCarryTheDocumentedCodesAndStatuses` and the controller and exception handler, none of which the assistant could compile in its sandbox (Maven Central blocked, HTTP 403). Result relayed by the candidate; the assistant did not observe it.