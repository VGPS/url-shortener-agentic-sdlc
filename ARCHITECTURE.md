# Architecture

## Overview

This project uses **Hexagonal (Ports-and-Adapters) Architecture**. The domain layer contains all business logic and has zero dependencies on any framework (Spring, Lombok, JPA). Infrastructure adapters implement domain port interfaces and are free to use any technology.

```
┌─────────────────────────────────────────────────────────────────────┐
│                              web layer                              │
│   UrlShortenerController  RedirectController  OrchestrationController│
└──────────────────────────────┬──────────────────────────────────────┘
                               │ inbound ports (interfaces)
┌──────────────────────────────▼──────────────────────────────────────┐
│                            domain                                   │
│  UrlShorteningService          SdlcOrchestrationService             │
│  ┌─────────────────────────┐   ┌────────────────────────────────┐   │
│  │ ShortenUrlUseCase       │   │ ConductOrchestrationUseCase    │   │
│  │ ResolveUrlUseCase       │   │ QueryOrchestrationUseCase      │   │
│  │ TrackClickUseCase       │   └────────────────────────────────┘   │
│  │ GetAnalyticsUseCase     │                                         │
│  └─────────────────────────┘   ┌────────────────────────────────┐   │
│                                │ DependencyGraph                 │   │
│  ┌─────────────────────────┐   │ PhaseExecutor                  │   │
│  │ ShortUrlPort            │   │ PromptBuilder                  │   │
│  │ ClickEventPort          │   └────────────────────────────────┘   │
│  │ CodeGeneratorPort       │                                         │
│  └─────────────────────────┘   ┌────────────────────────────────┐   │
│                                │ OrchestrationRunPort           │   │
│                                │ ApprovalPort                   │   │
│                                │ AgentExecutionPort             │   │
│                                │ PhaseGatePort                  │   │
│                                └────────────────────────────────┘   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ outbound ports (interfaces)
┌──────────────────────────────▼──────────────────────────────────────┐
│                         infrastructure                              │
│  ShortUrlAdapter         ClaudeAgentAdapter                         │
│  ClickEventAdapter       ClaudePhaseGateAdapter                     │
│  AlphanumericCodeGen.    OrchestrationRunAdapter                    │
│                          ApprovalAdapter                            │
└─────────────────────────────────────────────────────────────────────┘
```

## Dependency Rules

- **Domain** → JDK only. Zero Spring, Lombok, JPA, or AI imports.
- **Infrastructure** → implements domain ports; may use Spring, Spring AI, JPA, Jackson.
- **Web** → calls inbound ports; uses Spring MVC. Never imports infrastructure directly.
- **Domain checks every class header** — `System.Logger` (not `@Slf4j`) for logging in domain services; `@Slf4j` in infrastructure and web.

## Orchestration Engine Architecture

```
SdlcOrchestrationService
        │
        ├── DependencyGraph          (which phases depend on which)
        │         │
        │   GREENFIELD:  REQ → DESIGN → {IMPL ∥ DOCS} → RELEASE → TEST
        │   BROWNFIELD:  skip REQ;  DESIGN is entry point
        │   AMBIGUOUS:   REQ → human approval checkpoint → DESIGN → ...
        │
        ├── PhaseExecutor            (one phase: execute → gate → retry/fail)
        │         │
        │         ├── AgentExecutionPort → ClaudeAgentAdapter
        │         │         └── Spring AI ChatClient (blocking or streaming)
        │         │
        │         └── PhaseGatePort  → ClaudePhaseGateAdapter
        │                   └── LLM gate evaluation with remediation hints
        │
        ├── OrchestrationRunPort  → OrchestrationRunAdapter (H2 / Postgres)
        └── ApprovalPort         → ApprovalAdapter         (H2 / Postgres)
```

## Streaming Architecture

```
Client (EventSource)
        │
   GET /orchestration/{id}/stream
        │
   OrchestrationController.streamRun()
        │ registers SseEmitter in activeEmitters map
        │
   (async execution thread)
        │
   SdlcOrchestrationService.executeRun()
        │
   PhaseExecutor.execute(task, tokenConsumer)
        │  tokenConsumer = (token) -> controller.pushToken(runId, token)
        │
   AgentExecutionPort.execute(task, tokenConsumer)
        │
   ClaudeAgentAdapter.executeStreaming()
        │  Spring AI ChatClient.stream().content()
        │  .doOnNext(token -> tokenConsumer.accept(token))  ← domain callback
        │
   tokenConsumer invoked per token
        │
   controller.pushToken() → emitter.send(event().name("token").data(token))
        │
   Client receives each token as it arrives
```

The critical design: `Consumer<String>` in the domain port keeps the domain framework-free. The web layer wraps it with SSE; the domain layer never knows SSE exists.

## Database Schema (H2 / auto-created by JPA)

```sql
-- URL Shortener
short_urls      (code PK, original_url, created_at, expires_at, created_by, active)
click_events    (event_id PK, code, clicked_at, user_agent, ip_address, referrer)

-- Orchestration
orchestration_runs   (run_id PK, scenario_type, scenario_description, status,
                      audit_entries_json TEXT, phase_results_json TEXT,
                      metrics_json TEXT, started_at, completed_at)
approval_requests    (request_id PK, run_id, phase, question, status,
                      requested_at, responded_by, responded_at)
```

Phase results and audit entries are stored as JSON TEXT columns. This avoids complex JPA relationships and keeps the read model simple — the entire run is loaded in one query.

## Retry + Gate Loop

```
execute(phase)
    │
    └── for attempt in 0..maxRetries:
            │
            ├── call AgentExecutionPort (LLM call, possibly streaming)
            │
            ├── update status → AWAITING_GATE
            │
            ├── call PhaseGatePort (second LLM call: evaluate quality)
            │         │
            │         ├── PASS → mark SUCCEEDED, store PhaseResult, return
            │         │
            │         └── FAIL → extract remediation hint from gate response
            │
            ├── append audit: GATE_FAILED + remediation
            └── sleep(backoff * multiplier^attempt)
    │
    └── retries exhausted → mark FAILED, throw PhaseGateFailedException
                │
         SdlcOrchestrationService.performRollback()
                │ walks backward through completed phases
                └── marks each ROLLED_BACK + appends audit entry
```
