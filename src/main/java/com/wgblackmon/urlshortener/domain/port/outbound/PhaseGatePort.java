package com.wgblackmon.urlshortener.domain.port.outbound;

import com.wgblackmon.urlshortener.domain.model.GateResult;
import com.wgblackmon.urlshortener.domain.model.PhaseResult;
import com.wgblackmon.urlshortener.domain.model.SdlcPhase;

/**
 * Outbound port for evaluating phase exit gates before allowing the orchestrator
 * to advance to the next phase.
 *
 * <h2>Why a separate port for gate evaluation?</h2>
 * <p>Gate evaluation is itself an LLM call — a different prompt from the phase
 * execution prompt. Separating it into its own port allows the adapter to use a
 * lighter, faster model for gate evaluation than for phase execution (cost
 * optimisation). It also makes gates independently testable: unit tests can
 * inject a mock {@code PhaseGatePort} that returns PASS or FAIL without making
 * real LLM calls.
 *
 * <h2>Why pass the PhaseResult to the gate?</h2>
 * <p>The gate evaluator needs access to the agent's output to determine whether
 * the phase's deliverables meet the quality bar. Passing the full {@link PhaseResult}
 * (which includes the raw agent output and artifacts produced) gives the gate
 * evaluator everything it needs without requiring it to query the database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-14
 * @updated 2026-10-14
 */
public interface PhaseGatePort {

    /**
     * Evaluates whether the phase's output meets the quality bar required to advance.
     *
     * @param phase       the phase whose output is being evaluated
     * @param phaseResult the result produced by the phase execution
     * @return GateResult with pass/fail, reason, and remediation hint for retry
     */
    GateResult evaluate(SdlcPhase phase, PhaseResult phaseResult);
}
