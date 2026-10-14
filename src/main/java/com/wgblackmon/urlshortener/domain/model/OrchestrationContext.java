package com.wgblackmon.urlshortener.domain.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable in-memory execution context for a single orchestration run.
 *
 * <h2>Why mutable when OrchestrationRun is immutable?</h2>
 * <p>OrchestrationRun is the durable, queryable view of a run assembled by the
 * persistence adapter. OrchestrationContext is the live execution state held
 * in memory by the orchestration engine thread during a single run. Mutability
 * here is intentional and safe because:
 * <ol>
 *   <li>Only one execution thread owns this context at a time.</li>
 *   <li>It is never shared with REST controllers — controllers query the DB via
 *       {@code OrchestrationRunPort}, not this in-memory object.</li>
 *   <li>Artifacts are flushed to the DB (as part of PhaseResult) after each phase,
 *       so a JVM crash loses at most one phase of work, not the entire run.</li>
 * </ol>
 *
 * <h2>Artifact hash drift detection</h2>
 * <p>When the DESIGN phase produces an openapi.yaml artifact, a SHA-256 hash of
 * that content is stored in {@code artifactHashes}. Before IMPLEMENTATION begins,
 * the orchestrator recomputes the hash and compares it with the stored value. If
 * the content was modified externally (e.g., a human edited the spec file while
 * the orchestrator was awaiting approval), the hash will differ and the engine
 * raises a warning before proceeding. This implements the assignment's requirement
 * for "self-correcting feedback loops" — the system detects when its assumptions
 * have been invalidated and responds accordingly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public class OrchestrationContext {

    private final String runId;
    private final ScenarioType scenarioType;

    /** Artifact key → artifact content. Keys are e.g. "REQUIREMENTS_DOC", "OPENAPI_SPEC". */
    private final Map<String, String> artifacts = new HashMap<>();

    /**
     * Artifact key → SHA-256 hex hash of content at time of storage.
     * Used to detect external modification between phases.
     */
    private final Map<String, String> artifactHashes = new HashMap<>();

    public OrchestrationContext(String runId, ScenarioType scenarioType) {
        if (runId == null || runId.isBlank())  throw new IllegalArgumentException("runId must not be blank");
        if (scenarioType == null)              throw new IllegalArgumentException("scenarioType must not be null");
        this.runId        = runId;
        this.scenarioType = scenarioType;
    }

    /** Stores an artifact and records its hash for drift detection. */
    public void storeArtifact(String key, String content, String sha256Hash) {
        if (key == null || key.isBlank())      throw new IllegalArgumentException("key must not be blank");
        if (content == null)                   throw new IllegalArgumentException("content must not be null");
        if (sha256Hash == null)                throw new IllegalArgumentException("sha256Hash must not be null");
        artifacts.put(key, content);
        artifactHashes.put(key, sha256Hash);
    }

    /**
     * Returns true if the supplied hash differs from the stored hash for this key.
     * Returns false if the key is unknown (no drift is possible for unseen artifacts).
     */
    public boolean hasArtifactDrifted(String key, String currentHash) {
        String storedHash = artifactHashes.get(key);
        if (storedHash == null) return false;
        return !storedHash.equals(currentHash);
    }

    /** Returns the stored artifact content, or null if not present. */
    public String getArtifact(String key) {
        return artifacts.get(key);
    }

    /** Returns an unmodifiable view of all artifact keys currently stored. */
    public Map<String, String> getArtifacts() {
        return Collections.unmodifiableMap(artifacts);
    }

    public String getRunId()          { return runId; }
    public ScenarioType getScenarioType() { return scenarioType; }
}
