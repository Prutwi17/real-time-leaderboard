# Real-Time Leaderboard System

A production-style, multi-sport, real-time leaderboard platform. Authenticated users submit scores for configurable sports and watch rankings update live — no page refreshes.

> Reference project: **https://roadmap.sh/projects/realtime-leaderboard**
>
> Status: Phase 0 docs ✅ · Phase 1A microservice foundations ✅ · Phase 2 authentication ✅ · Phase 3 sport service ✅ · Phase 4 score service ✅ · Phase 5 user/player service ✅ · **Phase 6 Redis leaderboard ✅ (82/82 tests)** · **Phase 7 Kafka event integration ✅ (139/139 tests, outbox pattern, live E2E verified)** · **Phase 8 WebSocket/STOMP real-time ✅ (101/101 tests, live broadcast verified)**. React frontend is NOT implemented yet.

---

## Project Overview

The system decouples the write path (score ingestion → durable MySQL storage) from the read/broadcast path (Kafka events → Redis Sorted Set ranking → WebSocket push). Sports are data, not code: Football, Cricket, and Formula 1 ship as seed rows, and adding a new sport requires zero architectural change.

## Features

- JWT authentication with refresh-token rotation and USER/ADMIN roles
- Configurable sports catalog with admin enable/disable
- Validated score submission with rate limiting
- Global, per-sport, daily, weekly, and F1 season leaderboards
- Personal rank/score lookup
- Real-time WebSocket updates to connected clients
- Score history and period-based reports
- OpenAPI/Swagger documentation per service

## Supported Sports

| Sport | Code | Notes |
|---|---|---|
| Football | `FOOTBALL` | V1 |
| Cricket | `CRICKET` | V1 |
| Formula 1 | `F1` | season-scoped board |

Sports live in MySQL ([SCHEMA.md](SCHEMA.md)) — new sports are inserted rows, not redesigns.

## Architecture

```mermaid
flowchart LR
    FE[React + TS + Vite] --> GW[API Gateway :8080]
    GW --> AUTH[auth-service] & USR[user-service] & SPT[sport-service] & SCO[score-service] & LB[leaderboard-service]
    ER[Eureka :8761] -. discovery .- GW & AUTH & USR & SPT & SCO & LB
    SCO --> MY[(MySQL)]
    SCO --> RD[(Redis Sorted Sets)]
    SCO --> K[[Kafka score-submitted]]
    K --> LB
    LB --> RD
    LB -- STOMP/WebSocket --> FE
```

Full diagrams: [DESIGN.md](DESIGN.md). Flows: [APPFLOW.md](APPFLOW.md).

## Technology Stack

- **Backend:** Java 17 (LTS), Spring Boot 3.3.13, Spring Cloud 2023.0.5 (Eureka, Gateway), Spring Security + JWT (auth, sport, score, user services), Spring Data JPA/Hibernate (auth, sport, score, user services), Spring Data Redis (leaderboard-service), Maven Wrapper 3.3.2
- **Data:** MySQL 8, Redis 7 (Sorted Sets), Apache Kafka 3.x
- **Real-time:** Spring WebSocket + STOMP (+ SockJS fallback)
- **Frontend:** React 18, TypeScript (strict), Vite
- **DevOps:** Docker, Docker Compose, Git/GitHub
- **Quality:** JUnit 5, Mockito, Spring Boot Test; springdoc-openapi (Swagger)

Rationale for every choice: [TECHSPECS.md](TECHSPECS.md).

## Microservices

| Service | Port | Responsibility | Docs schema |
|---|---|---|---|
| service-registry | 8761 | Eureka discovery | — |
| api-gateway | 8080 | single entry point: routing, CORS, JWT filter | DESIGN §4 |
| auth-service | 8081 | registration, login, JWT + refresh tokens, roles | SCHEMA §3.1–3.2 |
| user-service | 8082 | player profiles CRUD, public read, ADMIN management | SCHEMA §3.3 |
| sport-service | 8083 | sports CRUD, competitions, enable/disable, seeds | SCHEMA §3.4 (implemented) |
| score-service | 8084 | score submission, validation, ownership, persistence, sport-service integration | SCHEMA §3.5 |
| leaderboard-service | 8085 | Redis Sorted Set boards, rank queries, score update consumer (HTTP), rebuild, reports | SCHEMA §4 |

## Sport Service

Implemented in Phase 3 (port 8083, registers with Eureka as `SPORT-SERVICE`, database `leaderboard_sport`).

**Supported sports:** exactly three — **Football** (`FOOTBALL`), **Cricket** (`CRICKET`), **Formula 1** (`F1`). The three sports are seeded automatically at startup if missing (idempotent `CommandLineRunner`; restarts never duplicate or delete data). Unsupported codes (BASKETBALL, TENNIS, …) are rejected with HTTP 400 — never silently created.

**Sport endpoints**

| Method | Path | Access |
|---|---|---|
| GET | `/api/sports` · `/api/sports/{id}` · `/api/sports/code/{code}` | public |
| POST / PUT / PATCH status / DELETE | `/api/sports` · `/api/sports/{id}` | ADMIN |

**Competition endpoints** (each competition belongs to exactly one sport)

| Method | Path | Access |
|---|---|---|
| GET | `/api/competitions` · `/api/competitions/{id}` · `/api/sports/{sportId}/competitions` | public |
| POST | `/api/sports/{sportId}/competitions` | ADMIN |
| PUT / PATCH status / DELETE | `/api/competitions/{id}` | ADMIN |

Deleting a sport that still owns competitions is refused with `409 CONFLICT` — deactivate it instead.

```bash
curl http://localhost:8080/api/sports
curl http://localhost:8080/api/sports/code/F1
curl -X POST http://localhost:8080/api/sports/1/competitions -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"name":"Premier League","code":"PREMIER_LEAGUE","startDate":"2026-08-15","endDate":"2027-05-24"}'
```

## User / Player Service

Implemented in Phase 5 (port 8082, registers with Eureka as `USER-SERVICE`, database `leaderboard_user`).

Player profile management with public read access and ADMIN-only management. The service validates JWTs using the shared `JWT_SECRET` (validation-only, no token generation). Deactivated players are filtered from list queries but still accessible by ID; hard delete is ADMIN-only.

**Player endpoints**

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/players` | authenticated | create player profile; `displayName`, `email` required; optional `bio`, `profileImageUrl` |
| GET | `/api/players` | public | paginated list of active players; `?search=` filters by display name |
| GET | `/api/players/{id}` | public | single player by ID |
| PUT | `/api/players/{id}` | ADMIN | update profile fields |
| PUT | `/api/players/{id}/deactivate` | ADMIN | soft-delete: sets `active=false`; player excluded from list |
| PUT | `/api/players/{id}/activate` | ADMIN | restore deactivated player |
| DELETE | `/api/players/{id}` | ADMIN | hard delete |

**Validation rules:** `displayName` 2–50 chars; `email` valid and unique; duplicate email → 409.

```bash
curl http://localhost:8080/api/players
curl http://localhost:8080/api/players/1

# create (requires auth token)
curl -X POST http://localhost:8080/api/players -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"displayName":"player_one","email":"player1@example.com","bio":"Football enthusiast"}'

# admin: update / deactivate / activate / delete
curl -X PUT http://localhost:8080/api/players/1 -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"bio":"Updated bio"}'
curl -X PUT http://localhost:8080/api/players/1/deactivate -H "Authorization: Bearer $ADMIN_TOKEN"
curl -X PUT http://localhost:8080/api/players/1/activate  -H "Authorization: Bearer $ADMIN_TOKEN"
curl -X DELETE http://localhost:8080/api/players/1 -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Score Service

Implemented in Phase 4 (port 8084, registers with Eureka as `SCORE-SERVICE`, database `leaderboard_score`).

Score submission with sport validation (via sport-service), ownership enforcement, optional idempotency keys, and paginated queries. The service validates JWTs using the shared `JWT_SECRET` (no token generation). Sport validation happens at submit time via a `@LoadBalanced RestTemplate` call to sport-service; submissions for missing (404) or inactive (409) sports are rejected.

**Score endpoints**

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/api/scores` | authenticated | submit score; sportId, value, scoreType required; optional eventName, eventId, submissionId |
| GET | `/api/scores/me` | authenticated | paginated list of caller's own scores (newest first) |
| GET | `/api/scores/{id}` | owner or ADMIN | single score by ID; USER can only read own scores |
| GET | `/api/scores` | ADMIN | search with filters: userId, sportId, eventId, scoreType, from, to (paginated) |
| DELETE | `/api/scores/{id}` | ADMIN | hard delete invalid submissions |

**Score types:** `POINTS`, `GOALS`, `RUNS`, `LAP_TIME`, `POSITION`

**Validation rules:** value ≥ 0, ≤ 1,000,000, max 2 decimal places; duplicate `submissionId` per user → 409.

```bash
curl -X POST http://localhost:8080/api/scores -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sportId":1,"value":100,"scoreType":"GOALS","submissionId":"epl-md1","eventName":"EPL Match Day 1","eventId":"EPL-MD1"}'

curl http://localhost:8080/api/scores/me -H "Authorization: Bearer $TOKEN"

# Admin search
curl "http://localhost:8080/api/scores?sportId=1&scoreType=GOALS" -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Leaderboard Service

Implemented in Phase 6 (port 8085, registers with Eureka as `LEADERBOARD-SERVICE`, Redis-backed — no MySQL).

Redis Sorted Sets power all live rankings. Score-service notifies leaderboard-service on each score submission via an internal HTTP API protected by a shared secret (`X-Internal-Service-Secret`). Idempotent processing via Redis processed-score keys prevents duplicate ranking updates.

**Leaderboard endpoints**

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/leaderboards/{sport}/top?limit=N` | public | top-N entries (default 10, max 100) |
| GET | `/api/leaderboards/{sport}?page=&size=` | public | paginated full leaderboard |
| GET | `/api/leaderboards/{sport}/players/{userId}/rank` | public | player rank + score |
| GET | `/api/leaderboards/{sport}/players/{userId}/nearby?range=N` | public | players around given rank |
| GET | `/api/leaderboards/{sport}/me` | authenticated | current user's rank |
| GET | `/api/leaderboards/{sport}/size` | public | total players on board |
| POST | `/internal/leaderboards/scores` | internal secret | score update notification |
| POST | `/internal/leaderboards/{sport}/rebuild` | internal secret | rebuild board from score-service data |

**Supported sports:** FOOTBALL, CRICKET, F1 — keys derived as `leaderboard:{sport_lowercase}`.

```bash
curl http://localhost:8080/api/leaderboards/football/top?limit=5
curl http://localhost:8080/api/leaderboards/football/players/1/rank
curl http://localhost:8080/api/leaderboards/football/me -H "Authorization: Bearer $TOKEN"
```

## Database

MySQL schemas are owned per service. The auth schema **`leaderboard_auth`** (tables `users`, `refresh_tokens`) is live as of Phase 2, the sport schema **`leaderboard_sport`** (tables `sports`, `competitions`) is live as of Phase 3, the score schema **`leaderboard_score`** (table `scores`) is live as of Phase 4, and the user schema **`leaderboard_user`** (table `players`) is live as of Phase 5 — all via Hibernate `ddl-auto=update`; Flyway/Liquibase migrations are required before production. Other schemas arrive with their phases — full column-level specs and ER diagrams: [SCHEMA.md](SCHEMA.md). MySQL is the durable source of truth; it never serves live ranking.

## Redis Leaderboard

Redis Sorted Sets power all current standings:

```
leaderboard:global            leaderboard:{code}
leaderboard:{code}:daily:{yyyy-MM-dd}
leaderboard:{code}:weekly:{yyyy}-W{ww}
leaderboard:f1:season:{yyyy}
```

Command rationale (`ZINCRBY`, `ZADD`, `ZREVRANGE`, `ZREVRANK`, `ZSCORE`, `ZCARD`, …) and TTL policy: [SCHEMA.md §4](SCHEMA.md).

## Kafka

Topic `score-submitted` (key = userId, idempotent consumer on `eventId`, DLT on poison pills). Event contract and evolution rules: [SCHEMA.md §5](SCHEMA.md), failure semantics: [DESIGN.md §8](DESIGN.md).

## WebSocket

STOMP endpoint `/ws/leaderboard`; subscriptions `/topic/leaderboard/global` and `/topic/leaderboard/{sportCode}`; coalesced ≤ 1 msg/sec/topic; client reconnects snapshot-first. Details: [DESIGN.md §9](DESIGN.md).

## Authentication

Implemented in auth-service (Phase 2):

- **Registration** `POST /api/auth/register` — validates username/email/password, stores BCrypt hash in MySQL (`leaderboard_auth.users`). Always creates a USER account; requested roles are ignored so nobody can self-elevate to ADMIN.
- **Login** `POST /api/auth/login` — returns `{accessToken, refreshToken, tokenType: "Bearer", expiresIn, userId, username, role}`.
- **JWT access tokens** — HS256, secret from `JWT_SECRET` env var, default 15-minute TTL.
- **Refresh tokens** — opaque 512-bit random value; only its SHA-256 hash is stored; 7-day TTL.
- **Refresh** `POST /api/auth/refresh` — exchanges a valid refresh token for a new access token.
- **Logout** `POST /api/auth/logout` *(requires Bearer token)* — revokes the refresh token server-side. Access tokens remain cryptographically valid until they expire (stateless JWT), which is why their lifetime is short.
- **Who am I** `GET /api/auth/me` *(requires Bearer token)* — safe identity payload for the current user.

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"Passw0rd123"}'

curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"john","password":"Passw0rd123"}'
```

Roles: `USER`, `ADMIN`; `/api/auth/admin/**` is ADMIN-only. Full security design: [SECURITY.md](SECURITY.md); schema: [SCHEMA.md](SCHEMA.md).

## Frontend

React 18 + TypeScript (strict) + Vite SPA in `frontend/`. Communicates exclusively with the API Gateway (REST via Axios, WebSocket via STOMP over SockJS). Layout: [DESIGN.md §10](DESIGN.md). Full README: [frontend/README.md](frontend/README.md).

**Tech stack:** React 18, TypeScript 5 (strict), Vite 5, Tailwind CSS, React Router v6, Axios, STOMP.js, SockJS.

**Dev server:** `npm run dev` → http://localhost:5173 (Vite proxies `/api` and `/ws` to gateway at :8080).

**Routes:**

| Path | Page | Auth |
|---|---|---|
| `/` | Dashboard | No |
| `/login` | Login | No |
| `/register` | Register | No |
| `/leaderboards/:sport` | Live leaderboard per sport | No |
| `/players` | Player directory | No |
| `/players/:id` | Player profile | No |
| `/scores` | Score submission + history | Yes |
| `/admin` | Admin panel (sport management) | Yes (ADMIN) |

**Key patterns:**
- Centralized Axios instance with JWT authorization interceptor.
- Token refresh interceptor: catches 401s, refreshes silently, retries once.
- STOMP WebSocket: subscribe to `/topic/leaderboards/{sport}` for live updates; REST snapshot on initial load.
- Custom hooks: `useWebSocket`, `useLeaderboard`, `usePlayer`, `useScores`, `useScoreSubmit`.
- 16+ reusable components (LeaderboardTable, RankBadge, ScoreForm, SportTabs, etc.).
- No mock data or hardcoded player names in production code.

**Tests:** `npm run test` — 26 tests passing (Vitest + React Testing Library).

**Build:** `npm run build` → `frontend/dist/` (static assets for nginx or CDN).

## Project Structure

```
real-time-leaderboard/
├── backend/
│   ├── service-registry/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── user-service/
│   ├── sport-service/
│   ├── score-service/
│   └── leaderboard-service/
├── frontend/
├── docker/
├── docs/            # architecture / api / database notes
├── .github/         # workflows + PR template
├── PRD.md           TECHSPECS.md     APPFLOW.md     DESIGN.md
├── SCHEMA.md        IMPLEMENTATIONPLAN.md   TRACKER.md
├── RULES.md         SECURITY.md
└── README.md   .env.example   .gitignore
```

## API Documentation

Each backend service exposes Swagger UI via springdoc-openapi (reached through the gateway once services exist): `http://localhost:8080/api/<service>/swagger-ui.html`. Endpoint catalog notes will accumulate under [docs/api/](docs/api/).

## Environment Variables

Copy `.env.example` → `.env` and fill real values locally. Never commit `.env`. The full variable table lives in [SECURITY.md §3](SECURITY.md).

## Local Development

### Build and run a backend service (Maven Wrapper — no global Maven required)

```bash
cd backend/auth-service        # or any service under backend/
.\mvnw.cmd clean test          # compile + run tests (verified for all 7 services)
.\mvnw.cmd spring-boot:run     # start the service
```

- Start `service-registry` first; its dashboard is at http://localhost:8761.
- Verified baseline (Phase 1A): all seven services pass `.\mvnw.cmd clean test`; api-gateway registers with the registry through Eureka discovery (`lb://` routes are wired, downstream services arrive in later phases).
- The first wrapper invocation downloads Maven 3.9.9 plus dependencies into `~/.m2` — one-time internet access required.

### Full stack via Docker Compose (target state — Phase 14, not yet available)

```bash
cp .env.example .env          # then edit values
docker compose -f docker/docker-compose.yml up --build
# frontend: http://localhost:5173 · gateway: http://localhost:8080 · eureka: http://localhost:8761
```

## Docker

Target state: `docker compose up --build` boots MySQL, Redis, Kafka, all seven backend services, and the frontend container with healthcheck-gated startup ordering. Tracked in [TRACKER.md Phase 14](TRACKER.md).

## Testing

JUnit 5 + Mockito unit tests and Spring Boot Test integration tests per service; frontend component tests land with Phase 11; coverage gate ≥ 70% enforced from Phase 13.

## Git Workflow

Monorepo with permanent `main` and `develop` branches; all work happens on `feature/*` branches merged into `develop` via PRs (Conventional Commits); releases flow through `release/*` to `main`. Rules: [RULES.md](RULES.md).

## Screenshots

*Placeholder — screenshots will be added when the UI exists.*

## Demo

*Placeholder — no live deployment yet. A real URL will appear here after Phase 15; none is claimed now.*

## Roadmap.sh Project

This repository implements the roadmap.sh challenge:
**https://roadmap.sh/projects/realtime-leaderboard**

## Future Improvements

See [PRD.md §17](PRD.md): scoring-policy engine, achievements/streaks, private leagues, OAuth social login, fraud-detection consumers, Kubernetes deployment, mobile clients.

## License

To be decided before public release (candidate: MIT).
