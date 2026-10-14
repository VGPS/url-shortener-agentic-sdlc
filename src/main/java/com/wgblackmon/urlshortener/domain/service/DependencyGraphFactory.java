package com.wgblackmon.urlshortener.domain.service;

import com.wgblackmon.urlshortener.domain.model.ScenarioType;

/**
 * Factory that constructs a {@link DependencyGraph} for the given scenario type.
 * Extracted into its own interface so it can be mocked in unit tests of
 * {@link SdlcOrchestrationService} without building a real graph.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface DependencyGraphFactory {
    DependencyGraph create(ScenarioType scenarioType);
}
