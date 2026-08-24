# Project Progress Tracker

**Last updated:** 2026-08-25 (Phase 2 authentication implemented, tested, and live-verified)
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

## Phase 5 — User Service

- [ ] user_db schema migration (user_profiles)
- [ ] Get/update own profile endpoints
- [ ] Feign client to auth internal API
- [ ] Authorization matrix tests (self-only edits)

## Phase 6 — Sport Service

- [ ] sport_db schema migration (sports)
- [ ] Public list-active-sports endpoint
- [ ] Admin create/update/status endpoints
- [ ] Unique code enforcement
- [ ] Seed data FOOTBALL, CRICKET, F1 via migration
- [ ] Internal lookup endpoint for Feign consumers
- [ ] Tests for conflicts and authorization

## Phase 7 — Score Service

- [ ] score_db schema migration (scores, score_history)
- [ ] POST /api/scores validation pipeline (user/sport/bounds/rate)
- [ ] Transactional persistence (scores + history)
- [ ] Paginated score history endpoint
- [ ] Cached Feign sport validation
- [ ] Kafka producer publishing after commit
- [ ] Redis writer interface stubbed
- [ ] Validation-matrix tests passing

## Phase 8 — Redis Leaderboard

- [ ] Key-builder honoring {code} derivation + TTL policy
- [ ] Producer-side ZINCRBY active
- [ ] Global / per-sport read endpoints with pagination
- [ ] Daily / weekly / season windows
- [ ] /me rank-score-count endpoint
- [ ] Rebuild-from-history bootstrap job
- [ ] Integration tests incl. tie handling and window rollover
- [ ] NFR-01 latency spot-check recorded

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
