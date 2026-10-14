# Three Scenario Walkthroughs

## GREENFIELD — New Feature from Scratch

**Input:**
```json
{
  "scenarioDescription": "Add rate limiting to the URL shortener — max 10 shortenings per minute per IP",
  "scenarioType": "GREENFIELD"
}
```

**Execution sequence:**

```
1. REQUIREMENTS phase
   - Agent analyzes description
   - Produces: FUNCTIONAL_REQUIREMENTS (numbered list), NON_FUNCTIONAL_REQUIREMENTS,
               AMBIGUITY_FLAGS (none in this case), OPENAPI_SKELETON
   - Gate evaluates: all required sections present? → PASS

2. DESIGN phase (depends on REQUIREMENTS completing)
   - Agent receives REQUIREMENTS_DOC as context
   - Produces: DESIGN_DOC (architecture + Bucket Token algorithm decision),
               OPENAPI_SPEC (full 3.0 YAML with rate-limit response codes),
               ADR (why Bucket Token over Leaky Bucket),
               RISK_ASSESSMENT (Redis dependency, clock skew)
   - Gate evaluates: OPENAPI_SPEC starts with "openapi:"? ADR present? → PASS

3. IMPLEMENTATION + DOCUMENTATION (parallel — both depend on DESIGN)
   - IMPLEMENTATION receives DESIGN_DOC + OPENAPI_SPEC as context
   - Produces: IMPLEMENTATION_SUMMARY (RateLimiterService, in-memory ConcurrentHashMap
               approach for assignment scope), CODE_OUTLINE, DEVIATIONS (no Redis for now)
   - DOCUMENTATION receives DESIGN_DOC as context
   - Produces: README with quick start, API reference, rate limit headers documented

4. RELEASE_READINESS (depends on IMPLEMENTATION + DOCUMENTATION completing)
   - Receives IMPLEMENTATION_SUMMARY + DOCUMENTATION_SUMMARY
   - Produces: RELEASE_CHECKLIST (12 items), RELEASE_DECISION: APPROVED

5. TESTING (depends on RELEASE_READINESS)
   - Produces: TEST_PLAN (unit, integration, edge cases like exactly-10 per minute),
               HAPPY_PATH_SCENARIOS (shorten 5 URLs → all succeed),
               EDGE_CASES (shorten 11th URL → 429, different IPs → independent counters),
               QUALITY_VERDICT: PASS
```

**Total phases:** 6 (IMPL + DOCS parallel = 5 sequential steps in wall-clock time)
**Audit entries generated:** ~30 (phase_started, gate_evaluated, phase_succeeded × 6 phases + run events)

---

## BROWNFIELD — Modify Existing Code

**Input:**
```json
{
  "scenarioDescription": "Add custom expiry dates to the URL shortener — currently all links expire in 365 days",
  "scenarioType": "BROWNFIELD"
}
```

**What differs:**
- REQUIREMENTS phase is **SKIPPED** (status = SKIPPED in phaseResults)
- DESIGN phase has **no upstream dependencies** — it becomes the entry point
- DESIGN prompt is different: it performs **impact analysis** ("which existing classes change?") rather than producing new requirements from scratch
- Downstream phases (IMPLEMENTATION through TESTING) are identical to GREENFIELD

**DESIGN output (BROWNFIELD):**
```
DESIGN_DOC: Impact analysis
  - ShortUrl record: add expiresAt field (already exists, make it user-configurable)
  - ShortenRequest record: add optional ttlDays field
  - UrlShorteningService.shorten(): honor user-supplied ttlDays if present
  - No schema migration required — expiresAt column exists
  
OPENAPI_SPEC: Updated spec with optional ttlDays parameter on POST /api/v1/urls
ADR: Why optional ttlDays (backward compatibility for existing integrations)
```

**Audit trail shows:** REQUIREMENTS: SKIPPED (not FAILED, not ROLLED_BACK — genuinely skipped)

---

## AMBIGUOUS — Pause for Human Clarification

**Input:**
```json
{
  "scenarioDescription": "Add analytics to the URL shortener",
  "scenarioType": "AMBIGUOUS"
}
```

**Why AMBIGUOUS?** "Analytics" is vague — it could mean click counts, geographic data, referrer tracking, time-series charts, export to CSV, or all of the above. The orchestrator detects ambiguity in the REQUIREMENTS phase output and raises a human approval checkpoint before proceeding.

**Execution sequence:**

```
1. REQUIREMENTS phase runs normally
   - Agent produces FUNCTIONAL_REQUIREMENTS with AMBIGUITY_FLAGS:
     "Analytics scope is undefined: click counts only? Geographic breakdown? 
      Time-series? Export? These decisions significantly affect the design."
   - Gate evaluates: AMBIGUITY_FLAGS section present → PASS

2. Human approval checkpoint raised (because scenarioType == AMBIGUOUS)
   - ApprovalRequest saved with status=PENDING
   - Run status → AWAITING_APPROVAL
   - Orchestrator releases execution thread (no blocking wait)
   - Question: "Requirements analysis complete. Please review the AMBIGUITY_FLAGS..."

3. Human polls /api/v1/orchestration/{runId}/approvals → sees the checkpoint

4. Human responds:
   POST /api/v1/orchestration/{runId}/approve/{requestId}
   {"approved": true, "respondedBy": "engineer@company.com"}

5. Orchestrator resumes on next poll cycle
   - DESIGN phase proceeds with the clarification recorded in audit log

6-7. Remaining phases (DESIGN through TESTING) continue as GREENFIELD
```

**REJECTED scenario:**
If the human responds `{"approved": false}`, the run is immediately marked FAILED.
All completed phases are rolled back (REQUIREMENTS → ROLLED_BACK). Audit trail records `HUMAN_REJECTED`.

**TIMED_OUT scenario:**
If no human responds within 30 minutes, the approval status flips to TIMED_OUT and the run fails with rollback.
