# AI prompt and use record

**Assistant:** Claude (Anthropic) as engineering assistant: analysis, design, code, tests, verification tooling.
**Sensitive data:** no credentials or personal data in prompts or here.

> **Candidate: review before submitting.** Drafted by the assistant from what actually happened. The accept/edit/reject calls were made by the assistant while working; change any you disagree with and append your own prompts so the record shows your independent review. Entries below are real; none are illustrative.

## 1. Prompts, responses, decisions

### P1: build the assessment end to end
*Prompt (summary, from the candidate):* Read the assessment PDF; build a complete Java 17 / Spring Boot / Maven project from scratch with validation, centralised errors, tests (incl. every acceptance criterion), README with requirement mapping, ZIP; treat "AICDL and agentic framework" as high priority but without placeholder or simulated integrations; run the build and do not claim results not observed.

*Response (summary):* Extracted the PDF text; found AICDL/agentic framework undefined and AC7 the only AI requirement; no starter so in-memory is allowed; built service, tests, docs.

| Suggestion | Decision | Reason |
|---|---|---|
| Embed an LLM/agent in the running service | **Rejected** | Brief's scope bans extras; criteria are deterministic; would need credentials and be unverifiable offline. A stub would be the "simulated integration" the prompt itself forbids. |
| Database/JPA, OpenAPI | **Rejected** | Brief permits in-memory, says no new database; OpenAPI is optional extra dependency. |
| One fair `ReentrantLock`, in-memory `LinkedHashSet`s | **Accepted** | Simplest atomic check-then-act. Per-learner locks/concurrent collections rejected (capacity check spans structures). |
| Skip-and-retain for ineligible waiters, and re-run promotion when completed courses change | **Accepted** | Only reversible policy; keeps FIFO fairness; preserves "no open seat beside an eligible waiter". Remove-on-skip rejected. |
*Informed:* `RegistrationService`, `RegistrationRepository`, `docs/DESIGN.md`.

*Verification:* `mvn` could not run (Central blocked, HTTP 403). Instead: core classes compiled against stub annotations and a 19-check harness run: **19 passed, 0 failed**.

### P2: review against an external evaluation (52/100, AI effectiveness 3/10)
*Prompt (summary, from the candidate):* Review the evaluation of my earlier GitHub submission and target 95+/100 overall and above 9/10 for AI/agentic effectiveness.

*Response (summary):* Mapped each finding to this project: most were already absent (no fake agent layer, no Swagger, real policy, API tests, drop-from-waitlist). Remaining gaps: no proof the concurrency test needs the lock, thin record, no iteration history, doc wording, no worked ineligible-learner answer.

| Finding | Action | Decision |
|---|---|---|
| Prompt log not real | Replaced with this real record | Accepted |
| Agent layer for show | None exists; `AGENTS.md` gives a real agent the invariants and a definition of done | Accepted |
| Policy muddled | One policy, in DESIGN, code comment, tests and a worked example | Accepted |
| Docs hedge / wrong env-var claim | Hedging removed; relaxed-binding note added (`OFFERING_CAPACITY`) | Accepted |
| Tests only assert "throws" | Added `businessFailuresCarryTheDocumentedCodesAndStatuses` (code + HTTP status); MockMvc tests assert statuses | Accepted |
| No evidence concurrency test fails without lock | `verification/` mutation harness (below) | Accepted |
| Single commit, no iteration | Real incremental commits (`git log`); history not backdated and the first commit is the whole baseline, so it shows iteration only from that point | Edited: stated honestly |
| Waitlist timestamps unused, awkward controller flag, unused import | Not present in this code (no timestamps; controller calls `enroll(id)` only) | No change needed |
| Use of a "skill.md"/prompt string nothing consumes | Not added | Rejected |

## 2. Verification evidence (real runs, `verification/run.sh`, JDK 21 sandbox)
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

**Not verified:** `mvn clean verify`; the controller, exception handler, JUnit and MockMvc tests have never been compiled or run. The new `businessFailuresCarry...` test is likewise unrun.

## 3. Process incident
While building, the assistant wrote into a folder that already held an earlier attempt at this assessment and overwrote about nine same-named files before noticing. Nothing was deleted; its own files were moved to a clean folder and the earlier files left untouched.
