# Technical Specifications

**Product:** Real-Time Leaderboard System
**Status:** Phase 0 — approved technology direction
**Related:** [PRD.md](PRD.md) · [DESIGN.md](DESIGN.md) · [SCHEMA.md](SCHEMA.md) · [RULES.md](RULES.md)

This document records *what* we build with and *why*. It is not a tutorial; rationale and constraints are the point.

---

## 1. Technology Matrix

| Layer | Technology | Version target | Why this, specifically |
|---|---|---|---|
| Language | **Java 17 (LTS)** | 17.x | Mandated. LTS with records, sealed types, pattern matching for switch, text blocks — enough modern syntax without Java 21-only features (virtual threads, sequenced collections). Every POM targets `maven.compiler.release=17`. |
| Framework | **Spring Boot 3.x** | 3.3.x | Native Jakarta EE baseline, first-class observability, mature security stack. Boot 3.x fully supports Java 17 as its floor — no tension with the Java 17 mandate. |
| Microservices | **Spring Cloud** | 2023.0.x | Release train aligned with Boot 3.3.x; provides Eureka, Gateway, OpenFeign integrations in one BOM so versions never drift. |
| Service discovery | **Spring Cloud Netflix Eureka** | via Spring Cloud | Services find each other by logical name (`lb://score-service`) instead of hardcoded host:port. Self-healing registry with heartbeats; trivially visible health dashboard at :8761. |
| Edge | **Spring Cloud Gateway** | via Spring Cloud | Single public entry point: routing, CORS, JWT validation filter, rate limiting hooks. Reactive (Netty) model fits an edge proxy that does I/O only. |
| Internal calls | **OpenFeign** | via Spring Cloud | Declarative REST clients with Eureka load balancing — e.g., Score Service → Sport Service to validate a sport without duplicating sport data or hardcoding URLs. |
| Security | **Spring Security + JWT (JJWT/nimbus)** | Boot-managed | Stateless auth fits microservices: gateway validates once, services trust claims after re-checking signature/expiry. BCrypt for password hashing (deliberately slow, salted). |
| Persistence | **MySQL 8.x + Spring Data JPA / Hibernate** | 8.x | ACID source of truth for users, sports, scores, history. JPA removes boilerplate; Hibernate handles dialect details. Database-per-service (see [SCHEMA.md §Ownership](SCHEMA.md)). |
| Hot state | **Redis 7.x — Sorted Sets** — **Implemented Phase 6** (leaderboard-service; Windows 5.0.14.1 used locally) | 7.x | Sorted Sets are *the* data structure for leaderboards: O(log N) inserts, O(log N) rank queries, O(log N + M) range reads. MySQL `ORDER BY` cannot serve live ranking at this cost. Redis also gives TTLs for daily/weekly windows and atomic ops (`ZINCRBY`). |
| Events | **Apache Kafka** | 3.x (KRaft) | Durable, replayable log decoupling score ingestion from fan-out (leaderboards, future fraud/analytics consumers). Absorbs bursts; consumers replay history on rebuild. |
| Real-time push | **Spring WebSocket + STOMP** | Boot-managed | Server-push so React clients never poll/refresh. STOMP adds pub/sub semantics (`/topic/...`) over one socket per client; SockJS fallback for restrictive networks. |
| Frontend | **React 18 + TypeScript 5 + Vite 5** | current stable | Component model suits leaderboard widgets; TS strict mode catches contract drift against backend DTOs; Vite gives instant HMR and simple builds. |
| API docs | **springdoc-openapi (Swagger UI)** | 2.x | Annotations → live OpenAPI 3 docs per service, aggregated through the gateway. Keeps docs honest because they're generated from code. |
| Build | **Maven 3.9** | 3.9.x | Multi-module reactor for 7 backend services; shared parent POM pins versions once. |
| Tests | **JUnit 5, Mockito, Spring Boot Test** | Boot-managed | Standard trio. Unit tests mock collaborators; `@SpringBootTest` slices exercise real wiring; optional Testcontainers for MySQL/Redis/Kafka integration profiles. |
| DevOps | **Docker + Docker Compose v2** | current | One command brings up MySQL, Redis, Kafka, 7 JVM services, frontend. Identical images are promotable beyond local later. |
| SCM/CI | **Git + GitHub (+ Actions placeholder)** | — | Monorepo strategy documented in [README](README.md#git-workflow); CI workflow scaffolded under `.github/workflows/ci.yml`, enabled when modules exist. |

### Implemented baseline (as of Phase 1A)

| Component | Version | Notes |
|---|---|---|
| Java toolchain | 17.0.12 LTS | `<java.version>17</java.version>` in every POM; no Java 21+ features anywhere |
| Spring Boot | 3.3.13 | latest 3.3.x patch release; Java 17 remains fully supported |
| Spring Cloud BOM | 2023.0.5 (Leyton) | imported via `spring-cloud-dependencies` in each service |
| Maven Wrapper | scripts 3.3.2 / Maven 3.9.9 | vendored per service (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) — no global Maven install required |

Version pairing rationale: Spring Boot 3.3.x runs on Java 17+, and the Spring Cloud 2023.0.x release train is the pairing officially tested against Boot 3.3.x — so this combination satisfies the Java 17 mandate with zero compatibility friction. Boot 4.x and any Java 21+ runtime are deliberately excluded.

Build layout decision (Phase 1A): each of the seven services is a **standalone** Spring Boot Maven project with its own wrapper and POM, making every service independently buildable (`backend/<svc>/.\mvnw.cmd`). The cross-service version pinning originally envisioned via a shared parent reactor POM is achieved by identical `<spring-cloud.version>2023.0.5</spring-cloud.version>` properties and identical starter-parent versions in all seven POMs.

> **Redis integration (Phase 6):** leaderboard-service uses Spring Data Redis with `StringRedisTemplate` for Sorted Set operations. Score-service notifies leaderboard-service via `@LoadBalanced RestTemplate` (not OpenFeign) after MySQL commit. Internal API protected by shared `X-Internal-Service-Secret` header. Windows Redis 5.0.14.1 (tporadowski port) used locally; all operations work correctly despite health check reporting DOWN.

Explicitly **not** used in V1: Spring Config Server (env vars suffice), Kubernetes (Compose first), virtual threads / any Java 21+ features, GraphQL.

---

## 2. Service Architecture

```
React (Vite dev server / static build)
        │  HTTPS/WS
        ▼
┌──────────────────┐
│   api-gateway    │ :8080   JWT check · routes · CORS · rate-limit hook
└───────┬──────────┘
        │ lb:// via Eureka (:8761)
┌───────┼───────────────┬───────────────┬───────────────┬───────────────┐
▼       ▼               ▼               ▼               ▼               ▼
auth    user            sport           score       leaderboard-service (Redis-backed)
:8081   :8082           :8083           :8084            :8085
 │MySQL │MySQL          │MySQL          │MySQL           │Redis ◄─ HTTP ◄─ score-service
 └─users│─user_profiles └─sports        ├─scores         └─WebSocket push
  └refresh_tokens                       └─score_history
```

Port plan (fixed for local dev, injected via env in Docker):

| Service | Port | DB schema owned |
|---|---|---|
| service-registry (Eureka) | 8761 | — |
| api-gateway | 8080 | — |
| auth-service | 8081 | `auth_db` (users, refresh_tokens) |
| user-service | 8082 | `user_db` (user_profiles) |
| sport-service | 8083 | `sport_db` (sports) |
| score-service | 8084 | `score_db` (scores, score_history) |
| leaderboard-service | 8085 | Redis keyspace (+ reporting reads of `score_db` views via API, not direct DB access) |

## 3. Communication Patterns

### 3.1 Synchronous REST (client-facing)
All external traffic: `React → API Gateway → {service}`. The gateway is the only public surface; frontend code contains zero direct microservice URLs ([RULES.md §Architecture](RULES.md)).

### 3.2 Synchronous internal (service-to-service)
- Transport: HTTP via **OpenFeign** + Eureka `lb://` resolution.
- Used only where an answer is needed *now*: Score Service validates the sport (and trusts JWT claims for user identity). Responses cached briefly (Caffeine/Redis) to avoid chatty calls.
- Anti-pattern guard: no synchronous chains deeper than one hop on the submission path.

### 3.3 Asynchronous events
- Transport: **Kafka**, topic `score-submitted`.
- Producer: score-service, via Transactional Outbox pattern (`outbox_events` table, `@TransactionalEventListener(AFTER_COMMIT)`, `@Transactional(REQUIRES_NEW)`, `@Scheduled` poller 5s interval).
- Consumer group: `leaderboard-service`; key = `userId` (per-user ordering); idempotent on `eventId`.
- Why async: the write path must not block on fan-out work (Redis updates, WS broadcasts), and future consumers (analytics, fraud) plug in without touching score-service.

### 3.4 Real-time push
Leaderboard Service → WebSocket (STOMP) topics:
- `/topic/leaderboard/global`
- `/topic/leaderboard/{sportCode}` (derived from data — new sports get topics automatically)
- `/queue/user-rank/{userId}` (personal rank nudges)

### 3.5 Discovery
Every service registers with Eureka and resolves peers by name. No service knows another's IP/port. Gateway routes use `lb://<service-id>`.

## 4. Caching Strategy

| What | Where | TTL / invalidation |
|---|---|---|
| Current standings (all scopes) | Redis ZSETs | Authoritative hot copy; windowed keys expire per [SCHEMA.md](SCHEMA.md) retention table |
| Sport catalog (enabled list) | In-process cache in score/user-facing paths | 60 s TTL or explicit eviction on admin change event |
| Validated sport lookups | Feign response cache | 60 s TTL |
| JWT revocation checks | Not cached in V1 (stateless tokens, short TTL) | Refresh-token rotation covers logout semantics |

## 5. Persistence Strategy

- **MySQL = durable truth.** Scores, history, users, refresh tokens, sports. Every Redis mutation is reconstructible from MySQL.
- **Redis = fast now.** Live boards answer in single-digit ms; treated as a cache-with-ranking-semantics, rebuilt via bootstrap job (`ZADD`) from history when cold or corrupted.
- **Kafka = durable change log.** Enables replay/rebuild and future consumers; retained ≥ 7 days locally.
- Cross-service data access is by API or event only — no shared tables between services ([SCHEMA.md §Ownership](SCHEMA.md)).

## 6. Security Architecture

1. Credentials: BCrypt-hashed in `auth_db`; complexity enforced at registration.
2. Tokens: HS256 JWT (secret from env, ≥ 32 bytes), short-lived access token + rotating opaque refresh token (hashed at rest).
3. Edge enforcement: gateway filter validates JWT on every protected route and forwards identity claims downstream (`X-User-Id`, `X-User-Role` headers set by gateway, never trusted from outside).
4. Service enforcement: each service re-validates required claims; ADMIN endpoints demand role claim.
5. Transport: TLS terminated at gateway in production profile; plain HTTP acceptable only inside the local Compose network.
6. Secrets: environment variables exclusively — `.env` (git-ignored) locally, GitHub Secrets/deployment env in hosted environments. See [SECURITY.md](SECURITY.md).

## 7. Deployment Architecture

Local (Phase 14 deliverable):

```
docker compose up --build
  ├── mysql:8        (healthcheck-gated)
  ├── redis:7        (AOF enabled)
  ├── kafka:3.x      (KRaft, single broker)
  ├── service-registry → api-gateway → auth/user/sport/score/leaderboard (depends_on infra healthy)
  └── frontend       (Vite build served by nginx container)
```

Startup order enforced via Compose `depends_on` + healthchecks; services retry Eureka/Kafka connections rather than crash-looping. Production-grade concerns (TLS, replicas, managed infra) are documented in [DESIGN.md §Deployment](DESIGN.md) but intentionally deferred.

## 8. Testing Strategy

| Level | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Services/mappers/rank math; no Spring context |
| Context load | `@SpringBootTest` `contextLoads()` | **Implemented per service in Phase 1A**; Eureka client disabled in each service's `src/test/resources/application.yml` so contexts boot offline |
| Web slice | `@WebMvcTest` | Controllers, validation, error payloads |
| Integration | `@SpringBootTest` (+ Testcontainers where available) | Repository layer, Kafka round-trip, Redis ZSET behavior |
| End-to-end | Manual scripted flows in Phase 13; API-level happy paths via Swagger/http files | Register → login → submit → board update |

Coverage gate ≥ 70% on domain logic (JaCoCo, introduced in Phase 13).

## 9. API Documentation Strategy

- springdoc-openapi per service at `/v3/api-docs` + `/swagger-ui.html`, proxied by the gateway.
- DTOs annotated once; docs generated, never hand-maintained.
- Endpoint catalog maintained in [docs/api/](docs/api/) as implementation lands.

## 10. Compatibility Constraints

- **Java 17 everywhere**: parent POM `<maven.compiler.release>17</maven.compiler.release>`; forbidden: virtual threads API, sequenced collections, record patterns (Java 21+).
- **Boot 3.3.x + Cloud 2023.0.x** pinned in the parent BOM; child POMs inherit versions.
- Frontend Node.js 20 LTS; TypeScript `strict: true` ([RULES.md §Frontend](RULES.md)).
- **As built:** Boot 3.3.13 + Cloud BOM 2023.0.5 are pinned identically in all seven standalone service POMs (see "Implemented baseline" above).
