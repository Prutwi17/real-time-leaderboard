# Real-Time Leaderboard System

A production-style, multi-sport, real-time leaderboard platform. Authenticated users submit scores for configurable sports and watch rankings update live — no page refreshes.

> Reference project: **https://roadmap.sh/projects/realtime-leaderboard**
>
> Status: Phase 0 (architecture & documentation) complete — implementation is in progress per [IMPLEMENTATIONPLAN.md](IMPLEMENTATIONPLAN.md). See [TRACKER.md](TRACKER.md) for honest, itemized progress. There is no live demo yet; this section will be updated only when a real deployment exists.

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

- **Backend:** Java 17, Spring Boot 3.x, Spring Cloud (Eureka, Gateway, OpenFeign), Spring Security + JWT, Spring Data JPA/Hibernate, Maven
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
| user-service | 8082 | profiles, user info/statistics | SCHEMA §3.3 |
| sport-service | 8083 | sports CRUD, enable/disable, seeds | SCHEMA §3.4 |
| score-service | 8084 | submission validation, persistence, event publishing | SCHEMA §3.5–3.6 |
| leaderboard-service | 8085 | boards, rank queries, Kafka consumer, WebSocket push, reports | SCHEMA §4 |

## Database

MySQL schemas are owned per service (`auth_db`, `user_db`, `sport_db`, `score_db`) — see [SCHEMA.md](SCHEMA.md) for tables, keys, indexes, and ER diagrams. MySQL is the durable source of truth; it never serves live ranking.

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

BCrypt credentials, HS256 access tokens (env secret), rotating hashed refresh tokens, gateway-enforced authorization with in-service re-checks. Full design and threat model: [SECURITY.md](SECURITY.md).

## Frontend

React + TypeScript strict + Vite SPA talking exclusively to the API Gateway: login/register, dashboard, global + sport leaderboards (live), score submission, history, reports, admin panel. Layout: [DESIGN.md §10](DESIGN.md).

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

*(Available from Phase 14 onward; commands below describe the target state and do not work yet.)*

```bash
cp .env.example .env          # then edit values
docker compose -f docker/docker-compose.yml up --build
# frontend: http://localhost:5173 · gateway: http://localhost:8080 · eureka: http://localhost:8761
```

Without Docker (per-service dev): start MySQL/Redis/Kafka locally, run each Spring Boot app (`mvn spring-boot:run`) and `npm run dev` in `frontend/`.

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
