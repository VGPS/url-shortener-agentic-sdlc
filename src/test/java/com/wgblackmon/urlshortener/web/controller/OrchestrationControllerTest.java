package com.wgblackmon.urlshortener.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.urlshortener.domain.exception.OrchestrationRunNotFoundException;
import com.wgblackmon.urlshortener.domain.model.*;
import com.wgblackmon.urlshortener.domain.port.inbound.ConductOrchestrationUseCase;
import com.wgblackmon.urlshortener.domain.port.inbound.QueryOrchestrationUseCase;
import com.wgblackmon.urlshortener.web.dto.StartOrchestrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for OrchestrationController.
 * Mocks both inbound use-case ports — no AI, no DB.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@WebMvcTest(OrchestrationController.class)
@Import(GlobalExceptionHandler.class)
class OrchestrationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  ConductOrchestrationUseCase conductor;
    @MockBean  QueryOrchestrationUseCase   querier;

    @Test
    void startRun_validRequest_returns202WithRunId() throws Exception {
        when(conductor.startRun(anyString(), any())).thenReturn("run-abc");

        mockMvc.perform(post("/api/v1/orchestration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new StartOrchestrationRequest("Build a URL shortener", "GREENFIELD"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value("run-abc"))
                .andExpect(header().string("Location", "/api/v1/orchestration/run-abc"));
    }

    @Test
    void startRun_missingDescription_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orchestration")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenarioDescription\":\"\",\"scenarioType\":\"GREENFIELD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startRun_invalidScenarioType_returns400() throws Exception {
        when(conductor.startRun(anyString(), any())).thenThrow(new IllegalArgumentException("Unknown scenario: INVALID"));

        mockMvc.perform(post("/api/v1/orchestration")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenarioDescription\":\"Build something\",\"scenarioType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRun_existingRun_returnsRunStatus() throws Exception {
        when(querier.getRun("run-1")).thenReturn(buildRun("run-1", PhaseStatus.RUNNING));

        mockMvc.perform(get("/api/v1/orchestration/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getRun_unknownRun_returns404() throws Exception {
        when(querier.getRun("not-found")).thenThrow(new OrchestrationRunNotFoundException("not-found"));

        mockMvc.perform(get("/api/v1/orchestration/not-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllRuns_returnsListOfRuns() throws Exception {
        when(querier.getAllRuns()).thenReturn(List.of(
                buildRun("run-1", PhaseStatus.SUCCEEDED),
                buildRun("run-2", PhaseStatus.RUNNING)));

        mockMvc.perform(get("/api/v1/orchestration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getPendingApprovals_returnsList() throws Exception {
        when(querier.getPendingApprovals("run-1")).thenReturn(List.of(
                new ApprovalRequest("req-1", "run-1", SdlcPhase.REQUIREMENTS,
                        "Proceed?", ApprovalStatus.PENDING, Instant.now(), null, null)));

        mockMvc.perform(get("/api/v1/orchestration/run-1/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void respondToApproval_approved_returns200() throws Exception {
        when(conductor.respondToApproval(eq("run-1"), eq("req-1"), eq(true), anyString()))
                .thenReturn(buildRun("run-1", PhaseStatus.RUNNING));

        mockMvc.perform(post("/api/v1/orchestration/run-1/approve/req-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"respondedBy\":\"bill@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void getMetrics_completedRun_returnsMetrics() throws Exception {
        when(querier.getMetrics("run-done")).thenReturn(new OrchestrationMetrics(
                "run-done", 60_000L, Map.of(), 1.0, 0, 0, 0L));

        mockMvc.perform(get("/api/v1/orchestration/run-done/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successRate").value(1.0))
                .andExpect(jsonPath("$.totalRetries").value(0));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private OrchestrationRun buildRun(String runId, PhaseStatus status) {
        return new OrchestrationRun(runId, ScenarioType.GREENFIELD, "Build a URL shortener",
                status, Map.of(), List.of(), null, Instant.now(), null);
    }
}
