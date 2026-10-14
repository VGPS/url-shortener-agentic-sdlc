# URL Shortener with Agentic SDLC Orchestration

A production-grade URL shortening service with a layered AI agent that orchestrates the full software development lifecycle — from requirements analysis through release readiness and testing — using Claude as the execution engine.

## Quick Start

### Prerequisites
- Java 21 (Temurin or Corretto)
- Maven 3.9+
- An Anthropic API key

### Configuration

```bash
cp .env.example .env
# Edit .env and set your ANTHROPIC_API_KEY
source .env
```

### Run

```bash
mvn spring-boot:run
```

Application starts on `http://localhost:8080`.

### Test

```bash
mvn test
```

97 tests, all passing. No AI calls during tests.

---

## URL Shortener API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/urls` | Shorten a URL |
| GET | `/api/v1/urls/{code}` | Get details for a short code |
| DELETE | `/api/v1/urls/{code}` | Deactivate a short code |
| GET | `/api/v1/urls/{code}/analytics` | Click analytics for a code |
| GET | `/{code}` | Redirect to original URL (302) |

### Example

```bash
# Shorten a URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/very/long/path"}'

# Response
{
  "shortCode": "xK3mP9",
  "shortLink": "http://localhost:8080/xK3mP9",
  "originalUrl": "https://example.com/very/long/path",
  "active": true
}

# Redirect (follow the 302)
curl -L http://localhost:8080/xK3mP9
```

---

## Agentic SDLC Orchestration API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/orchestration` | Start an orchestration run (202 Accepted) |
| GET | `/api/v1/orchestration/{runId}` | Poll run status |
| GET | `/api/v1/orchestration` | List all runs |
| GET | `/api/v1/orchestration/{runId}/approvals` | List pending human approval checkpoints |
| POST | `/api/v1/orchestration/{runId}/approve/{requestId}` | Respond to an approval checkpoint |
| GET | `/api/v1/orchestration/{runId}/metrics` | Reliability metrics (completed runs only) |
| GET | `/api/v1/orchestration/{runId}/stream` | SSE stream of agent output tokens |

### Start a GREENFIELD run

```bash
curl -X POST http://localhost:8080/api/v1/orchestration \
  -H "Content-Type: application/json" \
  -d '{
    "scenarioDescription": "Build a rate-limited URL shortener with analytics",
    "scenarioType": "GREENFIELD"
  }'

# Response — returns immediately with a runId
{
  "runId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

### Poll for status

```bash
curl http://localhost:8080/api/v1/orchestration/3fa85f64-5717-4562-b3fc-2c963f66afa6

# Response — check phaseStatuses for per-phase progress
{
  "runId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "scenarioType": "GREENFIELD",
  "status": "RUNNING",
  "phaseStatuses": {
    "REQUIREMENTS": "SUCCEEDED",
    "DESIGN": "AWAITING_GATE",
    "IMPLEMENTATION": "PENDING",
    "DOCUMENTATION": "PENDING",
    "RELEASE_READINESS": "PENDING",
    "TESTING": "PENDING"
  }
}
```

### Stream tokens in real time

```bash
curl -N http://localhost:8080/api/v1/orchestration/3fa85f64-5717-4562-b3fc-2c963f66afa6/stream
```

### Respond to an approval checkpoint (AMBIGUOUS scenario)

```bash
curl -X POST http://localhost:8080/api/v1/orchestration/RUN_ID/approve/REQUEST_ID \
  -H "Content-Type: application/json" \
  -d '{"approved": true, "respondedBy": "your.email@company.com"}'
```

---

## Configuration Reference

```yaml
# application.yml key properties
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: ${ANTHROPIC_MODEL:claude-opus-5}

urlshortener:
  base-url: ${BASE_URL:http://localhost:8080}
  code-length: 6

aihealthcare:
  orchestration:
    approval-timeout-minutes: ${APPROVAL_TIMEOUT_MINUTES:30}
    max-retry-attempts: 3
    retry-backoff-ms: 2000
    retry-backoff-multiplier: 2.0
```
