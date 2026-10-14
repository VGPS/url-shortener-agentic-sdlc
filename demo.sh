#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# demo.sh — end-to-end demonstration of the URL Shortener + Agentic SDLC
#
# Runs through four demonstrations:
#   1. URL Shortener basics (no API key required)
#   2. GREENFIELD orchestration — all 6 phases
#   3. BROWNFIELD orchestration — REQUIREMENTS skipped
#   4. AMBIGUOUS orchestration — pauses for human approval
#
# Usage:
#   export ANTHROPIC_API_KEY=sk-ant-...
#   mvn spring-boot:run &          # start the app first
#   sleep 15                       # wait for startup
#   chmod +x demo.sh && ./demo.sh
#
# The script polls until each orchestration run reaches a terminal state.
# LLM calls take time — expect 5–15 minutes per orchestration scenario.
# ─────────────────────────────────────────────────────────────────────────────

BASE="http://localhost:8080"
POLL_INTERVAL=10   # seconds between status checks

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'
header()  { echo -e "\n${CYAN}══════════════════════════════════════════════════${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}══════════════════════════════════════════════════${NC}"; }
success() { echo -e "${GREEN}✓ $1${NC}"; }
info()    { echo -e "${YELLOW}→ $1${NC}"; }
error()   { echo -e "${RED}✗ $1${NC}"; }

# ── Helpers ───────────────────────────────────────────────────────────────────
check_app_running() {
  if ! curl -s "$BASE/actuator/health" | grep -q '"status":"UP"' 2>/dev/null; then
    error "App is not running at $BASE"
    echo "  Start it with: mvn spring-boot:run"
    exit 1
  fi
}

check_api_key() {
  if [[ -z "$ANTHROPIC_API_KEY" || "$ANTHROPIC_API_KEY" == "not-set" ]]; then
    error "ANTHROPIC_API_KEY is not set. Orchestration scenarios will fail."
    echo "  export ANTHROPIC_API_KEY=sk-ant-..."
    echo "  Continuing — URL Shortener demo does not require an API key."
  fi
}

# POST to /api/v1/orchestration, return the runId
start_run() {
  local description="$1"
  local scenario_type="$2"
  local run_id
  run_id=$(curl -s -X POST "$BASE/api/v1/orchestration" \
    -H "Content-Type: application/json" \
    -d "{\"scenarioDescription\": \"$description\", \"scenarioType\": \"$scenario_type\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('runId','ERROR'))" 2>/dev/null)
  echo "$run_id"
}

# Get current run status
get_status() {
  local run_id="$1"
  curl -s "$BASE/api/v1/orchestration/$run_id" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','UNKNOWN'))" 2>/dev/null
}

# Get phase statuses as formatted string
get_phases() {
  local run_id="$1"
  curl -s "$BASE/api/v1/orchestration/$run_id" \
    | python3 -c "
import sys, json
d = json.load(sys.stdin)
phases = d.get('phaseStatuses', {})
for phase, status in phases.items():
    icon = '✓' if status == 'SUCCEEDED' else ('↷' if status == 'SKIPPED' else ('◷' if status == 'RUNNING' else ('?' if status == 'PENDING' else '✗')))
    print(f'  {icon}  {phase:<20} {status}')
" 2>/dev/null
}

# Poll until terminal status, printing phase progress each iteration
poll_until_done() {
  local run_id="$1"
  local timeout_secs="${2:-900}"   # 15 min default
  local elapsed=0
  while [[ $elapsed -lt $timeout_secs ]]; do
    local status
    status=$(get_status "$run_id")
    echo ""
    info "Status: $status  (${elapsed}s elapsed)"
    get_phases "$run_id"
    case "$status" in
      SUCCEEDED) success "Run completed successfully"; return 0 ;;
      FAILED)    error   "Run failed"; return 1 ;;
      ROLLED_BACK) error "Run rolled back"; return 1 ;;
    esac
    sleep "$POLL_INTERVAL"
    elapsed=$((elapsed + POLL_INTERVAL))
  done
  error "Timed out after ${timeout_secs}s"
  return 1
}

# Get first pending approval requestId
get_pending_approval_id() {
  local run_id="$1"
  curl -s "$BASE/api/v1/orchestration/$run_id/approvals" \
    | python3 -c "
import sys, json
approvals = json.load(sys.stdin)
pending = [a for a in approvals if a.get('status') == 'PENDING']
print(pending[0]['requestId'] if pending else '')
" 2>/dev/null
}

# ═══════════════════════════════════════════════════════════════════════════════
header "DEMO 1 — URL Shortener (no API key required)"
# ═══════════════════════════════════════════════════════════════════════════════

check_app_running
check_api_key

info "Shortening a URL..."
SHORTEN_RESPONSE=$(curl -s -X POST "$BASE/api/v1/urls" \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://schwab.com/research/stocks"}')

SHORT_CODE=$(echo "$SHORTEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['shortCode'])" 2>/dev/null)
SHORT_LINK=$(echo "$SHORTEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['shortLink'])" 2>/dev/null)
echo "$SHORTEN_RESPONSE" | python3 -m json.tool
success "Short code: $SHORT_CODE"

info "Following the redirect..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$SHORT_CODE")
LOCATION=$(curl -s -D - -o /dev/null "$BASE/$SHORT_CODE" | grep -i "location:" | tr -d '\r')
echo "  HTTP status: $HTTP_STATUS"
echo "  $LOCATION"
[[ "$HTTP_STATUS" == "302" ]] && success "Redirect works" || error "Expected 302, got $HTTP_STATUS"

info "Fetching analytics (1 click recorded)..."
curl -s "$BASE/api/v1/urls/$SHORT_CODE/analytics" | python3 -m json.tool

# ═══════════════════════════════════════════════════════════════════════════════
header "DEMO 2 — GREENFIELD Orchestration (all 6 phases)"
# ═══════════════════════════════════════════════════════════════════════════════

if [[ -z "$ANTHROPIC_API_KEY" || "$ANTHROPIC_API_KEY" == "not-set" ]]; then
  info "Skipping orchestration demos (ANTHROPIC_API_KEY not set)"
  echo ""
  echo "Set the key and re-run to see the orchestration demos:"
  echo "  export ANTHROPIC_API_KEY=sk-ant-..."
  echo "  ./demo.sh"
  exit 0
fi

info "Starting GREENFIELD run..."
GF_RUN_ID=$(start_run "Add rate limiting to the URL shortener — max 10 shortenings per minute per IP" "GREENFIELD")
echo "  Run ID: $GF_RUN_ID"
[[ "$GF_RUN_ID" == "ERROR" || -z "$GF_RUN_ID" ]] && error "Failed to start run" && exit 1

info "Streaming tokens (Ctrl+C to stop streaming, run continues in background)..."
echo "  curl -N $BASE/api/v1/orchestration/$GF_RUN_ID/stream"
echo "  (Press Enter to skip streaming and just poll)"
read -t 5 -r _ && true   # wait up to 5s for user to press Enter

info "Polling for completion..."
poll_until_done "$GF_RUN_ID" 900
GF_STATUS=$?

if [[ $GF_STATUS -eq 0 ]]; then
  info "Fetching reliability metrics..."
  curl -s "$BASE/api/v1/orchestration/$GF_RUN_ID/metrics" | python3 -m json.tool
fi

# ═══════════════════════════════════════════════════════════════════════════════
header "DEMO 3 — BROWNFIELD Orchestration (REQUIREMENTS skipped)"
# ═══════════════════════════════════════════════════════════════════════════════

info "Starting BROWNFIELD run — DESIGN will perform impact analysis of existing code..."
BF_RUN_ID=$(start_run "Add custom expiry dates — currently all links expire in 365 days" "BROWNFIELD")
echo "  Run ID: $BF_RUN_ID"
[[ "$BF_RUN_ID" == "ERROR" || -z "$BF_RUN_ID" ]] && error "Failed to start run" && exit 1

info "Polling for completion (watch for REQUIREMENTS = SKIPPED)..."
poll_until_done "$BF_RUN_ID" 900

# ═══════════════════════════════════════════════════════════════════════════════
header "DEMO 4 — AMBIGUOUS Orchestration (human approval checkpoint)"
# ═══════════════════════════════════════════════════════════════════════════════

info "Starting AMBIGUOUS run — will pause after REQUIREMENTS for human review..."
AM_RUN_ID=$(start_run "Add analytics to the URL shortener" "AMBIGUOUS")
echo "  Run ID: $AM_RUN_ID"
[[ "$AM_RUN_ID" == "ERROR" || -z "$AM_RUN_ID" ]] && error "Failed to start run" && exit 1

info "Waiting for approval checkpoint to appear..."
APPROVAL_ID=""
WAIT_SECS=0
while [[ -z "$APPROVAL_ID" && $WAIT_SECS -lt 300 ]]; do
  APPROVAL_ID=$(get_pending_approval_id "$AM_RUN_ID")
  if [[ -z "$APPROVAL_ID" ]]; then
    echo "  No checkpoint yet (${WAIT_SECS}s). Waiting ${POLL_INTERVAL}s..."
    sleep "$POLL_INTERVAL"
    WAIT_SECS=$((WAIT_SECS + POLL_INTERVAL))
  fi
done

if [[ -z "$APPROVAL_ID" ]]; then
  error "Timed out waiting for approval checkpoint"
  exit 1
fi

success "Approval checkpoint raised! Request ID: $APPROVAL_ID"
info "Displaying the approval question..."
curl -s "$BASE/api/v1/orchestration/$AM_RUN_ID/approvals" | python3 -m json.tool

echo ""
echo -e "${YELLOW}Human decision required. Approve this run? [y/n]${NC}"
read -r APPROVE_INPUT

if [[ "$APPROVE_INPUT" =~ ^[Yy]$ ]]; then
  info "Approving the run..."
  curl -s -X POST "$BASE/api/v1/orchestration/$AM_RUN_ID/approve/$APPROVAL_ID" \
    -H "Content-Type: application/json" \
    -d '{"approved": true, "respondedBy": "demo-user@example.com"}' | python3 -m json.tool
  success "Approved — polling for completion..."
  poll_until_done "$AM_RUN_ID" 900
else
  info "Rejecting the run..."
  curl -s -X POST "$BASE/api/v1/orchestration/$AM_RUN_ID/approve/$APPROVAL_ID" \
    -H "Content-Type: application/json" \
    -d '{"approved": false, "respondedBy": "demo-user@example.com"}' | python3 -m json.tool
  info "Polling for FAILED status and rollback..."
  poll_until_done "$AM_RUN_ID" 60
fi

# ═══════════════════════════════════════════════════════════════════════════════
header "Demo complete"
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo "All runs:"
curl -s "$BASE/api/v1/orchestration" \
  | python3 -c "
import sys, json
runs = json.load(sys.stdin)
for r in runs:
    print(f\"  {r['runId'][:8]}...  {r['scenarioType']:<12}  {r['status']}\")
" 2>/dev/null

echo ""
success "Demo finished. The app is still running at $BASE"
echo "  H2 console: $BASE/h2-console"
echo "  Health:     $BASE/actuator/health"
