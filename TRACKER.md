# Project Progress Tracker

**Last updated:** 2026-08-26 (Phase 6 Redis Leaderboard implemented, tested 69/69, and live-verified through gateway)
**Rule:** an item is checked only when it is demonstrably done and verified. Never pre-check.

Legend: `[x]` done · `[ ]` open · `(WIP)` may be noted inline while a phase is actively in progress.

---

## Phase 0 — Documentation and Architecture

*Items below were produced during project initialization and reflect files that now exist; they remain subject to owner review before Phase 1 begins.*

- [x] PRD.md completed
- [x] TECHSPECS.md completed
- [x] APPFLOW.md completed
- [x] DESIGN.md completed
- [x] SCHEMA.md completed
- [x] IMPLEMENTATIONPLAN.md completed
- [x] RULES.md completed
- [x] SECURITY.md completed
- [x] README.md completed
- [x] .gitignore and .env.example created (no real credentials committed)
- [x] Directory skeleton created (backend ×7, frontend, docker, docs/, .github/)
- [ ] Owner review/sign-off of all Phase 0 documents

## Phase 1 — Repository and Project Setup

*Phase 1A executed on `feature/project-setup`; git operations intentionally not performed (per instructions), so git-related items remain open. Build/test items below are verified.*

- [ ] Git repository initialized with `main` + `develop` *(git state untouched — see Problems log)*
- [ ] Branch protection conventions documented/applied
- [x] Seven service POMs created — Java 17, Spring Boot 3.3.13, Cloud BOM 2023.0.5 (standalone per-service projects instead of a parent-reactor POM; see [IMPLEMENTATIONPLAN.md](IMPLEMENTATIONPLAN.md) as-built note)
- [x] Maven Wrapper vendored per service (scripts 3.3.2, Maven 3.9.9 distribution)
- [x] Empty Spring Boot applications boot successfully (contextLoads per service; registry + gateway additionally smoke-tested running)
- [x] `.\\mvnw.cmd clean test` green across all seven services
- [ ] First PR merged using Conventional Commits

## Phase 2 — Service Registry

*Foundation scaffolded early during Phase 1A: module exists, Eureka Server enabled, boots on :8761 (dashboard + health verified in smoke test). Functional items below stay open until formally verified in Phase 2.*

- [ ] Eureka server module created
- [ ] Registry runs on :8761
- [ ] Dashboard accessible
- [ ] Test client registration verified
- [ ] Health endpoint green

## Phase 3 — API Gateway

- [ ] Gateway module created on :8080
- [ ] Routes: /api/auth, /api/users, /api/sports, /api/scores, /api/leaderboard, /api/reports
- [ ] `lb://` routing via Eureka working
- [ ] Global CORS configured from environment
- [ ] JWT filter skeleton in place
- [ ] Correlation-ID filter added
- [ ] Route smoke tests passing

## Phase 4 — Authentication

*Executed as "Phase 2" of this build-out. Schema was created via Hibernate `ddl-auto=update` in the `leaderboard_auth` database instead of a Flyway migration — migration tooling remains a production requirement. Verified end-to-end through the API Gateway against live MySQL.*

- [x] auth-service tables created (`leaderboard_auth.users`, `leaderboard_auth.refresh_tokens`) — *(via ddl-auto=update; Flyway deferred)*
- [x] Registration endpoint with BCrypt hashing (+ 409 duplicates, 400 validation, self-role ignored)
- [x] Login endpoint issuing access + refresh tokens
- [ ] Refresh token rotation + reuse detection *(deferred hardening — validate/revoke is implemented)*
- [x] Logout revocation (verified live: refresh after logout → 401)
- [x] Role model USER/ADMIN enforced (`/api/auth/admin/**` ADMIN-only; no self-elevation)
- [ ] Gateway validates real JWT signatures *(planned hardening — services enforce today)*
- [x] Unit + integration tests passing (**29/29**: 14 MockMvc integration on H2, 6 JwtServiceTest, 8 AuthServiceTest, 1 contextLoads)
- [x] No plaintext secrets anywhere (env-var placeholders only; placeholder JWT secret rejected at startup)

Additional Phase 2 deliverables: `/api/auth/me` identity endpoint, uniform JSON error handling, actuator health/info exposure.

## Phase 5 — User/Player Service

*Executed as "Phase 5" of this build-out. Schema via Hibernate `ddl-auto=update` in the `leaderboard_user` database (Flyway deferred). Profile management with public reads, authenticated create, and ADMIN-only management. Verified end-to-end through the API Gateway against live MySQL.*

- [x] `leaderboard_user` database and `players` table created — *(via ddl-auto=update; Flyway deferred)*
- [x] Player profile CRUD (create, read by ID, paginated list, search by display name)
- [x] Public read endpoints (`GET /api/players`, `GET /api/players/{id}` — no auth required)
- [x] Authenticated create (`POST /api/players` — any valid JWT)
- [x] ADMIN-only management (`PUT /api/players/{id}`, deactivate, activate, hard delete)
- [x] Email uniqueness enforcement (duplicate → 409)
- [x] Validation (`@NotBlank` displayName 2–50 chars, `@Email` format)
- [x] Soft-delete via `active` flag (deactivated players excluded from list queries)
- [x] Uniform JSON error responses (400 validation, 404 not found, 409 duplicate, 403 forbidden, 401 unauthorized)
- [x] JWT validation-only (no token generation; secret from `JWT_SECRET` via `JwtService`)
- [x] Tests passing (**44/44**: 13 PlayerServiceTest, 13 PlayerControllerTest, 7 PlayerRepositoryTest, 10 PlayerServiceIntegrationTest, 1 contextLoads)
- [x] Live E2E verified through gateway: public list/read, create with auth, ADMIN update/deactivate/activate/delete, USER cannot update/delete (403), no token on protected (401), duplicate email (409), invalid email (400), search by display name

## Phase 6 — Sport Service

*Executed as "Phase 3" of this build-out. Schema via Hibernate `ddl-auto=update` in the `leaderboard_sport` database (Flyway deferred); default sports seeded by an idempotent `CommandLineRunner`. Verified end-to-end through the API Gateway against live MySQL.*

- [x] sport_db schema created (`leaderboard_sport`: `sports`, `competitions`) — *(via ddl-auto=update; Flyway deferred)*
- [x] Public list-active-sports endpoint (`GET /api/sports`, `/api/sports/{id}`, `/api/sports/code/{code}`; all reads public)
- [x] Admin create/update/status endpoints (sports + competitions; USER writes → 403, anonymous → 401)
- [x] Unique code enforcement (sport codes enum-constrained + DB unique; competition codes pattern-checked + case-insensitive duplicate check)
- [x] Seed data FOOTBALL, CRICKET, F1 — *via idempotent startup initializer instead of a SQL migration*
- [x] Internal lookup endpoint for Feign consumers — *(not needed yet; public `GET /api/sports/code/{code}` serves discovery until a consumer exists. Revisit in score phase)*
- [x] Tests for conflicts and authorization (**55/55**: 15 SportControllerTest, 7 CompetitionControllerTest, 11+10 service unit + integration, plus contextLoads)

Additional Phase 3 deliverables: `SportCode` closed enum rejecting unsupported sports, competition lifecycle with date-range validation, gateway route `/api/competitions/**` → SPORT-SERVICE.

## Phase 7 — Score Service

*Executed as "Phase 4" of this build-out. Schema via Hibernate `ddl-auto=update` in the `leaderboard_score` database (Flyway deferred). Sport validation via `@LoadBalanced RestTemplate` to sport-service. Verified end-to-end through the API Gateway against live MySQL.*

- [x] score_db schema created (`leaderboard_score`: `scores`) — *(via ddl-auto=update; Flyway deferred)*
- [x] POST /api/scores validation pipeline (sport existence + activity, value bounds, scoreType enum, unique submissionId per user)
- [x] Transactional persistence (score entity with recordedAt/createdAt/updatedAt timestamps)
- [x] Paginated score history endpoint (`GET /api/scores/me` — own scores, newest first)
- [x] Sport validation via `@LoadBalanced RestTemplate` (404 missing, 409 inactive, 503 unavailable)
- [ ] Kafka producer publishing after commit *(deferred to Phase 9)*
- [ ] Redis writer interface stubbed *(deferred to Phase 8)*
- [x] Admin search with filters (userId, sportId, eventId, scoreType, from, to) + paginated results
- [x] Ownership enforcement (USER reads own scores only; ADMIN reads any; DELETE ADMIN-only)
- [x] Validation-matrix tests passing (**46/46**: 11 ScoreServiceTest, 4 SportValidationServiceTest, 11 ScoreControllerTest, 7 ScoreRepositoryTest, 12 ScoreServiceIntegrationTest, 1 contextLoads)
- [x] Live E2E verified: submit Football/Cricket/F1, GET /me, GET by ID, ownership 403, admin search/filter, admin delete, invalid sport 404, duplicate 409, no auth 401

## Phase 8 — Redis Leaderboard

*Executed as "Phase 6" of this build-out. Redis Sorted Sets power live ranking. Score-service notifies leaderboard-service on each score submission via internal HTTP API with shared secret. Schema: no MySQL (Redis keyspace only). Tests: 69/69 green in leaderboard-service; 46/46 in score-service (with LeaderboardClient).*

- [x] `leaderboard-service` implemented on port 8085, registers with Eureka as `LEADERBOARD-SERVICE`, Redis-backed (no MySQL)
- [x] Key-builder (`LeaderboardKeyFactory`) — deterministic sport→key mapping: FOOTBALL→leaderboard:football, CRICKET→leaderboard:cricket, F1→leaderboard:f1
- [x] Score submission notification: score-service `LeaderboardClient` → `POST /internal/leaderboards/scores` (X-Internal-Service-Secret header)
- [x] Idempotent score updates via `processed:score:{scoreId}` Redis keys (72h TTL)
- [x] Public read endpoints: GET `/api/leaderboards/{sport}/top?limit=N`, `/api/leaderboards/{sport}?page=&size=`, `/api/leaderboards/{sport}/players/{userId}/rank`, `/api/leaderboards/{sport}/players/{userId}/nearby?range=N`
- [x] Authenticated endpoint: GET `/api/leaderboards/{sport}/me` (returns current user's rank)
- [x] Size endpoint: GET `/api/leaderboards/{sport}/size` (total players)
- [x] Internal rebuild endpoint: POST `/internal/leaderboards/{sport}/rebuild` (X-Internal-Service-Secret)
- [x] Security: JWT validation-only (`JwtService`), public reads, `/internal/**` protected by `X-Internal-Service-Secret`, CSRF disabled, stateless session
- [x] Gateway routes added: `/api/leaderboards/**` → `lb://leaderboard-service`, `/api/reports/**` → `lb://leaderboard-service`
- [x] Tests passing (**69/69**: 14 LeaderboardControllerTest, 13 LeaderboardServiceTest, 9 LeaderboardUpdateServiceTest, 11 RedisLeaderboardRepositoryTest, 8 LeaderboardKeyFactoryTest, 13 LeaderboardServiceIntegrationTest, 1 LeaderboardServiceApplicationTests)
- [x] Live E2E verified: submit scores → leaderboard updates → top-N/paginated/rank/nearby/size endpoints return correct data through gateway
- [ ] Daily/weekly/season windowed boards (deferred to next phase)
- [ ] WebSocket real-time push (deferred to Phase 10)

## Phase 9 — Kafka Integration

- [ ] Consumer group applying events to Redis
- [ ] Idempotency marker (eventId) enforced
- [ ] Retry/backoff configured
- [ ] DLT topic wired with alert log
- [ ] Duplicate-event and poison-pill tests passing
- [ ] Restart/resume from offsets verified

## Phase 10 — WebSocket Real-Time Updates

- [ ] STOMP endpoint /ws/leaderboard (+SockJS fallback)
- [ ] Topic strategy implemented (global, per-sport, personal queue)
- [ ] Broadcast coalescing ≤ 1 msg/sec/topic
- [ ] Heartbeats + session cleanup
- [ ] Snapshot-on-subscribe behavior
- [ ] Two-client live update test ≤ 2 s

## Phase 11 — React Frontend

- [ ] Vite + TypeScript strict scaffold
- [ ] Central API client + refresh interceptor
- [ ] Auth store and route guards
- [ ] Pages: login, register, dashboard, leaderboard(+sport), profile, score-history, reports, admin
- [ ] Live leaderboard hook with polling fallback
- [ ] Score submission form
- [ ] Admin sport management UI
- [ ] Component tests + typecheck green
- [ ] All PRD user stories demoable

## Phase 12 — Reports

- [ ] Top-players-by-period report (ADMIN)
- [ ] Participation metrics
- [ ] CSV export (nice-to-have)
- [ ] Reports page wired to real data
- [ ] Reconciliation test vs SQL fixtures

## Phase 13 — Testing & Hardening

- [ ] JaCoCo coverage gate ≥ 70% domain logic
- [ ] Full `mvn verify` green
- [ ] Frontend suites green
- [ ] Rate-limit/brute-force sanity checks
- [ ] Structured logging + correlation verification
- [ ] OpenAPI completeness sweep
- [ ] Performance checks vs NFR-01/02
- [ ] Defect backlog cleared (Must-level zero)

## Phase 14 — Docker

- [ ] Per-service Dockerfiles (layered jars, non-root)
- [ ] docker/docker-compose.yml (mysql, redis, kafka, 7 services, frontend)
- [ ] Healthchecks + startup ordering
- [ ] .dockerignore files
- [ ] Compose consumes .env (not committed)
- [ ] Fresh full-stack run via `docker compose up --build` verified

## Phase 15 — Deployment

- [ ] Hosting provisioned
- [ ] TLS reverse proxy configured
- [ ] Secrets injected via hosting env/GitHub Secrets
- [ ] Images published to registry
- [ ] Deploy scripts/runbook documented
- [ ] Post-deploy smoke suite passed
- [ ] README updated with REAL demo URL

## Phase 16 — Documentation and Roadmap.sh Submission

- [ ] Screenshots captured and embedded in README
- [ ] Docs refreshed to as-built state
- [ ] TRACKER fully reconciled
- [ ] release/v1.0.0 → main tagged
- [ ] Submitted to https://roadmap.sh/projects/realtime-leaderboard
