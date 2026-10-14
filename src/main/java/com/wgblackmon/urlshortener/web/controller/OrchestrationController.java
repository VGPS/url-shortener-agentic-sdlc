package com.wgblackmon.urlshortener.web.controller;

import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.inbound.ConductOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.QueryOrchestrationUseCase;
import com.wgblackmon.urlshortener.web.dto.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for the SDLC orchestration API.
 *
 * <h2>SSE streaming endpoint design</h2>
 * <p>The {@code GET /api/v1/orchestration/{runId}/stream} endpoint delivers real-time
 * agent output tokens via Server-Sent Events. When a client connects:
 * <ol>
 *   <li>An {@link SseEmitter} is registered in the {@code activeEmitters} map under the runId.</li>
 *   <li>A new orchestration run is started (or the caller references an existing run).</li>
 *   <li>The {@link com.wgblackmon.urlshortener.domain.port.outbound.AgentExecutionPort} invokes
 *       the token consumer for each streamed token — the consumer calls {@code emitter.send()}.</li>
 *   <li>When the run completes, the emitter is completed and removed from the map.</li>
 * </ol>
 * <p>The streaming endpoint is kept separate from the start endpoint so non-streaming
 * clients (e.g., CI pipelines polling status) don't need to hold an SSE connection open.
 *
 * <h2>REST surface</h2>
 * <pre>
 * POST /api/v1/orchestration              — start a run, returns 202 + runId
 * GET  /api/v1/orchestration/{runId}      — poll run status
 * GET  /api/v1/orchestration              — list all runs
 * POST /api/v1/orchestration/{runId}/approve/{requestId}  — respond to approval checkpoint
 * GET  /api/v1/orchestration/{runId}/approvals            — list pending approvals for a run
 * GET  /api/v1/orchestration/{runId}/metrics              — reliability metrics (completed runs only)
 * GET  /api/v1/orchestration/{runId}/stream               — SSE stream of agent output tokens
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orchestration")
public class OrchestrationController {

    private final ConductOrchestrationUseCase conductor;
    private final QueryOrchestrationUseCase   querier;

    /** Active SSE emitters keyed by runId. Token consumers from the AI adapter write here. */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    public OrchestrationController(ConductOrchestrationUseCase conductor,
                                    QueryOrchestrationUseCase querier) {
        this.conductor = conductor;
        this.querier   = querier;
    }

    /**
     * Starts a new orchestration run asynchronously.
     * Returns 202 Accepted with a Location header pointing to the status endpoint.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> startRun(
            @RequestBody @Valid StartOrchestrationRequest request) {
        log.debug("startRun() | scenarioType={}", request.scenarioType());
        ScenarioType scenarioType = ScenarioType.valueOf(request.scenarioType().toUpperCase());
        String runId = conductor.startRun(request.scenarioDescription(), scenarioType);
        log.debug("startRun() | return={}", runId);
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/orchestration/" + runId))
                .body(Map.of("runId", runId));
    }

    /** Returns the current state of a run. */
    @GetMapping("/{runId}")
    public ResponseEntity<OrchestrationRunResponse> getRun(@PathVariable String runId) {
        log.debug("getRun() | runId={}", runId);
        OrchestrationRun run = querier.getRun(runId);
        OrchestrationRunResponse result = toResponse(run);
        log.debug("getRun() | return={}", result.status());
        return ResponseEntity.ok(result);
    }

    /** Lists all orchestration runs, newest first. */
    @GetMapping
    public ResponseEntity<List<OrchestrationRunResponse>> getAllRuns() {
        log.debug("getAllRuns()");
        List<OrchestrationRunResponse> result = querier.getAllRuns().stream().map(this::toResponse).toList();
        log.debug("getAllRuns() | return={} runs", result.size());
        return ResponseEntity.ok(result);
    }

    /** Records a human approval or rejection for a pending checkpoint. */
    @PostMapping("/{runId}/approve/{requestId}")
    public ResponseEntity<OrchestrationRunResponse> respondToApproval(
            @PathVariable String runId,
            @PathVariable String requestId,
            @RequestBody @Valid RespondApprovalRequest request) {
        log.debug("respondToApproval() | runId={}, requestId={}, approved={}", runId, requestId, request.approved());
        OrchestrationRun run = conductor.respondToApproval(runId, requestId, request.approved(), request.respondedBy());
        OrchestrationRunResponse result = toResponse(run);
        log.debug("respondToApproval() | return={}", result.status());
        return ResponseEntity.ok(result);
    }

    /** Returns pending approval requests for a run. */
    @GetMapping("/{runId}/approvals")
    public ResponseEntity<List<ApprovalResponse>> getPendingApprovals(@PathVariable String runId) {
        log.debug("getPendingApprovals() | runId={}", runId);
        List<ApprovalResponse> result = querier.getPendingApprovals(runId).stream()
                .map(this::toApprovalResponse).toList();
        log.debug("getPendingApprovals() | return={} pending", result.size());
        return ResponseEntity.ok(result);
    }

    /** Returns reliability metrics for a completed run. */
    @GetMapping("/{runId}/metrics")
    public ResponseEntity<MetricsResponse> getMetrics(@PathVariable String runId) {
        log.debug("getMetrics() | runId={}", runId);
        OrchestrationMetrics metrics = querier.getMetrics(runId);
        MetricsResponse result = new MetricsResponse(
                metrics.runId(), metrics.totalDurationMs(),
                metrics.phaseLatenciesMs().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)),
                metrics.successRate(), metrics.totalRetries(),
                metrics.rollbackCount(), metrics.mttrMs()
        );
        log.debug("getMetrics() | return={}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * SSE endpoint that streams agent output tokens in real time.
     *
     * <h2>How streaming works end-to-end</h2>
     * <ol>
     *   <li>Client connects to this endpoint and keeps the connection open.</li>
     *   <li>An {@link SseEmitter} (timeout=10min) is registered for this runId.</li>
     *   <li>When the orchestration engine calls {@code AgentExecutionPort.execute()} with
     *       a streaming task, it passes a {@code Consumer<String>} that writes tokens
     *       to this emitter via {@code emitter.send()}.</li>
     *   <li>When the run completes or fails, the emitter is completed.</li>
     * </ol>
     *
     * <p><b>Note:</b> For a complete implementation, the runId would be used to look up
     * an in-progress run and inject the emitter's consumer into the execution thread.
     * In this simplified version, the emitter is registered and the client can poll
     * for tokens that are pushed as they arrive.
     */
    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String runId) {
        log.debug("streamRun() | runId={}", runId);
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);  // 10-minute timeout
        activeEmitters.put(runId, emitter);

        emitter.onCompletion(() -> {
            activeEmitters.remove(runId);
            log.debug("streamRun() | SSE completed for runId={}", runId);
        });
        emitter.onTimeout(() -> {
            activeEmitters.remove(runId);
            log.debug("streamRun() | SSE timed out for runId={}", runId);
        });

        // Send an initial event so the client knows the stream is open
        try {
            emitter.send(SseEmitter.event().name("connected").data("run:" + runId));
        } catch (IOException e) {
            log.warn("streamRun() | failed to send initial event for runId={}", runId);
            emitter.completeWithError(e);
        }

        log.debug("streamRun() | return=SseEmitter(runId={})", runId);
        return emitter;
    }

    /**
     * Called by the token consumer (from the AI adapter execution thread) to push
     * a token to any active SSE client listening on this runId.
     */
    public void pushToken(String runId, String token) {
        SseEmitter emitter = activeEmitters.get(runId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException e) {
            log.warn("pushToken() | SSE send failed for runId={}, removing emitter", runId);
            activeEmitters.remove(runId);
        }
    }

    // ── Response mapping helpers ──────────────────────────────────────────

    private OrchestrationRunResponse toResponse(OrchestrationRun run) {
        Map<String, String> phaseStatuses = run.phaseResults().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), e -> e.getValue().status().name()));
        return new OrchestrationRunResponse(
                run.runId(), run.scenarioType().name(), run.status().name(),
                phaseStatuses, run.auditEntries().size(), run.startedAt(), run.completedAt()
        );
    }

    private ApprovalResponse toApprovalResponse(ApprovalRequest req) {
        return new ApprovalResponse(
                req.requestId(), req.runId(), req.phase().name(), req.question(),
                req.status().name(), req.requestedAt(), req.respondedBy()
        );
    }
}
