package com.wgblackmon.urlshortener.infrastructure.config;

import com.wgblackmon.urlshortener.domain.port.outbound.ApprovalPort;
import com.wgblackmon.urlshortener.domain.port.outbound.ClickEventPort;
import com.wgblackmon.urlshortener.domain.port.outbound.CodeGeneratorPort;
import com.wgblackmon.urlshortener.domain.port.outbound.AgentExecutionPort;
import com.wgblackmon.urlshortener.domain.port.outbound.OrchestrationRunPort;
import com.wgblackmon.urlshortener.domain.port.outbound.PhaseGatePort;
import com.wgblackmon.urlshortener.domain.port.outbound.ShortUrlPort;
import com.wgblackmon.urlshortener.domain.service.DependencyGraph;
import com.wgblackmon.urlshortener.domain.service.DependencyGraphFactory;
import com.wgblackmon.urlshortener.domain.service.PhaseExecutor;
import com.wgblackmon.urlshortener.domain.service.PromptBuilder;
import com.wgblackmon.urlshortener.domain.service.SdlcOrchestrationService;
import com.wgblackmon.urlshortener.domain.service.UrlShorteningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Central Spring configuration for the URL Shortener application.
 *
 * <p>Wires the async executor used by the agentic orchestration engine.
 * All application beans (services, adapters) are constructor-injected;
 * this class only registers infrastructure beans that require explicit
 * programmatic wiring.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
@Slf4j
@Configuration
public class AppConfig {

    /**
     * Creates the shared ChatClient instance from Spring AI's auto-configured builder.
     * Spring AI 1.0.0 auto-configures ChatClient.Builder (not ChatClient itself), so
     * this explicit bean definition is required for the AI adapters to function.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        log.debug("chatClient() | building ChatClient");
        return builder.build();
    }

    /**
     * Wires the pure-domain {@link UrlShorteningService} with its three outbound
     * port adapters and the configured base URL for link generation.
     */
    @Bean
    public UrlShorteningService urlShorteningService(
            ShortUrlPort shortUrlPort,
            ClickEventPort clickEventPort,
            CodeGeneratorPort codeGeneratorPort,
            @Value("${urlshortener.base-url}") String baseUrl) {
        log.debug("urlShorteningService() | baseUrl={}", baseUrl);
        return new UrlShorteningService(shortUrlPort, clickEventPort, codeGeneratorPort, baseUrl);
    }

    /**
     * Wires the PromptBuilder (stateless, no external dependencies — safe to share).
     */
    @Bean
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    /**
     * Wires the DependencyGraphFactory — creates a new DependencyGraph per scenario type.
     */
    @Bean
    public DependencyGraphFactory dependencyGraphFactory() {
        return DependencyGraph::new;
    }

    /**
     * Wires the PhaseExecutor with its four dependencies.
     * PhaseExecutor is responsible for a single phase: execute → gate → retry/fail.
     */
    @Bean
    public PhaseExecutor phaseExecutor(AgentExecutionPort agentPort,
                                       PhaseGatePort gatePort,
                                       OrchestrationRunPort runPort,
                                       PromptBuilder promptBuilder) {
        log.debug("phaseExecutor() | wiring");
        return new PhaseExecutor(agentPort, gatePort, runPort, promptBuilder);
    }

    /**
     * Wires the SdlcOrchestrationService — the top-level domain service implementing
     * both ConductOrchestrationUseCase and QueryOrchestrationUseCase.
     */
    @Bean
    public SdlcOrchestrationService sdlcOrchestrationService(OrchestrationRunPort runPort,
                                                              ApprovalPort approvalPort,
                                                              PhaseExecutor phaseExecutor,
                                                              DependencyGraphFactory graphFactory,
                                                              Executor orchestrationExecutor) {
        log.debug("sdlcOrchestrationService() | wiring");
        return new SdlcOrchestrationService(runPort, approvalPort, phaseExecutor, graphFactory, orchestrationExecutor);
    }

    /**
     * Dedicated executor for agentic orchestration runs.
     * Each orchestration run executes on its own thread, allowing the REST
     * controller to return 202 Accepted immediately while the run proceeds
     * asynchronously. Sized conservatively — orchestration is I/O bound
     * (LLM API calls), not CPU bound.
     */
    @Bean(name = "orchestrationExecutor")
    public Executor orchestrationExecutor() {
        log.debug("orchestrationExecutor() | initializing");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("orchestration-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.debug("orchestrationExecutor() | return={}", executor);
        return executor;
    }
}
