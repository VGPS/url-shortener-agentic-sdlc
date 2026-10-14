# Engineering Summary

## What Was Built

A URL shortener with a layered AI agent that can orchestrate a complete software development
lifecycle process — from requirements analysis through release readiness and test planning —
using Claude as the execution engine.

The system has two independently useful parts:

**1. URL Shortener** — A production-ready REST service with:
- Collision-resistant 6-character base-62 short codes (SecureRandom)
- Custom code support with conflict detection
- Click tracking (IP, user agent, referrer) with per-code analytics
- TTL-based expiry with active/inactive state
- 302 redirect at the root path, separate from the API prefix

**2. Agentic SDLC Orchestration** — An AI agent that:
- Orchestrates 6 SDLC phases in dependency-graph order (not a fixed linear sequence)
- Runs IMPLEMENTATION and DOCUMENTATION in parallel once DESIGN completes
- Evaluates each phase output via a separate LLM gate call with specific remediation hints
- Retries failed phases with exponential backoff and the gate's remediation appended to the prompt
- Persists every state change to the database so the run survives JVM restarts
- Streams agent output tokens to the browser via SSE
- Supports three scenario types: GREENFIELD (full pipeline), BROWNFIELD (skip requirements, enter at design with impact analysis), AMBIGUOUS (pause for human approval after requirements)
- Rolls back all completed phases when a failure is unrecoverable

## Architecture in 4 Lines

- **Domain layer**: pure JDK, zero framework imports — all business rules here
- **Infrastructure**: Spring AI, JPA adapters implement domain ports
- **Web**: Spring MVC controllers call inbound ports only, wrap SSE streaming
- **Config**: `AppConfig` wires everything with constructor injection

## Key Files for Review

| File | Why It Matters |
|------|---------------|
| `domain/service/SdlcOrchestrationService.java` | Core orchestration engine — dependency graph traversal, parallel phases, approval polling, rollback |
| `domain/service/DependencyGraph.java` | GREENFIELD/BROWNFIELD/AMBIGUOUS graph definitions + readyToStart() |
| `domain/service/PhaseExecutor.java` | Single-phase execution with bounded retry + gate loop |
| `domain/model/AgentExecutionPort.java` | Why Consumer<String> keeps the domain framework-free while enabling SSE |
| `domain/model/ApprovalRequest.java` | Why polling DB > blocking thread for approval checkpoints |
| `domain/model/AuditEntry.java` | Append-only audit design rationale |
| `infrastructure/ai/ClaudeAgentAdapter.java` | Streaming implementation — token consumer → Spring AI Flux → SSE |
| `infrastructure/ai/ClaudePhaseGateAdapter.java` | LLM gate evaluation with structured response parsing |
| `web/controller/OrchestrationController.java` | SSE emitter lifecycle, REST surface |
| `ARCHITECTURE.md` | Dependency rules, streaming architecture diagram |
| `SCENARIOS.md` | Three complete scenario walkthroughs with expected outputs |
| `TRADE-OFFS.md` | 8 explicit design decisions with reasoning |

## Tests: 97 Total (0 failures)

| Test Class | Count | Tests |
|-----------|-------|-------|
| `OrchestrationDomainTest` | 21 | All domain records, enums, context, exceptions |
| `OrchestrationEngineTest` | 15 | DependencyGraph traversal, PromptBuilder injection |
| `OrchestrationPersistenceTest` | 8 | @DataJpaTest — round-trip for run + approval adapters |
| `ClaudeAdapterTest` | 6 | Mocked ChatClient — execute, streaming, gate parse |
| `OrchestrationControllerTest` | 9 | MockMvc — all REST endpoints |
| `UrlShorteningServiceTest` | 12 | Pure domain service tests |
| `ShortUrlAdapterTest` | 8 | @DataJpaTest — URL persistence |
| `ClickEventAdapterTest` | 5 | @DataJpaTest — click tracking |
| `UrlShortenerControllerTest` | 10 | MockMvc — URL shortener endpoints |
| `RedirectControllerTest` | 2 | Redirect behaviour |
| `UrlShortenerApplicationTest` | 1 | Full context load smoke test |

No AI calls in any test. All AI adapters are either mocked (unit tests) or excluded (persistence/web slices).

## Reliability Metrics Tracked (Assignment Requirement)

The `OrchestrationMetrics` record (computed at run completion) tracks:
- `totalDurationMs` — end-to-end latency
- `phaseLatenciesMs` — per-phase execution duration
- `successRate` — fraction of non-skipped phases that succeeded without rollback
- `totalRetries` — retry frequency across all phases
- `rollbackCount` — rollback frequency
- `mttrMs` — mean time to recovery (average: FAILED → SUCCEEDED on retry)

Available via `GET /api/v1/orchestration/{runId}/metrics`.

## Constraints Observed

- Domain layer has zero Spring/Lombok/JPA imports (verified by arch-guard)
- All dependency injection is constructor-based
- Every class has a Javadoc block with `@author`, `@since`, `@updated`
- Per-method entry/exit logging on all concrete classes
- Streams are used in new code where appropriate (Java 21)
- No speculative abstractions beyond the assignment requirements
- H2 in-memory for zero-setup reviewer experience (Postgres swap = 1 yaml change)
