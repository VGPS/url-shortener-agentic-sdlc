# Setup and Running Guide

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Anthropic API key | — | [console.anthropic.com](https://console.anthropic.com) |

No database installation required — H2 runs in-memory and is included as a Maven dependency.

---

## 1. Clone / Open the Project

```bash
cd /c/workspaces/cSchwabAssignment   # or wherever you have the project
```

---

## 2. Set Your API Key

```bash
# Option A — export in your current shell (lasts until the shell closes)
export ANTHROPIC_API_KEY=sk-ant-api03-...

# Option B — copy .env.example and source it
cp .env.example .env
# Edit .env, replace sk-ant-... with your real key
source .env
```

Verify it is set:
```bash
echo $ANTHROPIC_API_KEY   # should print your key, not empty
```

---

## 3. Build and Test

```bash
# Compile + run all 102 tests (no AI calls — fast, ~2 min)
mvn test

# Expected output:
# Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

## 4. Start the Application

```bash
mvn spring-boot:run
```

You should see within ~15 seconds:
```
Started UrlShortenerApplication in X.XXX seconds
```

The app is ready at **http://localhost:8080**

Optional: browse the H2 database console at **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:urlshortenerdb`
- Username: `sa` / Password: *(blank)*

---

## 5. Smoke Test — URL Shortener (no API key needed)

Run these in a second terminal while the app is running:

```bash
# Shorten a URL
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://schwab.com/research/stocks"}' | python3 -m json.tool

# Expected:
# {
#   "shortCode": "xK3mP9",
#   "shortLink": "http://localhost:8080/xK3mP9",
#   "originalUrl": "https://schwab.com/research/stocks",
#   "active": true
# }

# Follow the redirect (replace xK3mP9 with your actual code)
curl -v http://localhost:8080/xK3mP9 2>&1 | grep "< HTTP\|Location"
# Expected: HTTP/1.1 302  +  Location: https://schwab.com/research/stocks

# View click analytics
curl -s http://localhost:8080/api/v1/urls/xK3mP9/analytics | python3 -m json.tool
```

---

## 6. Run the Full Demo Script

The `demo.sh` script runs all three orchestration scenarios automatically.
It requires a valid API key and takes approximately 5–10 minutes per scenario
(LLM calls are slow).

```bash
chmod +x demo.sh
./demo.sh
```

Or run scenarios individually — see `demo.sh` for the curl commands.

---

## 7. Manual Scenario Walkthrough

### GREENFIELD (all 6 phases, ~5–10 min)

```bash
# Start the run — returns immediately with a runId
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/orchestration \
  -H "Content-Type: application/json" \
  -d '{
    "scenarioDescription": "Add rate limiting to the URL shortener — max 10 per minute per IP",
    "scenarioType": "GREENFIELD"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['runId'])")

echo "Run ID: $RUN_ID"

# Poll status every 10 seconds
watch -n 10 "curl -s http://localhost:8080/api/v1/orchestration/$RUN_ID | python3 -m json.tool"

# Stream tokens live (open a third terminal)
curl -N http://localhost:8080/api/v1/orchestration/$RUN_ID/stream
```

### BROWNFIELD (skips REQUIREMENTS, ~4–8 min)

```bash
curl -s -X POST http://localhost:8080/api/v1/orchestration \
  -H "Content-Type: application/json" \
  -d '{
    "scenarioDescription": "Add custom expiry dates — currently all links expire in 365 days",
    "scenarioType": "BROWNFIELD"
  }' | python3 -m json.tool
```

### AMBIGUOUS (pauses for human approval)

```bash
# Start the run
RUN_ID=$(curl -s -X POST http://localhost:8080/api/v1/orchestration \
  -H "Content-Type: application/json" \
  -d '{
    "scenarioDescription": "Add analytics to the URL shortener",
    "scenarioType": "AMBIGUOUS"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['runId'])")

# Wait for the approval checkpoint (check every 10s — it appears after REQUIREMENTS completes)
curl -s http://localhost:8080/api/v1/orchestration/$RUN_ID/approvals | python3 -m json.tool

# Approve — replace REQUEST_ID with the value from above
curl -s -X POST http://localhost:8080/api/v1/orchestration/$RUN_ID/approve/REQUEST_ID \
  -H "Content-Type: application/json" \
  -d '{"approved": true, "respondedBy": "bill.blackmon@example.com"}' | python3 -m json.tool

# The run resumes automatically and completes the remaining phases
```

---

## 8. View Metrics (after a run completes)

```bash
curl -s http://localhost:8080/api/v1/orchestration/$RUN_ID/metrics | python3 -m json.tool

# Expected:
# {
#   "runId": "...",
#   "totalDurationMs": 180000,
#   "phaseLatenciesMs": { "REQUIREMENTS": 12000, "DESIGN": 25000, ... },
#   "successRate": 1.0,
#   "totalRetries": 0,
#   "rollbackCount": 0,
#   "mttrMs": 0
# }
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `401 Unauthorized` in logs | API key not set or wrong | `echo $ANTHROPIC_API_KEY` — must be non-empty |
| Run stuck in `RUNNING` | LLM is slow, wait 5 min | Check app logs for `phase=X, maxRetries=3` |
| Run ends in `FAILED` | Gate failed 3 times | Check logs for `GATE_FAILED` audit entries |
| Port 8080 in use | Another process | `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| `BUILD FAILURE` in mvn test | Missing Java 21 | `java -version` must show 21+ |
