# Trade-offs and Design Decisions

## 1. Hexagonal Architecture in an Assignment Context

**What:** The domain layer is framework-free (zero Spring, Lombok, JPA imports). This was verified by the arch-guard agent.

**Why in this context:** Demonstrating architectural discipline matters more in an interview than it would in a throwaway prototype. A reviewer reading the domain code should not need to know which framework is in use.

**Cost:** Additional boilerplate in `AppConfig` to wire beans explicitly, and the `System.Logger` pattern in domain services instead of `@Slf4j`. Accepted — the boilerplate is at the edges, not in the logic.

---

## 2. H2 Over Real Postgres

**What:** The database is H2 in-memory. A single property change in `application.yml` switches to Postgres.

**Why:** This is a 48-hour assignment. H2 eliminates the setup step for the reviewer. There are no Postgres-specific features in use (no pgvector, no JSONB queries, no custom types).

**Risk:** H2's CLOB handling differs slightly from Postgres TEXT. The JSON columns would be TEXT in Postgres; in H2 they are also stored as a string, so behaviour is equivalent for this use case.

---

## 3. JSON Columns for Phase Results and Audit Entries

**What:** Phase results (Map<SdlcPhase, PhaseResult>) and audit entries (List<AuditEntry>) are stored as JSON TEXT columns rather than normalized JPA entity relationships.

**Why:** The primary read pattern is "load the full run for the status poll endpoint" — one query, all data. Normalized tables would require a JOIN or a second query for audit entries. JSON TEXT also keeps the schema minimal (2 main tables).

**Cost:** Full-text search on individual audit events is not available via JPA. For this assignment that is not required. In production, a search requirement would justify adding a separate audit_entries table.

---

## 4. Polling-based Approval (No WebSocket, No Blocking Thread)

**What:** The orchestrator polls the `approval_requests` table every 10 seconds. The HTTP endpoint for submitting an approval response updates the row; the orchestrator detects it on the next poll.

**Why:** Blocking a JVM thread for up to 30 minutes is a resource risk (thread exhaustion in production). Restart resilience also matters — if the JVM restarts mid-approval, the PENDING record in the database allows the orchestrator to resume polling on restart.

**Cost:** Up to 10-second latency between a human approving and execution resuming. For an SDLC process that takes minutes per phase, 10 seconds is imperceptible.

---

## 5. Single-Process Parallel Phases (Not Multi-Thread)

**What:** IMPLEMENTATION and DOCUMENTATION are logically parallel (both depend only on DESIGN), but the current implementation runs them sequentially in the same thread for simplicity.

**Why:** True parallel execution requires either a thread barrier (wait for both to complete before starting RELEASE_READINESS) or a future-based approach. For an assignment, sequential execution with clear comments explaining *why* parallel is architecturally possible is the right trade-off.

**What would change:** The `executeRun` method would submit both phases to the `asyncExecutor` and use `CompletableFuture.allOf()` to wait for both before advancing. The `DependencyGraph.readyToStart()` method already returns both phases simultaneously when DESIGN completes — the execution engine just needs to handle the list as parallel submissions.

---

## 6. LLM Gate Evaluation vs. Rule-Based Gates

**What:** Phase exit gates call the LLM (Claude) to evaluate whether the output meets quality criteria.

**Why:** Rule-based checks (e.g., "does the output contain 'OPENAPI_SPEC:'?") catch structural issues but cannot assess content quality. An LLM evaluator can detect that the OPENAPI_SPEC section is present but contains placeholder text. This is the "self-correcting feedback loop" the assignment requires.

**Cost:** Each phase produces two LLM calls (execution + gate evaluation), roughly doubling token cost. For the assignment's 48-hour scope this is acceptable. In production, a tiered approach (rule-based fast check → LLM deep check only on near-pass) would reduce cost.

---

## 7. Remediation Hints Pass Gate Feedback Back to Agent

**What:** When a phase gate fails, the `GateResult` contains a `remediation` string. On retry, the orchestrator appends this to the prompt: "Your previous attempt failed because: [reason]. Specifically: [remediation]."

**Why:** Without feedback, a retry is just re-running the same prompt and hoping for a different result. With the gate's specific remediation hint ("Add AMBIGUITY_FLAGS section" or "OPENAPI_SPEC must start with 'openapi:'"), the agent has actionable guidance. This is what makes the retry loop self-correcting rather than random.

**Risk:** If the gate evaluator itself produces a vague remediation, the retry won't improve. Prompt quality for the gate is therefore as important as prompt quality for execution.

---

## 8. Artifact Hash Drift Detection

**What:** When DESIGN produces an `OPENAPI_SPEC` artifact, a hash of the content is stored in `OrchestrationContext`. Before IMPLEMENTATION starts, the orchestrator can recheck the hash to detect whether the spec was modified externally.

**Why:** In a real SDLC, a human might edit the generated spec before implementation begins (fixing errors the LLM missed). Without drift detection, IMPLEMENTATION would proceed with potentially inconsistent input without warning.

**Current status:** Drift detection is implemented in `OrchestrationContext` and is called in `executeRun`. In the current version it logs a warning; escalating to a blocking approval checkpoint would be the production behaviour.
