package com.wgblackmon.urlshortener.infrastructure.persistence;

import com.wgblackmon.urlshortener.domain.model.ApprovalRequest;
import com.wgblackmon.urlshortener.domain.model.ApprovalStatus;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;
import com.wgblackmon.urlshortener.domain.port.outbound.ApprovalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing {@link ApprovalPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class ApprovalAdapter implements ApprovalPort {

    private final ApprovalRequestRepository repository;

    public ApprovalAdapter(ApprovalRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        log.debug("save() | requestId={}", request.requestId());
        repository.save(toEntity(request));
        log.debug("save() | return={}", request.requestId());
        return request;
    }

    @Override
    public Optional<ApprovalRequest> findById(String requestId) {
        log.debug("findById() | requestId={}", requestId);
        Optional<ApprovalRequest> result = repository.findById(requestId).map(this::toDomain);
        log.debug("findById() | return={}", result.map(r -> r.status().name()).orElse("empty"));
        return result;
    }

    @Override
    public List<ApprovalRequest> findByRunId(String runId) {
        log.debug("findByRunId() | runId={}", runId);
        List<ApprovalRequest> result = repository.findByRunId(runId).stream().map(this::toDomain).toList();
        log.debug("findByRunId() | return={} requests", result.size());
        return result;
    }

    @Override
    public List<ApprovalRequest> findPendingByRunId(String runId) {
        log.debug("findPendingByRunId() | runId={}", runId);
        List<ApprovalRequest> result = repository.findByRunIdAndStatus(runId, ApprovalStatus.PENDING.name())
                .stream().map(this::toDomain).toList();
        log.debug("findPendingByRunId() | return={} pending", result.size());
        return result;
    }

    @Override
    public void resolve(String requestId, ApprovalStatus status, String respondedBy, Instant respondedAt) {
        log.debug("resolve() | requestId={}, status={}", requestId, status);
        repository.findById(requestId).ifPresent(entity -> {
            entity.setStatus(status.name());
            entity.setRespondedBy(respondedBy);
            entity.setRespondedAt(respondedAt);
            repository.save(entity);
        });
        log.debug("resolve() | return=void");
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    private ApprovalRequestEntity toEntity(ApprovalRequest r) {
        return new ApprovalRequestEntity(
                r.requestId(), r.runId(),
                r.phase().name(), r.question(), r.status().name(),
                r.requestedAt(), r.respondedBy(), r.respondedAt()
        );
    }

    private ApprovalRequest toDomain(ApprovalRequestEntity e) {
        return new ApprovalRequest(
                e.getRequestId(), e.getRunId(),
                SdlcPhase.valueOf(e.getPhase()), e.getQuestion(),
                ApprovalStatus.valueOf(e.getStatus()), e.getRequestedAt(),
                e.getRespondedBy(), e.getRespondedAt()
        );
    }
}
