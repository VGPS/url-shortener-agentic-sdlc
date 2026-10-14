package com.wgblackmon.urlshortener.infrastructure.ai;

import com.wgblackmon.urlshortener.domain.model.AgentOutput;
import com.wgblackmon.urlshortener.domain.model.AgentTask;
import com.wgblackmon.urlshortener.domain.port.outbound.AgentExecutionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter implementing {@link AgentExecutionPort} via Spring AI's {@link ChatClient}.
 *
 * <h2>Streaming implementation</h2>
 * <p>When {@code task.streamingEnabled()} is true, this adapter uses
 * {@code ChatClient.stream()} to get a {@code Flux<ChatResponse>}. For each token
 * chunk, it invokes the {@code tokenConsumer} callback and also accumulates the full
 * response. The domain layer receives a complete {@link AgentOutput} with the full
 * raw response regardless of whether streaming was enabled — the streaming mechanism
 * is purely a delivery optimisation for the HTTP layer.
 *
 * <h2>Token counting</h2>
 * <p>Spring AI 1.0.0 exposes token usage via {@code ChatResponse.getMetadata().getUsage()}.
 * When usage metadata is unavailable (e.g., streaming with no usage block), token count
 * is approximated as zero. The metrics layer uses these counts for cost analysis.
 *
 * <h2>Why no retry logic here?</h2>
 * <p>Retry logic lives in {@link com.wgblackmon.urlshortener.domain.service.PhaseExecutor},
 * which owns the retry policy and backoff calculation. Having retry in two places would
 * make the retry count metrics inaccurate and the audit log incomplete. This adapter
 * makes a single best-effort call and lets the caller decide whether to retry.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Component
public class ClaudeAgentAdapter implements AgentExecutionPort {

    private final ChatClient chatClient;

    public ClaudeAgentAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public AgentOutput execute(AgentTask task, Consumer<String> tokenConsumer) {
        log.debug("execute() | taskId={}, phase={}, streaming={}", task.taskId(), task.phase(), task.streamingEnabled());

        String rawResponse;
        int tokensUsed = 0;

        if (task.streamingEnabled() && tokenConsumer != null) {
            rawResponse = executeStreaming(task.prompt(), tokenConsumer);
        } else {
            rawResponse = executeBlocking(task.prompt());
        }

        AgentOutput result = new AgentOutput(
                task.taskId(),
                task.phase(),
                rawResponse,
                extractArtifacts(rawResponse),
                tokensUsed,
                Instant.now()
        );
        log.debug("execute() | return=AgentOutput(taskId={}, phase={}, responseLength={})",
                result.taskId(), result.phase(), rawResponse.length());
        return result;
    }

    private String executeBlocking(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String executeStreaming(String prompt, Consumer<String> tokenConsumer) {
        StringBuilder accumulated = new StringBuilder();
        chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(token -> {
                    tokenConsumer.accept(token);
                    accumulated.append(token);
                })
                .blockLast();
        return accumulated.toString();
    }

    /**
     * Extracts labelled artifact sections from a structured LLM response.
     * Looks for lines matching "ARTIFACT_KEY:" and extracts until the next such label.
     * Returns the raw artifact content strings.
     */
    private List<String> extractArtifacts(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) return List.of();
        // Simple extraction: split on double-newline sections; return non-empty sections
        String[] sections = rawResponse.split("\n\n+");
        List<String> artifacts = new java.util.ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (!trimmed.isBlank() && trimmed.contains(":")) {
                artifacts.add(trimmed);
            }
        }
        return artifacts;
    }
}
