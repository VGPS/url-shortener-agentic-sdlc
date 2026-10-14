# Charles Schwab Interview Assignment — Build Plan
## Agentic Software Engineering System: URL Shortener

**Author:** Bill Blackmon  
**Assignment:** Agentic-Proficient Software Engineer — URL Shortener  
**Target:** C:\workspaces\cSchwabAssignment  
**Stack:** Java 21, Spring Boot 3.x, Spring AI (Anthropic Claude), H2, Maven  
**Duration:** 2–3 days  
**Last updated:** 2026-10-14

---

## What Is Being Graded

The URL shortener is a simple, well-understood problem deliberately chosen so evaluators can
focus entirely on the **agentic orchestration layer**. The grading rubric weights:

1. Effectiveness of agentic orchestration (critical differentiator)
2. Architecture/system design quality
3. Depth of decomposition and execution quality
4. Validation and risk management rigor
5. Engineering judgment

**Rule:** Build the URL shortener fast (Day 1). Invest the majority of time (Day 2) on the
orchestration engine. Documentation and scenarios close it out (Day 3).

---

## Architecture Overview

### Coding Rules (from CLAUDE.md — apply to every file)
- Java 21 records for all immutable data types (domain models, DTOs, result types)
- Hexagonal / Ports-and-Adapters: domain has zero Spring/Lombok/AI imports
- Constructor injection only — no `@Autowired` field injection
- Lombok `@Slf4j` on every concrete class
- Per-method logging: first line logs all args, last line before every return logs result
- Class headers: Javadoc with `@author Bill Blackmon`, `@since`, `@updated`
- JUnit 5 + AssertJ + Mockito for tests
- Streams permitted in new code
- Streaming API responses permitted (Spring AI `Flux<String>`)

### Package Structure

```
com.wgblackmon.urlshortener
├── domain
│   ├── model          # Records: ShortUrl, ClickEvent, AnalyticsSummary, + orchestration domain
│   ├── port
│   │   ├── inbound    # Use-case interfaces (driven ports)
│   │   └── outbound   # Adapter interfaces (driving ports)
│   ├── service        # Pure domain services (no Spring)
│   └── exception      # Domain exceptions
├── infrastructure
│   ├── ai             # Spring AI / Claude adapter
│   ├── persistence    # JPA entities, repos, adapters
│   └── orchestration  # Orchestration engine implementation
└── web
    ├── controller     # Spring MVC controllers
    └── dto            # Request/response DTOs
```

### Dependency Rules
```
web  ──▶  domain (inbound ports)
infrastructure  ──▶  domain (outbound ports)
domain  ──▶  JDK only (no Spring, no Lombok, no AI)
```

---

## Domain Model

### URL Shortener Domain

| Record | Fields | Notes |
|--------|--------|-------|
| `ShortUrl` | code, originalUrl, createdAt, expiresAt, createdBy, active | Core aggregate |
| `ClickEvent` | eventId, code, clickedAt, userAgent, ipAddress, referrer | Immutable click record |
| `AnalyticsSummary` | code, totalClicks, clicksByDay, topReferrers, lastClickedAt | Computed summary |
| `ShortenRequest` | originalUrl, customCode (nullable), ttlDays (nullable), createdBy | Input record |
| `ShortenResult` | shortUrl, shortCode, fullShortLink, alreadyExisted | Output record |

### Orchestration Domain

| Record/Enum | Fields | Notes |
|-------------|--------|-------|
| `SdlcPhase` | REQUIREMENTS, DESIGN, IMPLEMENTATION, TESTING, DOCUMENTATION, RELEASE_READINESS | Enum |
| `PhaseStatus` | PENDING, AWAITING_GATE, RUNNING, AWAITING_APPROVAL, SUCCEEDED, FAILED, ROLLED_BACK, SKIPPED | Enum |
| `ScenarioType` | GREENFIELD, BROWNFIELD, AMBIGUOUS | Enum |
| `OrchestrationRun` | runId, scenarioType, scenarioDescription, phases, status, startedAt, completedAt, auditEntries, metrics | Aggregate |
| `PhaseResult` | phase, status, agentOutput, artifactsProduced, startedAt, completedAt, retryCount, rollbackReason | Immutable result |
| `AuditEntry` | entryId, runId, phase, action, actor, detail, occurredAt | Append-only |
| `OrchestrationMetrics` | runId, totalDurationMs, phaseLatencies, successRate, retryCount, rollbackCount, mttrMs | Computed |
| `ApprovalRequest` | requestId, runId, phase, question, requestedAt, approved, respondedAt, respondedBy | Checkpoint record |
| `GateResult` | passed, reason, remediation | Gate evaluation result |
| `RetryPolicy` | maxAttempts, backoffMs, backoffMultiplier | Retry configuration |
| `AgentTask` | taskId, phase, prompt, contextSummary, streamingEnabled | Unit of agent work |
| `AgentOutput` | taskId, phase, rawResponse, structuredArtifacts, tokensUsed, completedAt | LLM response wrapper |

---

## Port Interfaces

### Inbound (Use Cases)

| Port | Methods |
|------|---------|
| `ShortenUrlUseCase` | `shorten(ShortenRequest)` → `ShortenResult` |
| `ResolveUrlUseCase` | `resolve(code)` → `Optional<ShortUrl>` |
| `TrackClickUseCase` | `track(code, userAgent, ip, referrer)` → `ClickEvent` |
| `GetAnalyticsUseCase` | `getSummary(code)` → `AnalyticsSummary` |
| `ConductOrchestrationUseCase` | `startRun(scenarioType, description)` → `OrchestrationRun`; `getRun(runId)`; `approve(requestId, respondedBy)`; `stopRun(runId)` |
| `QueryOrchestrationUseCase` | `getAuditLog(runId)`; `getMetrics(runId)`; `listRuns()` |

### Outbound (Adapter Interfaces)

| Port | Methods |
|------|---------|
| `ShortUrlPort` | `save(ShortUrl)`; `findByCode(code)`; `existsByCode(code)`; `existsByOriginalUrl(url)` |
| `ClickEventPort` | `record(ClickEvent)`; `findByCode(code)`; `countByCode(code)` |
| `CodeGeneratorPort` | `generate()` → `String` (6-char alphanumeric) |
| `OrchestrationRunPort` | `save(OrchestrationRun)`; `findById(runId)`; `findAll()`; `appendAuditEntry(entry)` |
| `ApprovalPort` | `requestApproval(ApprovalRequest)`; `findPendingByRunId(runId)`; `recordResponse(requestId, approved, respondedBy)` |
| `AgentExecutionPort` | `execute(AgentTask)` → `AgentOutput`; `executeStreaming(AgentTask)` → `Flux<String>` |
| `PhaseGatePort` | `evaluateEntryGate(phase, context)` → `GateResult`; `evaluateExitGate(phase, result, context)` → `GateResult` |

---

## Orchestration Engine Design

The engine is the critical differentiator. It must demonstrate:

### Dependency Graph
```
REQUIREMENTS ──▶ DESIGN ──▶ IMPLEMENTATION ──▶ TESTING
                                    │                │
                                    ▼                ▼
                             DOCUMENTATION ◀── RELEASE_READINESS
```
- Parallel paths: IMPLEMENTATION and DOCUMENTATION can run concurrently after DESIGN
- Synchronization barrier: RELEASE_READINESS waits for both TESTING and DOCUMENTATION

### Entry/Exit Gates
Each phase has:
- **Entry gate**: validates preconditions (e.g., DESIGN entry requires REQUIREMENTS artifacts)
- **Exit gate**: validates outputs (e.g., IMPLEMENTATION exit requires test coverage > 70%)
- Gate failure → retry or human escalation

### Human Approval Checkpoints
Required before:
- IMPLEMENTATION (approve the design artifacts)
- RELEASE_READINESS (approve test results and documentation)

Implemented as a blocking REST poll — orchestrator sets phase to `AWAITING_APPROVAL`,
caller polls `GET /api/v1/orchestration/runs/{runId}` until status changes, then POSTs approval.

### Retry + Rollback
- Each phase has a `RetryPolicy` (default: 3 attempts, 2s backoff, 2x multiplier)
- On gate failure after max retries: phase status → FAILED, orchestrator triggers rollback
- Rollback restores `OrchestrationContext` to pre-phase snapshot

### Dynamic Re-planning
- If REQUIREMENTS output changes after DESIGN starts: orchestrator detects context drift,
  marks DESIGN as PENDING, re-runs entry gate evaluation
- Context drift detection: hash comparison of upstream artifact snapshots

### Observability
- Every state transition appended to `AuditEntry` list (runId, phase, action, actor, detail, timestamp)
- `OrchestrationMetrics` computed on run completion: per-phase latency, total duration, retry count, MTTR
- Streaming phase output via `Flux<String>` — client receives real-time agent reasoning

---

## Three Scenarios

### Scenario 1 — Greenfield
**Requirement:** "Build a URL shortener service with analytics."
- Orchestrator runs full SDLC graph from REQUIREMENTS through RELEASE_READINESS
- Claude interprets requirement, identifies ambiguities (custom codes? expiry? auth?)
- Produces: ADR, OpenAPI spec, implementation plan, test plan, README
- Human approval at DESIGN and RELEASE_READINESS gates
- Demonstrates: full pipeline, parallel paths, human checkpoints

### Scenario 2 — Brownfield
**Requirement:** "Add rate limiting to the existing URL shortener."
- Orchestrator skips REQUIREMENTS (requirement is clear) and GREENFIELD DESIGN
- Runs codebase impact analysis (DESIGN phase): identifies `ShortenUrlController`,
  `ShortenUrlService`, and need for `RateLimitPort`
- Implements: token-bucket rate limiter, `X-RateLimit-*` response headers, 429 handler
- Tests: unit tests for rate limiter, integration test for 429 response
- Demonstrates: non-linear execution (phase skipping), brownfield reasoning

### Scenario 3 — Ambiguous
**Requirement:** "Make it enterprise-ready."
- Orchestrator detects ambiguity in REQUIREMENTS phase via Claude analysis
- Generates clarifying questions, halts at ApprovalCheckpoint with question list
- Human provides answers (SAML SSO? Audit logs? SLA? Multi-tenancy?)
- Orchestrator dynamically re-plans based on answers: selects applicable sub-phases
- Demonstrates: ambiguity handling, dynamic re-planning, human-in-the-loop governance

---

## REST API Surface

### URL Shortener
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/urls` | Shorten a URL |
| GET | `/{code}` | Redirect to original (302) |
| GET | `/api/v1/urls/{code}` | Get ShortUrl details |
| DELETE | `/api/v1/urls/{code}` | Deactivate a short URL |
| GET | `/api/v1/urls/{code}/analytics` | Get analytics summary |

### Orchestration
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/orchestration/runs` | Start an orchestration run |
| GET | `/api/v1/orchestration/runs` | List all runs |
| GET | `/api/v1/orchestration/runs/{runId}` | Get run status + current phase |
| POST | `/api/v1/orchestration/runs/{runId}/approve` | Submit human approval |
| POST | `/api/v1/orchestration/runs/{runId}/stop` | Safe stop (sets STOPPED status) |
| GET | `/api/v1/orchestration/runs/{runId}/audit` | Full audit log |
| GET | `/api/v1/orchestration/runs/{runId}/metrics` | Reliability metrics |
| GET | `/api/v1/orchestration/runs/{runId}/stream` | SSE stream of agent output (Flux) |

---

## Step-by-Step Build Plan

### Day 1 — URL Shortener Foundation

#### Step 1.1 — Project Bootstrap (45 min)
**Tasks:**
- Generate Spring Boot 3.x project (Spring Web, Spring Data JPA, H2, Spring AI Anthropic, Lombok, Validation)
- Set `pom.xml`: Java 21, `spring-ai-anthropic-spring-boot-starter`, `spring-boot-starter-data-jpa`, H2
- Create package structure: `domain.model`, `domain.port.inbound`, `domain.port.outbound`, `domain.service`, `domain.exception`, `infrastructure.persistence`, `infrastructure.ai`, `infrastructure.orchestration`, `web.controller`, `web.dto`
- Create `application.yml`: H2 in-memory, `spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}`, `ddl-auto: create-drop`
- Create `.env.example` documenting required env vars

**Validation:** `mvn verify` passes with zero production code written (empty main class only).

---

#### Step 1.2 — URL Shortener Domain (1.5 hrs)
**Tasks:**
- Records: `ShortUrl`, `ClickEvent`, `AnalyticsSummary`, `ShortenRequest`, `ShortenResult`
- Exceptions: `UrlNotFoundException`, `CodeAlreadyExistsException`, `InvalidUrlException`
- Inbound ports: `ShortenUrlUseCase`, `ResolveUrlUseCase`, `TrackClickUseCase`, `GetAnalyticsUseCase`
- Outbound ports: `ShortUrlPort`, `ClickEventPort`, `CodeGeneratorPort`
- Domain service: `UrlShorteningService` (implements inbound ports, calls outbound ports)
  - URL validation (must start with http:// or https://)
  - Code generation: delegate to `CodeGeneratorPort`, retry up to 5x on collision
  - Expiry: default TTL 365 days if not specified

**Validation:** Unit tests — `UrlShorteningServiceTest` (12 tests: shorten, resolve, track, collision retry, invalid URL, expiry).

---

#### Step 1.3 — Persistence Layer (1.5 hrs)
**Tasks:**
- JPA entities: `ShortUrlEntity` (code PK, originalUrl VARCHAR(2048), createdAt, expiresAt, createdBy, active), `ClickEventEntity` (eventId UUID PK, code FK, clickedAt, userAgent, ipAddress, referrer)
- Repositories: `ShortUrlRepository`, `ClickEventRepository`
- Adapters: `ShortUrlAdapter` (implements `ShortUrlPort`), `ClickEventAdapter` (implements `ClickEventPort`)
- `AlphanumericCodeGenerator` (implements `CodeGeneratorPort`): SecureRandom, 6-char base62
- `data.sql`: no seed data needed

**Validation:** `@DataJpaTest` — `ShortUrlAdapterTest` (8 tests: save, findByCode, existsByCode, existsByOriginalUrl), `ClickEventAdapterTest` (5 tests).

---

#### Step 1.4 — Web Layer — URL Shortener (1 hr)
**Tasks:**
- DTOs: `ShortenRequestDto` (originalUrl, customCode, ttlDays), `ShortenResponseDto` (shortCode, shortLink, originalUrl, expiresAt), `AnalyticsSummaryDto`
- `UrlShortenerController`: POST `/api/v1/urls`, GET `/{code}` (302 redirect), GET `/api/v1/urls/{code}`, DELETE `/api/v1/urls/{code}`, GET `/api/v1/urls/{code}/analytics`
- `GlobalExceptionHandler`: `UrlNotFoundException` → 404, `CodeAlreadyExistsException` → 409, `InvalidUrlException` → 400

**Validation:** `@WebMvcTest` — `UrlShortenerControllerTest` (10 tests: shorten success, resolve redirect, 404, 409, 400, analytics).

---

### Day 2 — Agentic Orchestration Layer

#### Step 2.1 — Orchestration Domain Records (1.5 hrs)
**Tasks:**
- Enums: `SdlcPhase`, `PhaseStatus`, `ScenarioType`, `ApprovalStatus`
- Records: `OrchestrationRun`, `PhaseResult`, `AuditEntry`, `OrchestrationMetrics`, `ApprovalRequest`, `GateResult`, `RetryPolicy`, `AgentTask`, `AgentOutput`, `OrchestrationContext`
- `OrchestrationContext`: mutable context bag (Map<String, Object> artifacts, Map<SdlcPhase, String> artifactHashes for drift detection)
- Inbound ports: `ConductOrchestrationUseCase`, `QueryOrchestrationUseCase`
- Outbound ports: `OrchestrationRunPort`, `ApprovalPort`, `AgentExecutionPort`, `PhaseGatePort`
- Exceptions: `OrchestrationRunNotFoundException`, `ApprovalNotFoundException`, `PhaseGateFailedException`

**Validation:** Unit tests — `OrchestrationDomainTest` (15 tests: record construction, context drift detection, metrics calculation, phase status transitions).

---

#### Step 2.2 — Orchestration Engine (3 hrs)
**Tasks:**
- `DependencyGraph`: hardcoded DAG as `Map<SdlcPhase, List<SdlcPhase>>` (dependencies per phase), `getReadyPhases(completedPhases)` returns phases whose dependencies are all complete
- `PhaseExecutor`: executes a single phase — evaluates entry gate, calls `AgentExecutionPort`, evaluates exit gate, handles retry loop, triggers rollback on exhaustion
- `SdlcOrchestrationService` (implements `ConductOrchestrationUseCase`):
  - `startRun()`: creates `OrchestrationRun`, persists, submits to async executor
  - Main loop: `while not terminal` → `getReadyPhases()` → parallel submit to `PhaseExecutor` → await completion → check for approval gates → repeat
  - Parallel path sync: `CompletableFuture.allOf()` for phases that must converge (e.g., TESTING + DOCUMENTATION before RELEASE_READINESS)
  - Human approval: phase transitions to `AWAITING_APPROVAL`, main loop blocks on approval latch (30-min timeout → auto-fail)
  - Dynamic re-plan: after each phase, check artifact hashes for drift; if upstream changed, re-queue affected downstream phases
  - Safe stop: `stopRun()` sets a volatile flag; PhaseExecutor checks flag between retry attempts
- `OrchestrationMetricsCalculator`: pure domain service, computes metrics from `OrchestrationRun`

**Validation:** Unit tests — `SdlcOrchestrationServiceTest` (12 tests: full run happy path with mocked agent, approval checkpoint blocking, retry exhaustion → rollback, safe stop, drift re-plan).

---

#### Step 2.3 — Persistence — Orchestration (1 hr)
**Tasks:**
- JPA entities: `OrchestrationRunEntity` (runId UUID PK, scenarioType, status, startedAt, completedAt), `AuditEntryEntity` (entryId UUID PK, runId FK, phase, action, actor, detail, occurredAt), `ApprovalRequestEntity` (requestId UUID PK, runId FK, phase, question, approved, respondedAt)
- Repositories: `OrchestrationRunRepository`, `AuditEntryRepository`, `ApprovalRequestRepository`
- Adapters: `OrchestrationRunAdapter`, `ApprovalAdapter`

**Validation:** `@DataJpaTest` — `OrchestrationRunAdapterTest` (8 tests), `ApprovalAdapterTest` (5 tests).

---

#### Step 2.4 — AI Adapter + Streaming (1.5 hrs)
**Tasks:**
- `ClaudeAgentAdapter` (implements `AgentExecutionPort`):
  - Injects Spring AI `ChatClient`
  - `execute(AgentTask)`: builds prompt with phase context, calls Claude, parses `AgentOutput`
  - `executeStreaming(AgentTask)` → `Flux<String>`: uses `ChatClient.stream()`, maps `ChatResponse` to token strings
  - Phase-specific system prompts: each `SdlcPhase` has a prompt template in `src/main/resources/prompts/`
- Prompt templates (one per phase):
  - `phase-requirements.txt` — requirement analysis, ambiguity detection, ADR generation
  - `phase-design.txt` — component design, API schema, architecture decisions
  - `phase-implementation.txt` — code generation guidance, implementation plan
  - `phase-testing.txt` — test plan, coverage targets, test case generation
  - `phase-documentation.txt` — README, API docs, setup instructions
  - `phase-release-readiness.txt` — release checklist, risk assessment, go/no-go criteria
- `ClaudePhaseGateAdapter` (implements `PhaseGatePort`):
  - Entry gates: validates required artifacts exist in context
  - Exit gates: calls Claude to evaluate output quality against phase criteria

**Validation:** Unit tests — `ClaudeAgentAdapterTest` (8 tests with mocked `ChatClient`): execute, streaming, prompt building per phase, null safety.

---

### Day 3 — Scenarios, REST, Documentation

#### Step 3.1 — Orchestration Web Layer (1 hr)
**Tasks:**
- DTOs: `StartRunRequestDto` (scenarioType, scenarioDescription), `OrchestrationRunDto` (runId, status, currentPhase, startedAt), `ApprovalResponseDto` (approved, respondedBy), `AuditEntryDto`, `MetricsDto`
- `OrchestrationController`:
  - POST `/api/v1/orchestration/runs` → starts run async, returns 202 with runId
  - GET `/api/v1/orchestration/runs/{runId}` → current status
  - POST `/api/v1/orchestration/runs/{runId}/approve` → submit approval
  - POST `/api/v1/orchestration/runs/{runId}/stop` → safe stop
  - GET `/api/v1/orchestration/runs/{runId}/audit` → full audit log
  - GET `/api/v1/orchestration/runs/{runId}/metrics` → metrics
  - GET `/api/v1/orchestration/runs/{runId}/stream` → SSE `Flux<String>` streaming agent output

**Validation:** `@WebMvcTest` — `OrchestrationControllerTest` (10 tests).

---

#### Step 3.2 — Three Scenario Integration Tests (1.5 hrs)
**Tasks:**
- `GreenfieldScenarioTest`: starts run with GREENFIELD type, mocked Claude returns structured artifacts, asserts full phase graph completes, audit log contains all phase transitions, metrics computed
- `BrownfieldScenarioTest`: starts run with BROWNFIELD type, asserts REQUIREMENTS skipped, DESIGN runs codebase impact analysis, rate limiting implementation plan produced
- `AmbiguousScenarioTest`: starts run with AMBIGUOUS type, asserts run halts at ApprovalCheckpoint with clarifying questions, resumes after simulated approval, re-plans based on answers

**Validation:** All three scenario tests pass with mocked `AgentExecutionPort`.

---

#### Step 3.3 — Documentation (1 hr)
**Tasks:**
- `README.md`: project overview, setup instructions, how to run, environment variables, how to trigger each scenario via REST
- `ARCHITECTURE.md`: component diagram, orchestration model, control flow, key decisions (why hexagonal, why blocking approval latch vs. polling, why dependency graph over linear chain)
- `SCENARIOS.md`: detailed walkthrough of each scenario — requirement input, decomposition steps, orchestration trace, validation results, trade-offs
- `TRADE-OFFS.md`: streaming vs. non-streaming, in-memory vs. persistent approval, single-instance vs. distributed orchestration, H2 vs. Postgres
- `ENGINEERING-SUMMARY.md`: plan/rationale, artifacts produced, risks, assumptions, limitations

---

#### Step 3.4 — Final Validation (30 min)
**Checklist:**
- [ ] `mvn verify` passes (all unit + integration tests green)
- [ ] App starts with `mvn spring-boot:run` (requires `ANTHROPIC_API_KEY` in env)
- [ ] POST `/api/v1/urls` returns a working short link
- [ ] GET `/{code}` redirects correctly
- [ ] POST `/api/v1/orchestration/runs` with GREENFIELD starts a run
- [ ] GET `/api/v1/orchestration/runs/{runId}` returns phase status
- [ ] GET `/api/v1/orchestration/runs/{runId}/stream` streams agent output in browser/curl
- [ ] Approval checkpoint blocks until POST approve is called
- [ ] All three scenario tests pass
- [ ] All documentation files present and complete

---

## Key Design Decisions (Pre-committed)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Stack | Spring Boot 3.x + Java 21 | Matches candidate's expertise; production-grade |
| Architecture | Hexagonal ports-and-adapters | Testability, swappability, same pattern as production work |
| Persistence | H2 in-memory | Assignment scope; swap to Postgres via single yml change |
| Orchestration state | DB-backed (JPA) | Survives restart; audit log is append-only |
| Approval mechanism | Blocking latch + REST poll | Simple, testable, no WebSocket complexity |
| Parallel phases | CompletableFuture.allOf() | Standard JDK; no external scheduler needed |
| Streaming | Spring AI Flux + SSE | Demonstrates real agentic streaming; evaluator can watch Claude think |
| Re-planning trigger | Artifact hash comparison | Deterministic drift detection; no LLM needed for the check itself |
| Rate limiting (brownfield) | Token bucket in domain service | No framework dependency in domain |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Claude API latency in tests | Mock `AgentExecutionPort` in all unit/integration tests; real calls only in `@Profile("ai-integration")` |
| Approval latch timeout in tests | Default timeout configurable via `application.yml`; tests set it to 0 (immediate timeout) |
| Orchestration engine complexity | Start with linear execution, add parallel paths after linear is green |
| H2 dialect quirks | Use ANSI SQL in JPA queries; avoid H2-specific functions |
| Streaming SSE in MockMvc | Use `WebTestClient` for SSE endpoint tests |

---

## Environment Variables Required

```bash
ANTHROPIC_API_KEY=sk-ant-...   # Required for real Claude calls
```

All other config has safe defaults in `application.yml`.

---

## File Count Estimate

| Layer | Files |
|-------|-------|
| Domain records/enums | ~15 |
| Port interfaces | ~10 |
| Domain services | ~4 |
| JPA entities + repos | ~8 |
| Adapters | ~8 |
| Web controllers + DTOs | ~6 |
| Prompt templates | ~6 |
| Test classes | ~20 |
| Documentation | ~5 |
| **Total** | **~82** |
