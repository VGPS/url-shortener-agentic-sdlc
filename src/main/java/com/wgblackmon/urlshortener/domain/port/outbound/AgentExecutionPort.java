package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.AgentOutput;
import com.wgblackmon.urlshortener.domain.model.AgentTask;

import java.util.function.Consumer;

/**
 * Outbound port for executing a single agent task against an LLM backend.
 *
 * <h2>Why Consumer&lt;String&gt; for streaming instead of Reactor Flux?</h2>
 * <p>The domain layer must remain framework-free — no Spring, no Reactor, no
 * external library imports. {@code Consumer<String>} is a pure JDK functional
 * interface (java.util.function). The infrastructure adapter implementing this
 * port is free to use Spring AI's streaming ChatClient internally and invoke the
 * consumer on each token. The web layer wraps the consumer in an {@code SseEmitter}
 * to push tokens to the browser. This three-layer chain keeps the domain unaware of
 * both the AI framework and the HTTP delivery mechanism.
 *
 * <h2>When is tokenConsumer null?</h2>
 * <p>When {@code task.streamingEnabled()} is false, the caller passes null and the
 * adapter accumulates the full response before returning. When streaming is enabled,
 * the caller provides a consumer and the adapter invokes it as tokens arrive.
 * AgentOutput always contains the complete raw response regardless of streaming mode.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface AgentExecutionPort {

    /**
     * Executes the agent task and returns the complete output.
     *
     * @param task           the task to execute
     * @param tokenConsumer  callback invoked for each streamed token; null for non-streaming
     * @return the complete agent output including raw response and token count
     */
    AgentOutput execute(AgentTask task, Consumer<String> tokenConsumer);
}
