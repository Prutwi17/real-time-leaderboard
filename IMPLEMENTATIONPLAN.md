# Implementation Plan

**Product:** Real-Time Leaderboard System
**Status:** Phase 0 — plan only. Nothing below is complete; see [TRACKER.md](TRACKER.md).
**Discipline:** one phase per feature branch (`feature/<name>`), PR into `develop`, per [RULES.md §Git](RULES.md).

Each phase lists Objective, Tasks, Expected files, Dependencies, Testing requirements, Completion criteria.

---

## PHASE 0 — Documentation and Architecture
- **Objective:** Establish the full architectural contract before any code exists.
- **Tasks:** Author PRD, technical specs, flows, design, schema, rules, security docs; scaffold repo tree; define env-var policy; GitHub templates.
- **Expected files:** Root `*.md` set, `.gitignore`, `.env.example`, `.github/` placeholders, directory skeleton.
- **Dependencies:** None.
- **Testing:** Documentation review against the Phase-0 validation checklist (§31 of the initialization brief).
- **Completion criteria:** All documents exist with requested names, no secrets committed, tracker reflects reality.

## PHASE 1 — Repository and Project Setup
- **Objective:** Compilable multi-module Maven skeleton with Git workflow active.
- **Tasks:** `git init` + branch protection conventions; parent POM (Java 17 release, Boot 3.3.x, Cloud 2023.0.x BOM); create the seven child module POMs with empty Spring Boot apps; shared `.editorconfig`; commit conventions enforced via PR template.
- **Expected files:** root `pom.xml`, `backend/*/pom.xml`, minimal `*Application.java` + `application.yml` per service.

> As-built note (Phase 1A): implemented as seven **standalone** Spring Boot projects — one POM + Maven Wrapper per service, no shared reactor parent — so each service builds independently. Versions: Java 17.0.12 toolchain, Boot 3.3.13, Cloud BOM 2023.0.5, wrapper scripts 3.3.2 / Maven 3.9.9 distribution. All seven pass `.\mvnw.cmd clean test`; registry and gateway additionally smoke-tested running (Eureka dashboard UP, gateway registered as `API-GATEWAY`).
- **Dependencies:** Phase 0.
- **Testing:** `mvn clean verify` green across reactor; each app boots locally with placeholder config.
- **Completion criteria:** Reactor builds; services register nothing yet but start; first PRs merged to `develop`.

## PHASE 2 — Service Registry
- **Objective:** Eureka server operational as discovery backbone.
- **Tasks:** Eureka server dependency/config; dashboard on :8761; standalone mode; disable client self-registration-as-client quirks.
- **Expected files:** `service-registry` module: application class, `application.yml` (port, registry settings).
- **Dependencies:** Phase 1.
- **Testing:** App starts; dashboard renders; a temporary test client registers and appears.
- **Completion criteria:** Registry stable; health endpoint green; documented in README run section.

## PHASE 3 — API Gateway
- **Objective:** Single public entry point routing to all services.
- **Tasks:** Gateway module with `lb://` routes per [DESIGN.md §4](DESIGN.md); global CORS from env; JWT-validation filter skeleton (real crypto wired in Phase 4); identity-header injection; correlation-ID filter; uniform 401/403 problem responses.
- **Expected files:** gateway module: route config yml, security filter classes, CORS config.
- **Dependencies:** Phases 1–2.
- **Testing:** Route smoke tests (WebTestClient): each `/api/**` path reaches its stubbed downstream; unauthenticated protected routes → 401.
- **Completion criteria:** All six route families forward correctly through Eureka; CORS verified from Vite dev origin.

## PHASE 4 — Authentication
- **Objective:** Complete register/login/refresh/logout with JWT + roles.
- **Tasks:** auth-service entities/repositories for `users`+`refresh_tokens`; BCrypt encoding; JWT issue/validate utility (env secret); refresh rotation + reuse detection; login backoff; role model USER/ADMIN; gateway filter consumes real signatures; Feign-ready `/internal/users/{id}` lookup if needed by other services later.
- **Expected files:** auth-service full slice (controller/dto/entity/repo/service/security/config); Flyway migration V1__auth_schema.sql; updated gateway filter.
- **Dependencies:** Phases 2–3.
- **Testing:** Unit tests for token utils + password flow; `@SpringBootTest` MVC tests for register/login/refresh/logout incl. failure paths; manual Swagger pass.
- **Completion criteria:** AC of US-01/02 pass end-to-end through gateway; no plaintext secrets in repo.

> As-built note (Phase 4, executed as "Phase 2"): completed with documented deviations — schema created via Hibernate `ddl-auto=update` in `leaderboard_auth` DB (no Flyway migration yet); refresh tokens validate-and-revoke only (rotation + reuse detection deferred); login backoff/rate limiting not yet implemented; gateway JWT filter still a skeleton (services enforce authorization today). Additions beyond plan: `/api/auth/me` identity endpoint, `/api/auth/admin/check` RBAC probe, uniform JSON error handling, actuator health/info on auth-service. Verified: 29/29 automated tests + live E2E through gateway against MySQL.

## PHASE 5 — User Service
- **Objective:** Profile management and user statistics view.
- **Tasks:** `user_profiles` table + CRUD (self only); profile read falls back to JWT username; stats endpoint (counts from score data arrive Phase 7+ — stub interface now); admin user-list endpoint (ADMIN) reading via auth-service API.
- **Expected files:** user-service slice; Flyway V1__user_schema.sql; Feign client to auth internal API.
- **Dependencies:** Phase 4.
- **Testing:** Controller/service unit tests; integration test for get/update own profile; authorization matrix test (user A cannot edit B).
- **Completion criteria:** US-08 acceptance passes through gateway.

## PHASE 6 — Sport Service
- **Objective:** Configurable sports catalog with seed data.
- **Tasks:** `sports` entity/repo; public list-active endpoint; ADMIN create/update/status endpoints; unique-code enforcement; idempotent seed migration (FOOTBALL, CRICKET, F1); cacheable `/internal/sports/{id}` for Feign consumers.
- **Expected files:** sport-service slice; Flyway V1__sport_schema.sql (+V2 seed); OpenAPI annotations.
- **Dependencies:** Phase 4 (admin role available).
- **Testing:** Uniqueness conflict tests; enable/disable behavior tests; public-vs-admin authorization tests.
- **Completion criteria:** US-10/11 acceptance passes; catalog visible publicly without auth.

> As-built note (Phase 6, executed as "Phase 3"): completed with documented deviations — schema via Hibernate `ddl-auto=update` in `leaderboard_sport` (no Flyway migration); seeds via idempotent `DefaultSportsInitializer` (`CommandLineRunner`) instead of a migration; sport codes additionally constrained by a closed `SportCode` enum (FOOTBALL/CRICKET/F1) so unsupported sports are rejected at the API boundary while the varchar column stays extensible; competitions added per spec §7 with ManyToOne FK, unique uppercase code, and date-range validation; planned `/internal/sports/{id}` Feign endpoint deferred until a consumer exists; `score_unit_label` column deferred. Security: validation-only JWT (`JwtService` has no issue methods), public reads, ADMIN-only management. Gateway gained route `/api/competitions/**` → `lb://sport-service`. Tests: 55/55 green + live E2E through the gateway against MySQL.

## PHASE 7 — Score Service
- **Objective:** Validated score ingestion persisted durably.
- **Tasks:** `scores`+`score_history` tables; POST /api/scores pipeline (validate → persist tx); GET /api/scores/me paginated history; sport validation via cached Feign call; rate limiting (per-user bucket); Kafka producer wired (publish-after-commit) — consumer lands Phase 9; Redis write hook stubbed behind interface (Phase 8 fills it).
- **Expected files:** score-service slice; Flyway V1__score_schema.sql; kafka producer config; DTO events matching [SCHEMA.md §5](SCHEMA.md).
- **Dependencies:** Phases 4, 6.
- **Testing:** Validation-matrix tests (disabled sport, unknown sport, bad ranges); tx rollback test; history pagination test; producer test with embedded/mock Kafka.
- **Completion criteria:** US-03/07 acceptance passes; events observable in local Kafka; MySQL rows correct.

## PHASE 8 — Redis Leaderboard
- **Objective:** Live boards served entirely from Sorted Sets.
- **Tasks:** leaderboard-service reads: global/per-sport/daily/weekly/season endpoints with pagination; `/me` rank/score/count via ZREVRANK/ZSCORE/ZCARD; key-builder component honoring `{code}` derivation + TTL table; bootstrap rebuild job (history → ZADD); producer-side ZINCRBY activation in score-service behind the Phase-7 interface.
- **Expected files:** leaderboard-service slice (read controllers, redis config, key builders, rebuild runner); score-service redis writer impl.
- **Dependencies:** Phase 7 (events/data exist).
- **Testing:** Testcontainers/embedded-redis integration tests: increment→rank→page math; tie-handling; window rollover at UTC boundaries; rebuild correctness vs SQL aggregate.
- **Completion criteria:** FR-13/14 pass; NFR-01 latency target met in local benchmark notes.

## PHASE 9 — Kafka Integration
- **Objective:** Event-driven propagation with guaranteed-once effects.
- **Tasks:** Consumer group in leaderboard-service applying events to Redis (idempotency marker first); retry/backoff; DLT topic + alert log; offset/acks tuning; lag logging.
- **Expected files:** consumer config/classes, error handler, DLT wiring, integration test scaffolding.
- **Dependencies:** Phases 7–8.
- **Testing:** Integration tests: duplicate eventId ignored; poison-pill → DLT without blocking partition; restart-resume from offsets.
- **Completion criteria:** FR-12 passes; failure-scenario table behaviors demonstrated ([DESIGN.md §16](DESIGN.md)).

## PHASE 10 — WebSocket Real-Time Updates
- **Objective:** Sub-2-second push to connected clients.
- **Tasks:** STOMP endpoint `/ws/leaderboard` (+SockJS); broker topics; broadcast coalescer (≤1 msg/sec/topic); personal queue for rank nudges; heartbeat/session cleanup; snapshot-on-subscribe message.
- **Expected files:** WS config, broadcaster service, message DTOs, coalescer.
- **Dependencies:** Phase 9.
- **Testing:** Integration test with STOMP client asserting delivery + coalescing under burst; disconnect-cleanup test.
- **Completion criteria:** FR-15 passes two-client live check (US-04 AC).

## PHASE 11 — React Frontend
- **Objective:** Full SPA consuming only the gateway.
- **Tasks:** Vite+TS strict scaffold; axios client + refresh interceptor; auth store/guards; pages per PRD §9 route list; LeaderboardTable with live socket hook + polling fallback; ScoreForm; History page; Admin panel (sport mgmt); Reports page (Phase 12 wires data); error/toast system mapping problem+json.
- **Expected files:** entire `frontend/` app per [DESIGN.md §10](DESIGN.md).
- **Dependencies:** Phases 3–10 APIs available.
- **Testing:** Component/unit tests (vitest + testing-library) for critical widgets; typecheck strict; manual E2E script for all user stories.
- **Completion criteria:** All PRD user stories demoable in browser against local stack.

## PHASE 12 — Reports
- **Objective:** Period reporting backed by MySQL truth.
- **Tasks:** `/api/reports/top-players` (ADMIN + range params) aggregating `score_history` via score-service read API; participation metrics; CSV export (nice-to-have); wire Reports page.
- **Expected files:** report controller/service in leaderboard-service, feign read client, frontend reports page finalization.
- **Dependencies:** Phase 11 UI shell.
- **Testing:** Report numbers reconcile with hand-computed SQL for fixed fixtures; authorization tests.
- **Completion criteria:** RPT-1/2 satisfied; US-12/13 acceptance.

## PHASE 13 — Testing & Hardening
- **Objective:** Quality gates before containerization.
- **Tasks:** JaCoCo ≥70% gate on domain packages; fill coverage gaps; rate-limit + brute-force load sanity; structured-log/correlation verification; OpenAPI completeness sweep; performance spot-checks vs NFR-01/02; fix backlog burn-down.
- **Expected files:** test modules/config, coverage reports (CI artifact), fixes across services.
- **Dependencies:** Phases 4–12 code present.
- **Testing:** This phase *is* testing; full `mvn verify` + frontend suites green in CI placeholder activated here.
- **Completion criteria:** Gates green; zero known Must-level defects open.

## PHASE 14 — Docker
- **Objective:** One-command full environment.
- **Tasks:** Per-service Dockerfiles (layered jars, non-root user); `docker/docker-compose.yml` with mysql/redis/kafka + all services + frontend nginx; healthchecks + depends_on ordering; `.dockerignore`s; compose uses `.env` (never committed).
- **Expected files:** `backend/*/Dockerfile`, `docker/docker-compose.yml`, `frontend/Dockerfile`, env template updates.
- **Dependencies:** Phase 13.
- **Testing:** Fresh-machine style run: `docker compose up --build` → seeded sports visible, full happy-path walk-through inside containers.
- **Completion criteria:** PRD success criterion #4 met.

## PHASE 15 — Deployment
- **Objective:** Publicly reachable demo (honest scope: single VM acceptable).
- **Tasks:** Provision host; TLS reverse proxy; production env vars via hosting secrets (GitHub Secrets / server env); images pushed to registry; deploy scripts; smoke checks post-deploy; backup story for MySQL volume.
- **Expected files:** deployment configs/scripts under `docker/deploy/` or CI workflow jobs; runbook doc.
- **Dependencies:** Phase 14.
- **Testing:** Post-deploy smoke suite (register→submit→live update) against public URL.
- **Completion criteria:** Real demo URL exists; README updated with actual link (no placeholders remain).

## PHASE 16 — Documentation and Roadmap.sh Submission
- **Objective:** Submission-ready repository.
- **Tasks:** Final README polish (screenshots, demo URL, honest status); architecture diagrams refreshed to as-built state; TRACKER fully reconciled; tag `v1.0.0` via release branch flow; submit to Roadmap.sh.
- **Expected files:** updated docs, screenshots assets, git tag/release.
- **Dependencies:** Phase 15.
- **Testing:** Checklist audit against Roadmap.sh requirements + §31 validation list.
- **Completion criteria:** Project submitted; repo self-explanatory to a stranger.
