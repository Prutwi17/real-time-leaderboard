# System Design

**Product:** Real-Time Leaderboard System
**Status:** Phase 0 target design (implementation tracked in [TRACKER.md](TRACKER.md))
**Related:** [TECHSPECS.md](TECHSPECS.md) · [APPFLOW.md](APPFLOW.md) · [SCHEMA.md](SCHEMA.md) · [RULES.md](RULES.md)

---

## 1. High-Level Architecture

```mermaid
flowchart TB
    U[Users / Browsers] --> FE["React + TypeScript + Vite"]
    FE -->|REST / WSS| GW["API Gateway :8080<br/>JWT filter - routes - CORS"]
    GW --> ER[(Eureka :8761<br/>service registry)]
    subgraph Services[Microservices — registered in Eureka]
        AUTH[auth-service :8081]
        USR[user-service :8082]
        SPT[sport-service :8083]
        SCO[score-service :8084]
        LBS[leaderboard-service :8085]
    end
    GW --> AUTH & USR & SPT & SCO & LBS
    AUTH --- DBA[(MySQL auth_db)]
    USR --- DBU[(MySQL user_db)]
    SPT --- DBS[(MySQL sport_db)]
    SCO --- DBC[(MySQL score_db)]
    SCO -->|internal HTTP| LBS
    SCO --> K[[Kafka: score-submitted<br/>(planned)]]
    LBS --> RD[(Redis<br/>Sorted Sets)]
    LBS -->|STOMP over WebSocket| FE
```

Key properties:
- **Single entry point.** The gateway is the only public surface; CORS terminates there.
- **Discovery, not addresses.** All east-west traffic resolves via Eureka logical names.
- **Two write paths.** Synchronous truth (MySQL) and asynchronous propagation (Kafka → Redis/WS).

## 2. Low-Level Architecture (per service)

Every backend service shares the same internal skeleton (package-per-feature, constructor injection, DTO boundary):

```
<service>/
 ├── src/main/java/com/leaderboard/<svc>/
 │    ├── config/        # security, redis/kafka/eureka/openapi config
 │    ├── controller/    # REST endpoints (DTO in/out only)
 │    ├── dto/           # request/response records + validators
 │    ├── entity/        # JPA entities (never serialized to clients)
 │    ├── repository/    # Spring Data interfaces
 │    ├── service/       # business logic (@Service)
 │    ├── client/        # OpenFeign clients where needed
 │    ├── exception/     # domain exceptions + @RestControllerAdvice
 │    └── util/
 └── src/main/resources/application.yml   # env-var placeholders only (${...})
```

- Parent POM pins Boot/Cloud versions and plugins; children inherit ([TECHSPECS §10](TECHSPECS.md)).
- Each service owns exactly one MySQL schema (except gateway/registry which are stateless).
- No shared libraries with business logic across services — contracts are DTOs/events, preventing tight coupling.

## 3. Microservice Boundaries & Data Ownership

| Service | Owns | Must NOT touch directly |
|---|---|---|
| auth-service | `users`, `refresh_tokens` | other services' tables; reads identity via its own API/JWT claims |
| user-service | `user_profiles` (keyed by userId from token) | credentials (lives in auth_db) |
| sport-service | `sports` catalog | scores/users |
| score-service | `scores`, `score_history` | users/sports tables (uses Feign or JWT claims) |
| leaderboard-service | **Implemented:** Redis Sorted Sets for live ranking (all-time per sport); internal HTTP score update endpoint (X-Internal-Service-Secret); rebuild-from-history; paginated/top-N/rank/nearby read endpoints. | Daily/weekly/season windows (planned); Kafka consumer (planned); WebSocket push (planned) |

Boundary rule ([RULES.md](RULES.md)): cross-service data access is **API or event only**. This is what makes "add a sport" a data change rather than a migration.

## 4. API Gateway Design

| Route | Target | Auth | Notes |
|---|---|---|---|
| `/api/auth/**` | `lb://auth-service` | public (register/login/refresh); logout public-with-token | rate-limited harder |
| `/api/users/**` | `lb://user-service` | JWT required | self-profile only |
| `/api/sports/**` | `lb://sport-service` | GET public; writes ADMIN | |
| `/api/scores/**` | `lb://score-service` | JWT required | request size/rate limits |
| `/api/leaderboard/**` | `lb://leaderboard-service` | GET public; `/me` needs JWT | cache-friendly headers |
| `/api/reports/**` | `lb://leaderboard-service` | ADMIN | reporting component |
| `/ws/**` | WS route → leaderboard-service STOMP endpoint |SockJS upgrade; JWT via connect headers | |

Gateway responsibilities: global CORS (allowed origin from env), JWT signature/expiry validation filter, injection of `X-User-Id`/`X-User-Role` downstream headers, centralized error responses for auth failures, optional per-route rate limiting.

## 5. Eureka Design

- Registry at `:8761`; self-mode off; clients register with health check URLs and 30 s renewal.
- Gateway uses `spring.cloud.gateway.discovery.locator` + explicit `lb://` routes (explicit preferred — locator is a convenience fallback).
- Services tolerate registry downtime (cached registry locally); losing Eureka degrades new-instance discovery but does not sever established routes immediately.

## 6. MySQL Design (summary)

Five conceptual tables across four owned schemas; full column-level spec in [SCHEMA.md](SCHEMA.md):

| Table | Owner schema | Purpose |
|---|---|---|
| `users` | auth_db | identity + BCrypt credential + role |
| `refresh_tokens` | auth_db | hashed rotating refresh tokens |
| `user_profiles` | user_db | display profile keyed by userId |
| `sports` | sport_db | configurable sport catalog (code unique) |
| `scores` | score_db | latest submission facts |
| `score_history` | score_db | immutable append-only audit/history (report source) |

Indexes follow query patterns: `(email)` unique, `(code)` unique on sports, `(user_id, created_at DESC)` and `(sport_id, created_at DESC)` on history, unique `(event_id)` for consumer-side dedupe safety net.

## 7. Redis Design (summary)

Full keyspace, TTLs, and command rationale in [SCHEMA.md §Redis](SCHEMA.md). Essence:

- Sorted Set per scope; member = `userId` (string), score = aggregate points.
- Live ranking commands: `ZINCRBY` (apply submission), `ZREVRANGE` (top-N page), `ZREVRANK`/`ZSCORE` (my rank/my score), `ZCARD` (participant count), `ZADD` (bootstrap/rebuild), `ZRANGE` (ascending views/debug).
- Windowed keys (`daily`, `weekly`, season) created lazily on first write, given TTLs per retention policy; expired windows remain answerable from MySQL reports.
> **Implemented (Phase 6):** leaderboard-service uses `LeaderboardKeyFactory` for deterministic key derivation (`FOOTBALL` → `leaderboard:football`, `CRICKET` → `leaderboard:cricket`, `F1` → `leaderboard:f1`). Score updates arrive via internal HTTP from score-service; processed-score idempotency keys (`leaderboard:processed:{scoreId}`, 72h TTL) prevent duplicate ranking increments. Read operations: `ZREVRANGE` (top-N, pagination), `ZREVRANK`+`ZSCORE` (player rank), `ZCARD` (size), `ZRANGE`+`ZREVRANGE` (nearby). Rebuild: fetch scores from score-service API, aggregate by userId, `DEL` + `ZADD` batch.

## 8. Kafka Design (summary)

| Aspect | Decision |
|---|---|
| Topic | `score-submitted` (3 partitions, key = `userId` ⇒ per-user ordering) |
| Event | JSON, envelope with `eventId`(UUID), `eventVersion`, `userId`, `sportCode`, `sportId`, `score`, `occurredAt` |
| Producer | score-service, after MySQL commit; acks=all local dev default |
| Consumer | leaderboard-service group `leaderboard-service`; manual/batch ack after Redis apply |
| Idempotency | dedupe on `eventId` (Redis `SET NX EX` marker + DB unique constraint fallback) |
| Failure | in-app retry ×3 backoff → `score-submitted.dlt` + error log alert; replay tooling documented |
| Versioning | additive fields only; consumers ignore unknown fields; breaking change ⇒ new topic suffix `.v2` |

## 9. WebSocket Design

- Endpoint: gateway route `/ws/**` → leaderboard-service STOMP endpoint `/ws/leaderboard` (SockJS fallback).
- Subscriptions: `/topic/leaderboard/global`, `/topic/leaderboard/{sportCode}`, personal `/queue/user-rank/{userId}`.
- Message shape:
```json
{
  "type": "LEADERBOARD_UPDATED",
  "scope": "f1",
  "entries": [{ "rank": 1, "userId": 101, "username": "nova", "score": 5120 }],
  "updatedAt": "2026-08-25T10:15:30Z"
}
```
- Broadcast coalescing: ≤ 1 message/second/topic under load; final state always delivered after burst settles.
- Lifecycle: heartbeat 10 s/10 s; server evicts dead sessions; client reconnects exponentially (1 s→30 s), snapshot-first resync.

## 10. Frontend Architecture

```
frontend/
 ├── src/
 │   ├── api/          # axios instance (baseURL = gateway), interceptors for refresh flow
 │   ├── auth/         # token storage, guards, role helpers
 │   ├── components/   # LeaderboardTable, RankBadge, ScoreForm, SportTabs...
 │   ├── pages/        # Login Register Dashboard Leaderboard Profile ScoreHistory Reports Admin
 │   ├── hooks/        # useLeaderboardSocket(topic) -> live entries
 │   ├── types/        # DTO mirrors of backend contracts
 │   └── App.tsx       # router
 └── vite.config.ts    # dev proxy -> http://localhost:8080 (gateway), never direct services
```

State: server state via small query layer + WebSocket patches; no global store required in V1. TypeScript `strict: true`.

## 11. Security Architecture

Layered (detail + threat model in [SECURITY.md](SECURITY.md)):
1. Edge: TLS termination point, JWT validation, CORS allow-list, header hygiene.
2. Identity: BCrypt(≥10) credentials; opaque hashed refresh tokens with rotation + reuse detection.
3. Propagation: signed JWT → gateway-injected trusted headers inside the private network.
4. Service: role re-checks, Bean Validation everywhere, parameterized JPA (no native string SQL).
5. Data: secrets only via env vars; no PII beyond username/display name in events/logs.

## 12. Error Handling

- RFC 7807 `application/problem+json` payloads from every service: `type, title, status, detail, instance, correlationId, errors[]`.
- One `@RestControllerAdvice` per service mapping domain exceptions → precise status codes (409 duplicates, 404 unknown ids, 422 rule violations, 429 throttled).
- Gateway converts auth failures to uniform 401/403 problems; never leaks stack traces.
- Client maps problem codes to typed UI messages; `correlationId` shown on failures for support.

## 13. Logging & Observability

- Structured JSON logs; MDC carries `correlationId` (generated at gateway filter, propagated as header), `userId` when authenticated.
- Never logged: passwords, tokens, secrets ([RULES.md §Security](RULES.md)).
- Every service exposes Spring Actuator `health` (Eureka heartbeats use it); metrics endpoints reserved for Phase 13+ hardening.

## 14. Scalability

| Axis | Mechanism |
|---|---|
| Stateless services | Scale replicas behind gateway (`lb://`) — auth/user/sport trivially |
| Read path | Redis serves O(log N+M); add Redis read replica later if needed |
| Write path | Kafka partitions scale consumers horizontally |
| Hot key (global board) | Single ZSET is fine to ~100k members; sharding by scope already inherent |
| Frontend | Static assets on CDN in production profile |

## 15. Performance Targets

From PRD NFR-01/02: top-100 read p95 < 50 ms (Redis), submission p95 < 300 ms to event-published. Broadcast latency ≤ 2 s (coalesced). These gate Phase 13 testing.

## 16. Availability & Failure Scenarios

| Failure | Blast radius | Behavior | Recovery |
|---|---|---|---|
| auth-service down | login/register/refresh fail | boards still readable (public GETs unaffected) | restart; tokens stay valid |
| score-service down | submissions fail | reads fine; clients show submit error | restart; nothing lost client-side (rejected before persist) |
| Redis down | live boards stale/unavailable | score writes still persist (MySQL first); WS broadcasts pause | rebuild job replays history into ZSETs |
| Kafka down | broadcasts stop | submissions succeed; Redis updated by producer-path sync write; consumer catches up on replay | resume from committed offsets |
| MySQL down | submissions fail fast | boards serve last-known Redis state | restore backup; history authoritative |
| Eureka down | no *new* instance discovery | existing routes continue via cached registry | restart registry |
| Duplicate event delivery | none | idempotent consumer skips seen `eventId` | n/a |
| Poison-pill event | one partition stalls without DLT | retried ×3 then routed to `.dlt`; pipeline continues | inspect DLT, fix, replay |

Design principle: **MySQL commit precedes side effects**, so any downstream component can be rebuilt from truth.

## 17. Future Scaling Strategy

1. Compose → single VM prod (TLS via reverse proxy) → Kubernetes with HPA on CPU/lag metrics.
2. Managed MySQL/Redis/Kafka replace containers; multi-AZ replication.
3. Introduce transactional outbox for publish atomicity if audit shows loss windows.
4. Add CQRS-style reporting replica of `score_db` if report queries grow.
5. Global distribution: regional Redis + event fan-out, if product demands sub-50 ms worldwide.
