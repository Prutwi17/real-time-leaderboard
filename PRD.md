# Product Requirements Document (PRD)

**Product:** Real-Time Leaderboard System
**Repository:** `real-time-leaderboard` (monorepo)
**Reference project:** <https://roadmap.sh/projects/realtime-leaderboard>
**Status:** Draft — Phase 0 (Documentation & Architecture)
**Related documents:** [TECHSPECS.md](TECHSPECS.md) · [DESIGN.md](DESIGN.md) · [APPFLOW.md](APPFLOW.md) · [SCHEMA.md](SCHEMA.md)

---

## 1. Product Overview

The Real-Time Leaderboard System is a multi-sport platform where authenticated users submit scores for sports they participate in and watch leaderboards update in real time without refreshing the page.

The system is built as a set of cooperating microservices behind a single API gateway. Current leaderboard state is served from Redis Sorted Sets, permanent score history is stored in MySQL, score events flow through Kafka, and updates are pushed to connected clients over WebSocket.

Version 1 ships with Football, Cricket, and Formula 1. Sports are configuration data stored in MySQL — the architecture does not hardcode them, so adding Tennis or Basketball later requires no redesign.

## 2. Problem Statement

Ranking systems for competitive activities typically suffer from three problems:

1. **Stale data.** Leaderboards are computed on page load from relational queries (`ORDER BY ... LIMIT N`), which becomes slow and inconsistent as participant counts grow.
2. **Poor scalability.** A monolithic application couples authentication, scoring, ranking, and reporting into one deployable unit that cannot scale or fail independently.
3. **No extensibility.** Systems hardcoded around specific sports cannot absorb new sports, scoring policies, or time-based competition windows.

This product solves these problems with an event-driven microservice architecture: scores are persisted durably (MySQL), ranked in memory at high speed (Redis Sorted Sets), propagated asynchronously (Kafka), and pushed live to clients (WebSocket).

## 3. Goals

- G1. Provide secure user registration, login, and role-based access (USER, ADMIN).
- G2. Allow users to submit scores for any enabled sport via a single REST API.
- G3. Maintain global, per-sport, daily, weekly, and (for F1) season leaderboards backed by Redis Sorted Sets.
- G4. Push leaderboard changes to connected clients within ~2 seconds of score submission.
- G5. Keep permanent, auditable score history in MySQL for reports and dispute resolution.
- G6. Treat sports as configurable data managed by admins through the Sport Service.
- G7. Expose all functionality through one public API Gateway entry point.
- G8. Provide period-based reporting (top players per day/week/custom range).
- G9. Run the full stack locally with a single `docker compose up --build`.

## 4. Non-Goals

- NG1. Live match tracking, play-by-play commentary, or video integration.
- NG2. Payments, prizes, wagering, or real-money competitions.
- NG3. Anti-cheat beyond validation, rate limiting, and anomaly reporting (a dedicated fraud engine is future scope).
- NG4. Native mobile applications (the REST/WebSocket API will support them, but V1 is web only).
- NG5. Multi-region active-active deployment.
- NG6. Social features (friends, chat, guilds) in Version 1.
- NG7. OAuth/social login providers in Version 1.

## 5. Target Users

| Persona | Description | Primary value |
|---|---|---|
| Competitive player | Registers, submits scores, checks own rank | Instant rank/score feedback, fair rankings |
| Casual spectator | Browses leaderboards without submitting | Live-updating standings |
| Competition admin | Manages sports, monitors activity | Enable/disable sports, reports, moderation |
| Platform operator / developer | Runs and extends the system | Clear observability, documented APIs |

## 6. User Roles

### USER
- Registers and authenticates with email + password.
- Submits scores to enabled sports.
- Views global/sport/daily/weekly leaderboards.
- Views own rank, score, and score history.
- Manages own profile.

### ADMIN
- Everything USER can do, plus:
- Create, update, enable/disable, and retire sports.
- Access admin dashboard and reports.
- View aggregate statistics across users and sports.

Roles are static in V1 (no self-service elevation). Role claims travel inside the JWT; authorization is enforced at the API Gateway and re-checked inside each service.

## 7. Supported Sports (Version 1)

| Sport | Code | Seeded in `sports` table | Notes |
|---|---|---|---|
| Football | `FOOTBALL` | Yes | Generic score submission |
| Cricket | `CRICKET` | Yes | Generic score submission |
| Formula 1 | `F1` | Yes | Season-scoped leaderboard supported |

**Extensibility rule:** no service may hardcode these codes in business logic. Codes live exclusively in the `sports` table (owned by Sport Service). Adding Tennis means inserting a row — new Redis keys (`leaderboard:tennis`), WebSocket topics, and routes derive automatically from the code.

## 8. Core Features

1. Registration and login with BCrypt-hashed passwords.
2. JWT access tokens + rotating refresh tokens.
3. Role-based access control (USER / ADMIN).
4. User profiles and personal statistics.
5. Admin-managed sports catalog with enable/disable.
6. Score submission with validation (user active, sport enabled, score range).
7. Durable score storage (MySQL) + high-speed current standings (Redis Sorted Sets).
8. Global, per-sport, daily, weekly, and F1 season leaderboards.
9. Personal rank lookup (`ZREVRANK`) and score lookup (`ZSCORE`).
10. Kafka event pipeline (`score-submitted`) driving leaderboard recomputation.
11. WebSocket (STOMP) push updates to connected React clients.
12. Score history page per user.
13. Reports: top players per period, participation counts, custom ranges.
14. Admin panel: sport management, system overview.
15. Swagger/OpenAPI documentation for every service.

## 9. Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-01 | The system shall register a user with unique email and username; passwords stored only as BCrypt hashes. | Must |
| FR-02 | The system shall issue a JWT access token and a refresh token on successful login. | Must |
| FR-03 | The system shall allow obtaining a new access token using a valid refresh token and revoke refresh tokens on logout. | Must |
| FR-04 | The gateway shall reject requests without a valid JWT except public endpoints (`/api/auth/**` register/login/refresh, Swagger). | Must |
| FR-05 | The system shall support roles USER and ADMIN and enforce them per endpoint. | Must |
| FR-06 | Users shall retrieve and update their own profile (display name, avatar URL, bio). | Should |
| FR-07 | Admins shall create a sport with code, name, description, unit label, and active flag; codes are unique. | Must |
| FR-08 | Admins shall enable/disable a sport; disabled sports reject new submissions but retain history. | Must |
| FR-09 | Any client shall list enabled sports without authentication; full management requires ADMIN. | Must |
| FR-10 | Authenticated users shall submit `{sportId, score}`; the service validates user status, sport existence/enabled state, and score bounds before persisting. | Must |
| FR-11 | On valid submission the system shall write the score to MySQL, update the relevant Redis Sorted Sets, and publish a `score-submitted` event to Kafka. | Must |
| FR-12 | The Leaderboard Service shall consume score events idempotently (duplicate `eventId` ignored). | Must |
| FR-13 | The system shall expose global, per-sport, daily (`{date}`), weekly (`{year}-W{week}`), and season (F1) leaderboards with rank, userId, username, and score. | Must |
| FR-14 | A user shall query their own rank and score for any leaderboard scope. | Must |
| FR-15 | The system shall push leaderboard delta/full snapshots over WebSocket within 2 seconds of a consumed event (coalesced to at most ~1 update/second per topic). | Must |
| FR-16 | Users shall view their complete paginated score history from MySQL. | Must |
| FR-17 | Reports shall provide top-N players for a given period and sport, sourced from persistent data. | Should |
| FR-18 | Every service shall publish OpenAPI docs reachable through the gateway. | Should |
| FR-19 | All write endpoints shall validate input (Bean Validation) and return consistent RFC 7807-style error payloads. | Must |
| FR-20 | Score submission shall be rate-limited per user per sport to blunt abuse. | Should |

## 10. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | Leaderboard reads (top 100) p95 < 50 ms from Redis at 10k members. |
| NFR-02 | Performance | Score submission end-to-end (gateway → persistence → event published) p95 < 300 ms. |
| NFR-03 | Scalability | Services horizontally scalable; stateless except Leaderboard Service's in-memory caches (Redis-backed). |
| NFR-04 | Availability | No single service outage takes down unrelated capabilities (e.g., auth down ⇒ login fails; cached leaderboards still readable). |
| NFR-05 | Security | OWASP Top 10 mitigations applied; secrets only via environment variables; see [SECURITY.md](SECURITY.md). |
| NFR-06 | Data integrity | Scores never lost: MySQL commit precedes Kafka publish; failed publishes retried; consumer idempotent. |
| NFR-07 | Observability | Structured JSON logs with correlation IDs; health endpoints on every service; visible in Eureka. |
| NFR-08 | Compatibility | Java 17 LTS everywhere; Spring Boot 3.x; no Java 21-only language features. |
| NFR-09 | Maintainability | Microservice boundaries per [DESIGN.md](DESIGN.md); DTO-only REST contracts; constructor injection. |
| NFR-10 | Testability | Unit tests (JUnit 5 + Mockito) and integration tests (Spring Boot Test) per service; coverage gate ≥ 70% on core logic. |
| NFR-11 | Portability | Entire environment reproducible locally through Docker Compose. |
| NFR-12 | Documentation | Docs kept in-repo and updated with each feature branch. |

## 11. User Stories

### Player
- US-01: As a visitor, I can register an account so I can participate.
- US-02: As a player, I can log in and stay logged in with tokens so I don't re-authenticate constantly.
- US-03: As a player, I can submit a score for a sport so my performance counts.
- US-04: As a player, I can see the global leaderboard update live while I watch it.
- US-05: As a player, I can open `/leaderboard/f1` and see only Formula 1 standings.
- US-06: As a player, I can check "what is my rank?" for any sport instantly.
- US-07: As a player, I can browse my past submissions on the score history page.
- US-08: As a player, I can edit my profile display name and avatar.

### Spectator
- US-09: As a spectator, I can view leaderboards without logging in (read-only public view).

### Admin
- US-10: As an admin, I can add a new sport (e.g., Tennis) without a deployment.
- US-11: As an admin, I can disable a sport to pause submissions during maintenance.
- US-12: As an admin, I can pull a "Top 50 players this week" report.
- US-13: As an admin, I can review total submissions and active players per sport.

## 12. Acceptance Criteria

| Story | Acceptance criteria |
|---|---|
| US-01 | Duplicate email rejected with 409; password ≥ 8 chars enforced; success returns 201 and auto-login issues tokens. |
| US-02 | Access token expires after configured TTL; refresh flow returns new pair; logout invalidates the refresh token server-side. |
| US-03 | Submission to disabled/nonexistent sport → 400/404 with clear error; valid submission → 201 with persisted record; duplicate rapid submissions throttled (429). |
| US-04 | With two browser tabs open, a score submitted by another user appears in both tabs within 2 s without reload. |
| US-05 | Route renders sport-specific board driven by sport code from the API, not a hardcoded component. |
| US-06 | Rank endpoint returns correct position matching `ZREVRANK`, including ties handled consistently (same score = same rank, ordered by member name). |
| US-07 | History lists newest first, paginated 20/page, showing sport, score, and timestamp. |
| US-09 | Unauthenticated GET leaderboard succeeds; unauthenticated POST score returns 401. |
| US-10 | New sport appears in sport list and accepts submissions immediately; its leaderboard keys/topics are derived from the code. |
| US-12 | Report numbers reconcile with MySQL aggregates for the same window. |

## 13. Real-Time Requirements

- Transport: WebSocket with SockJS fallback, STOMP subprotocol, endpoint `/ws/leaderboard` exposed via the gateway.
- Update latency target: ≤ 2 s from Kafka consumption to client render.
- Coalescing: at most one broadcast per second per topic under burst load (debounce), with final state always delivered.
- Reconnection: client auto-reconnects with exponential backoff (1 s → 30 s cap); on reconnect it fetches a fresh snapshot over REST before resuming subscriptions.
- Degradation: if WebSocket is unavailable, the UI polls the REST leaderboard every 15 s (documented fallback, not silent failure).
- Details in [DESIGN.md §WebSocket](DESIGN.md) and [APPFLOW.md §Flow 7](APPFLOW.md).

## 14. Leaderboard Requirements

Scopes required at launch:

| Scope | Key pattern | Source |
|---|---|---|
| Global (all-time) | `leaderboard:global` | Sum of all submissions |
| Per-sport (all-time) | `leaderboard:{sportCode}` e.g. `leaderboard:f1` | Sum of that sport's submissions |
| Daily | `leaderboard:{sportCode}:daily:{yyyy-MM-dd}` (+ `leaderboard:global:daily:{yyyy-MM-dd}`) | That day's submissions |
| Weekly | `leaderboard:{sportCode}:weekly:{yyyy}-W{ww}` (+ global variant) | ISO week's submissions |
| Season (F1) | `leaderboard:f1:season:{yyyy}` | Calendar-year aggregation |

Rules:
- Ranking engine is Redis Sorted Sets exclusively for *current* standings; MySQL `ORDER BY` is used only for historical reports.
- Ties broken deterministically (equal scores share rank; ordering by member lexicographically).
- Time-windowed keys expire after a retention period (see [SCHEMA.md §Redis](SCHEMA.md)) — expired windows remain answerable from MySQL history/reports.
- Aggregation semantics: default policy is **cumulative sum** (`ZINCRBY`). Rebuild/bootstrap uses `ZADD` from MySQL history.

## 15. Reporting Requirements

- RPT-1: Top-N players for any sport over a date range (from `score_history`, aggregated in SQL).
- RPT-2: Participation metrics: distinct active users, submission counts per sport per day/week.
- RPT-3: Exportable CSV for admin reports (nice-to-have).
- RPT-4: Reports are eventually consistent with live boards (bounded by Kafka lag) and must always agree with MySQL source-of-truth history.
- Reporting endpoints live behind `/api/reports/**` routed to the Leaderboard Service (reporting component) per the gateway route plan.

## 16. Authentication Requirements

- AUTH-1: Email + password registration; BCrypt strength ≥ 10; plain passwords never logged or stored.
- AUTH-2: JWT access token (HS256), secret from `JWT_SECRET` env (≥ 32 bytes), TTL from `JWT_EXPIRATION`.
- AUTH-3: Opaque refresh token, stored hashed in `refresh_tokens`, rotated on use, revoked on logout, expiring after 7 days idle.
- AUTH-4: Token claims: `sub` (userId), `username`, `role`, `iat`, `exp`, `jti`.
- AUTH-5: API Gateway performs signature/expiry validation centrally; services re-validate claims they rely on (defense in depth).
- AUTH-6: Brute-force mitigation: progressive lockout/backoff on repeated failures per account+IP.
- Full detail: [SECURITY.md](SECURITY.md).

## 17. Future Scope (post-V1)

- Additional sports (Tennis, Basketball) purely via configuration.
- Scoring policy engine per sport (best-score vs cumulative vs decayed).
- Achievements/badges and streaks.
- Friend following and private leagues.
- OAuth2 social login (Google/GitHub) alongside local auth.
- Fraud/anomaly detection service consuming the same Kafka stream.
- Kubernetes deployment with HPA; multi-AZ Redis/Kafka.
- Mobile apps on the same public API.
- GraphQL aggregation layer (optional) if client fan-out demands it.

## 18. Risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|---|
| R-01 | Redis data loss (restart w/o persistence) corrupts live boards | High | Medium | AOF/RDB enabled in Compose; documented rebuild procedure from MySQL history (`ZADD` bootstrap). |
| R-02 | Kafka consumer lag delays real-time feel | Medium | Medium | Partition keying by userId, coalesced broadcasts, lag monitoring. |
| R-03 | Duplicate score events double-count scores | High | Medium | Idempotent consumer keyed on `eventId` + unique constraint on history event id. |
| R-04 | JWT secret leakage | Critical | Low | Env-var only, never in Git; rotation runbook in SECURITY.md. |
| R-05 | Score abuse/botting skews boards | High | Medium | Rate limiting, input bounds, anomaly report in Phase 12+. |
| R-06 | Microservice sprawl overwhelms solo development | Medium | High | Strict phase plan ([IMPLEMENTATIONPLAN.md](IMPLEMENTATIONPLAN.md)); shared parent POM; minimal per-service footprint. |
| R-07 | Gateway becomes single point of failure | Medium | Medium | Multiple replicas behind LB in production profile; documented. |
| R-08 | Clock skew breaks daily/weekly keys | Medium | Low | Single UTC clock discipline; keys derived from event timestamps, not wall clock at consume time. |

## 19. Success Criteria

1. All Must-have functional requirements (FR-01…FR-15, FR-19) demonstrably working end-to-end locally.
2. Two-browser live test passes (US-04 acceptance criteria) with updates ≤ 2 s.
3. Leaderboard read/write paths exercised by automated tests; core logic coverage ≥ 70%.
4. `docker compose up --build` brings up infra + 7 services + frontend with seeded sports (Football, Cricket, F1).
5. No plaintext secrets anywhere in the repository; `.env` git-ignored; `.env.example` accurate.
6. README contains the Roadmap.sh project URL and honest, non-exaggerated status.
7. Project submitted to Roadmap.sh with screenshots of running system.
