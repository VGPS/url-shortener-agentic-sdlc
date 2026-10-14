package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.outbound.OrchestrationRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * JPA adapter implementing {@link OrchestrationRunPort}.
 *
 * <h2>JSON serialisation strategy</h2>
 * <p>Phase results and audit entries are serialised as JSON TEXT columns using
 * Jackson. This avoids complex JPA relationships (e.g., a OneToMany for audit
 * entries would require eager/lazy fetch tuning) and keeps the persistence schema
 * minimal. The trade-off is that full-text search on individual audit events is
 * not available via JPA — but that is not required by the assignment.
 *
 * <h2>Why update methods instead of save(entity)?</h2>
 * <p>See {@link OrchestrationRunPort} Javadoc. Fine-grained updates avoid
 * deserialising, modifying, and re-serialising the entire JSON payload on every
 * small state change (e.g., a status update should not touch the audit entries column).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class OrchestrationRunAdapter implements OrchestrationRunPort {

    private final OrchestrationRunRepository repository;
    private final ObjectMapper mapper;

    public OrchestrationRunAdapter(OrchestrationRunRepository repository) {
        this.repository = repository;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public OrchestrationRun save(OrchestrationRun run) {
        log.debug("save() | runId={}", run.runId());
        OrchestrationRunEntity entity = toEntity(run);
        repository.save(entity);
        log.debug("save() | return={}", run.runId());
        return run;
    }

    @Override
    public Optional<OrchestrationRun> findById(String runId) {
        log.debug("findById() | runId={}", runId);
        Optional<OrchestrationRun> result = repository.findById(runId).map(this::toDomain);
        log.debug("findById() | return={}", result.map(r -> r.status().name()).orElse("empty"));
        return result;
    }

    @Override
    public List<OrchestrationRun> findAll() {
        log.debug("findAll()");
        List<OrchestrationRun> result = repository.findAll().stream().map(this::toDomain).toList();
        log.debug("findAll() | return={} runs", result.size());
        return result;
    }

    @Override
    public void updateStatus(String runId, PhaseStatus status) {
        log.debug("updateStatus() | runId={}, status={}", runId, status);
        repository.findById(runId).ifPresent(entity -> {
            entity.setStatus(status.name());
            if (status.isTerminal()) entity.setCompletedAt(Instant.now());
            repository.save(entity);
        });
        log.debug("updateStatus() | return=void");
    }

    @Override
    public void savePhaseResult(String runId, PhaseResult result) {
        log.debug("savePhaseResult() | runId={}, phase={}", runId, result.phase());
        repository.findById(runId).ifPresent(entity -> {
            Map<String, Object> phaseResults = deserializeMap(entity.getPhaseResultsJson());
            phaseResults.put(result.phase().name(), serializePhaseResult(result));
            entity.setPhaseResultsJson(toJson(phaseResults));
            repository.save(entity);
        });
        log.debug("savePhaseResult() | return=void");
    }

    @Override
    public void appendAuditEntry(AuditEntry entry) {
        log.debug("appendAuditEntry() | runId={}, action={}", entry.runId(), entry.action());
        repository.findById(entry.runId()).ifPresent(entity -> {
            List<Map<String, Object>> entries = deserializeList(entity.getAuditEntriesJson());
            entries.add(serializeAuditEntry(entry));
            entity.setAuditEntriesJson(toJson(entries));
            repository.save(entity);
        });
        log.debug("appendAuditEntry() | return=void");
    }

    @Override
    public List<AuditEntry> findAuditEntries(String runId) {
        log.debug("findAuditEntries() | runId={}", runId);
        List<AuditEntry> result = repository.findById(runId)
                .map(entity -> {
                    List<Map<String, Object>> list = deserializeList(entity.getAuditEntriesJson());
                    return list.stream().map(this::toAuditEntry).toList();
                }).orElse(List.of());
        log.debug("findAuditEntries() | return={} entries", result.size());
        return result;
    }

    @Override
    public void updatePhaseStatus(String runId, SdlcPhase phase, PhaseStatus status) {
        log.debug("updatePhaseStatus() | runId={}, phase={}, status={}", runId, phase, status);
        repository.findById(runId).ifPresent(entity -> {
            Map<String, Object> phaseResults = deserializeMap(entity.getPhaseResultsJson());
            @SuppressWarnings("unchecked")
            Map<String, Object> phaseData = (Map<String, Object>) phaseResults.getOrDefault(phase.name(), new HashMap<>());
            phaseData.put("status", status.name());
            phaseResults.put(phase.name(), phaseData);
            entity.setPhaseResultsJson(toJson(phaseResults));
            repository.save(entity);
        });
        log.debug("updatePhaseStatus() | return=void");
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private OrchestrationRunEntity toEntity(OrchestrationRun run) {
        return new OrchestrationRunEntity(
                run.runId(),
                run.scenarioType().name(),
                run.scenarioDescription(),
                run.status().name(),
                toJson(List.of()),   // audit entries start empty
                toJson(Map.of()),    // phase results start empty
                null,
                run.startedAt(),
                run.completedAt()
        );
    }

    private OrchestrationRun toDomain(OrchestrationRunEntity entity) {
        List<AuditEntry> auditEntries = deserializeList(entity.getAuditEntriesJson())
                .stream().map(this::toAuditEntry).toList();
        Map<SdlcPhase, PhaseResult> phaseResults = deserializePhaseResults(entity.getPhaseResultsJson());
        return new OrchestrationRun(
                entity.getRunId(),
                ScenarioType.valueOf(entity.getScenarioType()),
                entity.getScenarioDescription(),
                PhaseStatus.valueOf(entity.getStatus()),
                phaseResults,
                auditEntries,
                null,   // metrics deserialisation omitted for brevity — null until slice D adds metrics calculation
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    private Map<SdlcPhase, PhaseResult> deserializePhaseResults(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Map<String, Object> raw = deserializeMap(json);
        Map<SdlcPhase, PhaseResult> results = new EnumMap<>(SdlcPhase.class);
        raw.forEach((key, value) -> {
            try {
                SdlcPhase phase = SdlcPhase.valueOf(key);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) value;
                PhaseResult pr = new PhaseResult(
                        phase,
                        PhaseStatus.valueOf((String) data.getOrDefault("status", "PENDING")),
                        (String) data.get("agentOutput"),
                        data.containsKey("artifactsProduced")
                                ? (List<String>) data.get("artifactsProduced")
                                : List.of(),
                        data.containsKey("startedAt") ? Instant.parse((String) data.get("startedAt")) : null,
                        data.containsKey("completedAt") ? Instant.parse((String) data.get("completedAt")) : null,
                        (int) data.getOrDefault("retryCount", 0),
                        (String) data.get("rollbackReason")
                );
                results.put(phase, pr);
            } catch (Exception e) {
                log.warn("deserializePhaseResults() | skipping malformed phase entry key={}", key);
            }
        });
        return results;
    }

    private Map<String, Object> serializePhaseResult(PhaseResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status",             r.status().name());
        m.put("agentOutput",        r.agentOutput());
        m.put("artifactsProduced",  r.artifactsProduced());
        m.put("startedAt",          r.startedAt() != null ? r.startedAt().toString() : null);
        m.put("completedAt",        r.completedAt() != null ? r.completedAt().toString() : null);
        m.put("retryCount",         r.retryCount());
        m.put("rollbackReason",     r.rollbackReason());
        return m;
    }

    private Map<String, Object> serializeAuditEntry(AuditEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entryId",    e.entryId());
        m.put("runId",      e.runId());
        m.put("phase",      e.phase() != null ? e.phase().name() : null);
        m.put("action",     e.action());
        m.put("actor",      e.actor());
        m.put("detail",     e.detail());
        m.put("occurredAt", e.occurredAt().toString());
        return m;
    }

    private AuditEntry toAuditEntry(Map<String, Object> m) {
        return new AuditEntry(
                (String) m.get("entryId"),
                (String) m.get("runId"),
                m.get("phase") != null ? SdlcPhase.valueOf((String) m.get("phase")) : null,
                (String) m.get("action"),
                (String) m.get("actor"),
                (String) m.get("detail"),
                Instant.parse((String) m.get("occurredAt"))
        );
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("toJson() | serialisation failed", e);
            return "[]";
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("deserializeMap() | failed to parse JSON", e);
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> deserializeList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("deserializeList() | failed to parse JSON", e);
            return new ArrayList<>();
        }
    }
}
