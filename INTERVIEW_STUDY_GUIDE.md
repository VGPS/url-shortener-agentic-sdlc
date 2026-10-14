# Interview Study Guide — Agentic-Proficient Software Engineer

Questions grouped by topic. Each answer is written as you'd say it out loud,
not as bullet points to memorize. The goal is to sound like someone who made
deliberate choices, not someone who followed a template.

---

## 1. Agentic Concepts

**Q: What's the difference between an agent and an orchestrator?**

An agent executes a single bounded task — it takes a prompt, calls an LLM, returns output.
It has no knowledge of what came before or after. The orchestrator owns control flow: which
agent runs next, what input it gets, whether the output is good enough to proceed, and what
to do on failure. In this system, `ClaudeAgentAdapter` is the agent; `SdlcOrchestrationService`
is the orchestrator. The practical test: an agent doesn't know it's in a pipeline; the
orchestrator does nothing itself except direct agents.

---

**Q: Is this actually an AI agent? It sounds more like a pipeline.**

Honest answer: it's a pipeline with a self-correcting feedback loop, which is the minimum
bar for "agentic." A purely linear pipeline accepts whatever the LLM returns. This system
evaluates each output against quality criteria via a second LLM call (the gate), feeds the
gate's specific remediation instruction back into the next attempt, and retries. That
perceive → act → evaluate → correct loop is the agentic quality. What it is NOT is a
tool-calling agent where the LLM decides what to do next — the dependency graph is fully
deterministic, and the LLM only fills in the content for each predefined phase.

---

**Q: When would you use a tool-calling agent instead of a deterministic pipeline?**

When the problem space is genuinely open-ended and you can't enumerate the steps in advance.
A customer-support agent that might need to check order status, then issue a refund, then
send an email — in any order, depending on what it finds — is a good fit for tool-calling.
An SDLC pipeline where the phases are always REQUIREMENTS → DESIGN → IMPLEMENTATION is not.
Deterministic pipelines are more auditable, cheaper (no LLM tokens spent on "what should I
do next?"), and easier to test. Use an agent when you need flexibility; use a pipeline when
you need predictability.

---

**Q: What is a ReAct loop? Does your system implement one?**

ReAct (Reason + Act) is the pattern where the LLM iterates: think about what to do, take an
action (tool call), observe the result, think again, take another action. It's an inner loop
inside a single agent invocation. This system does not implement ReAct — each phase is a
single LLM call followed by a separate gate evaluation call. The retry loop in `PhaseExecutor`
is superficially similar but is controlled by the Java orchestrator, not by the LLM reasoning
about its own output. A true ReAct implementation would have the LLM call tools (run tests,
check coverage, verify the spec) and adjust its output before returning.

---

**Q: What are the risks of agentic systems in production?**

Three main ones: cost, reliability, and safety.

Cost: each retry doubles token spend for that phase. Without circuit breakers, a misconfigured
gate that never passes can exhaust a budget in minutes.

Reliability: LLM outputs are non-deterministic. A gate that passes 90% of the time will
occasionally fail after 3 retries on well-formed output. The system needs to surface that as
a signal to improve the gate prompt, not just silently mark the run FAILED.

Safety: if the LLM controls tool execution (file writes, API calls, deployments), a hallucinated
tool call can cause real damage. This system sidesteps that by making all tool calls
deterministic — the LLM only produces text, and the Java layer decides what to do with it.

---

## 2. Architecture

**Q: Why hexagonal architecture for a 48-hour assignment?**

Because the assignment asked to demonstrate architectural discipline, not to ship a prototype
quickly. A reviewer reading the domain code should not need to know which framework is in use.
More practically: the AI adapter is the most likely thing to change (swap Claude for GPT, add
a Bedrock adapter, mock it for tests). Hexagonal architecture makes that swap a single class
replacement with zero domain changes. That's proven by the tests — every test that exercises
business logic uses a mock `AgentExecutionPort`, not a mock `ChatClient`.

---

**Q: What does "domain purity" mean and why does it matter?**

The domain layer — all the business rules, port interfaces, and domain records — contains zero
Spring, Lombok, JPA, or AI imports. Only the JDK. This matters for three reasons: testability
(domain logic tests need no Spring context, they run in under a second), portability (the domain
is a library that could be reused in a different delivery mechanism — CLI, batch job, different
web framework), and readability (a domain class should be understandable without knowing the
framework). The constraint is enforced not by convention but by arch-guard checks in CI.

---

**Q: Why store phase results and audit entries as JSON TEXT columns instead of separate tables?**

The primary read pattern for this system is "load the full run to display its status." If phase
results were in a normalized child table, every status poll would require a JOIN. JSON TEXT
lets a single query return the entire run. The cost is that you can't efficiently query
individual audit entries with SQL — but that wasn't a requirement. In a system where you needed
to ask "how many GATE_FAILED events occurred across all runs last week?", a separate
audit_entries table would be the right call.

---

**Q: How would you scale this to handle 100 concurrent orchestration runs?**

Several changes: First, make the executor pool configurable via a property and size it based
on profiling (the runs are I/O-bound on LLM API calls, so a larger pool than CPU count is
appropriate). Second, replace H2 with a real Postgres instance and add connection pooling
(HikariCP, already included via Spring Boot). Third, the approval polling loop currently holds
a thread for up to 30 minutes — at 100 concurrent approvals that's 100 threads blocked. Replace
the polling loop with a scheduled sweeper (one thread polls all PENDING approvals every 10s)
similar to the EnterpriseDataPushScheduler pattern in the main AIHealthcare project. Fourth,
the SSE emitter map is in-memory — if the app scales to multiple instances, a subscriber needs
to land on the same instance as its run. Sticky sessions or a pub/sub broker (Redis, SQS) would
fix that.

---

## 3. Concurrency and Async Design

**Q: Why does `startRun()` return a runId immediately instead of waiting for the run to finish?**

LLM calls are slow — minutes, not milliseconds. If `startRun()` blocked the HTTP thread until
all 6 phases completed, the connection would time out and the client would get no response.
Returning a runId immediately gives the client something to poll. This is the standard
pattern for long-running async jobs: accept (202), poll, result.

---

**Q: Why is `OrchestrationRun` immutable if the run is constantly changing?**

Because the execution thread and the REST polling thread both need access to run state, and
shared mutable state between threads requires locks. Instead of locking a mutable run object,
the execution thread writes to the database (via port methods like `updateStatus`,
`savePhaseResult`, `appendAuditEntry`), and the polling thread reads a fresh snapshot from
the database on each poll. The immutable record is assembled from that snapshot. No locks
needed. The tradeoff is that each poll is a database read rather than a direct memory lookup.

---

**Q: Why not use CompletableFuture for the parallel phases (IMPLEMENTATION + DOCUMENTATION)?**

The current implementation runs them sequentially in a single thread, which is simpler to
reason about, simpler to debug, and sufficient for the assignment. The `DependencyGraph.readyToStart()`
method already returns both phases when DESIGN completes — the execution engine just doesn't
submit them in parallel yet. The upgrade path is `CompletableFuture.allOf()` over both phase
futures, with the barrier before RELEASE_READINESS. That change is localized to
`SdlcOrchestrationService.executeRun()` and would require no domain changes.

---

**Q: Why poll for approval instead of blocking on a latch or a queue?**

Three reasons. Thread exhaustion: `CountDownLatch.await()` holds a thread for up to 30 minutes.
At 10 concurrent AMBIGUOUS runs, that's 10 threads blocked indefinitely. Restart resilience:
if the JVM crashes with a latch in progress, the approval is lost and the run can never resume.
A database-persisted `ApprovalRequest` survives restarts. Audit visibility: a latch either
counts down or it doesn't — there's no record of when it was created, who responded, or how
long it waited. The database row gives you all of that for free.

---

## 4. Spring AI

**Q: What is Spring AI and how did you use it?**

Spring AI is a Spring abstraction over LLM APIs (OpenAI, Anthropic, Bedrock, Gemini). It
provides a `ChatClient` that handles authentication, request construction, and response
parsing. In this project it's used in two adapters: `ClaudeAgentAdapter` for phase execution
and `ClaudePhaseGateAdapter` for gate evaluation. Spring AI 1.0.0 has a quirk: it
auto-configures `ChatClient.Builder` but not `ChatClient` itself, so you have to register
`@Bean ChatClient chatClient(ChatClient.Builder builder) { return builder.build(); }`
explicitly in `AppConfig`. Without that bean, any class that injects `ChatClient` directly
will fail at startup with a NoSuchBeanDefinitionException.

---

**Q: How does the streaming implementation work?**

Spring AI's `ChatClient` supports streaming via a reactive `Flux<String>` — each token is
emitted as it arrives from the API. The challenge is keeping the domain layer framework-free:
the domain port (`AgentExecutionPort`) can't reference `Flux` without importing Reactor.
The solution is a `Consumer<String>` callback in the port signature. The adapter calls
`tokenConsumer.accept(token)` for each token inside a `.doOnNext()` on the Flux, then
`.blockLast()` to collect the full response. The web layer passes a lambda that calls
`sseEmitter.send(token)` as the consumer. The domain service never knows SSE exists.

---

**Q: What model did you use and why?**

`claude-opus-5` — configured via `${ANTHROPIC_MODEL:claude-opus-5}` so it can be overridden.
For the gate evaluation calls, a cheaper/faster model (Haiku) would be appropriate in
production since gate evaluation is a structured parsing task, not creative generation.
The assignment didn't specify a budget constraint, so I defaulted to the most capable model.

---

## 5. Testing

**Q: Why no real LLM calls in any test?**

Cost, speed, and determinism. A test suite that calls a live API costs money on every run,
takes minutes instead of seconds, and can fail due to network issues or API rate limits.
The behavioral contracts are tested by mocking `AgentExecutionPort` and `PhaseGatePort` —
the same interfaces the production adapters implement. If a test needs to verify that the
orchestrator retries on gate failure, I configure the mock gate to fail twice then pass.
Real LLM behavior is covered by smoke tests tagged `@Profile("ai-integration")` that run
manually, not in CI.

---

**Q: What does the `@MockitoSettings(strictness = LENIENT)` annotation do and when do you need it?**

Mockito's default strict mode rejects stubs that are set up but never called in a specific
test — it's a correctness check that catches stale test setup. But when a `@BeforeEach`
method sets up stubs used by most tests, a test that short-circuits before reaching those
stubs (like the "empty agent output → no LLM call" gate test) will fail with
`UnnecessaryStubbingException`. `LENIENT` relaxes that check for the whole class.
The alternative is to move the stub setup into only the tests that need it, which is cleaner
but more verbose.

---

**Q: How did you test the AMBIGUOUS approval scenario end-to-end without waiting 10 seconds?**

The approval poll interval (10 seconds in production) is now an injectable parameter on the
service constructor. `AppConfig` reads it from `${aihealthcare.orchestration.approval-poll-ms:10000}`.
The integration test overrides it to 200ms via `@SpringBootTest(properties = "aihealthcare.orchestration.approval-poll-ms=200")`.
The domain service stays pure — it just uses whatever `long` it was constructed with.
This is preferable to a `@TestConfiguration` bean override because it doesn't require
`spring.main.allow-bean-definition-overriding=true`.

---

## 6. Design Decisions the Interviewer Will Probe

**Q: What would you change if you had another week?**

Three things. First, true parallel phase execution — submit IMPLEMENTATION and DOCUMENTATION
to the executor concurrently and use `CompletableFuture.allOf()` before RELEASE_READINESS.
Second, a real metrics calculation — `OrchestrationMetrics` is defined and the field is on
`OrchestrationRun`, but the calculation (MTTR, per-phase latencies, success rate) is not
wired in yet. Third, a dead-letter queue for runs that fail after rollback — currently they
just end up in FAILED status with no notification.

---

**Q: What's the weakest part of this design?**

The gate evaluator. Gate quality entirely determines whether the self-correction loop works.
If the gate prompt is vague ("did this look good?"), the remediation hints will be vague
("try again"), and retries won't improve. The gate prompts in `phase-gate-evaluate.txt` are
specific (per-phase required sections, exact format checks), but they've never been validated
against real LLM output — that would require the AI integration tests to run against a live API
and compare pass rates across prompt variations.

---

**Q: If this were a real Schwab internal tool, what compliance concerns would you flag?**

Four immediately. First, all LLM inputs and outputs should be logged for audit — the raw
prompts going to Claude and the raw responses are currently only in application logs, not in
a durable store with retention policy. Second, if the agent produces code, that code should
not be auto-deployed — the RELEASE_READINESS gate must require a human sign-off, not just an
LLM opinion. Third, PII policy: if the scenario description mentions customer data, that
description goes verbatim into the Claude API call, which means it leaves the Schwab perimeter.
Fourth, model versioning: `claude-opus-5` is a point-in-time model; a model update could
change gate pass rates without any code change, which needs to be tracked.
