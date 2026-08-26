# Real-Time Leaderboard — Frontend

React 18 + TypeScript (strict) + Vite SPA. Communicates exclusively with the API Gateway (`VITE_API_BASE_URL`); receives live leaderboard updates over WebSocket (STOMP).

## Tech Stack

| Library | Purpose |
|---|---|
| React 18 | UI framework |
| TypeScript 5 (strict) | Type safety, DTO contracts |
| Vite 5 | Dev server, HMR, production build |
| Tailwind CSS | Utility-first styling |
| React Router v6 | Client-side routing |
| Axios | HTTP client (REST calls to gateway) |
| STOMP.js + SockJS | WebSocket/STOMP real-time subscriptions |
| Vitest + Testing Library | Component/unit tests |

## Directory Structure

```
frontend/
├── src/
│   ├── api/              # Axios instance (baseURL = gateway), refresh interceptor
│   ├── components/       # 16+ reusable components (LeaderboardTable, RankBadge, ScoreForm, SportTabs, etc.)
│   ├── hooks/            # useWebSocket, useLeaderboard, usePlayer, useScores, useScoreSubmit
│   ├── pages/            # Route-level pages (Login, Register, Dashboard, Leaderboard, Players, Scores, Admin)
│   ├── types/            # TypeScript DTO mirrors of backend contracts
│   ├── utils/            # Auth helpers, formatting, constants
│   ├── App.tsx           # Root component with React Router
│   └── main.tsx          # Entry point
├── index.html
├── vite.config.ts        # Dev proxy → http://localhost:8080 (gateway)
├── tailwind.config.js
├── tsconfig.json
└── package.json
```

## Getting Started

```bash
cd frontend
npm install
npm run dev          # → http://localhost:5173
```

Ensure the backend is running: `service-registry` (:8761), `api-gateway` (:8080), and downstream services.

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Start Vite dev server on port 5173 |
| `npm run build` | Production build → `dist/` |
| `npm run preview` | Preview production build locally |
| `npm run test` | Run Vitest test suite (26 tests) |
| `npm run lint` | Lint source files |
| `npm run typecheck` | TypeScript strict-mode type check |

## Environment Variables

All prefixed with `VITE_` (exposed to the client by Vite):

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | API Gateway base URL for REST calls |
| `VITE_WS_URL` | `http://localhost:8080` | WebSocket endpoint (STOMP over SockJS) |

Create `.env` in `frontend/` (git-ignored). Vite proxy in dev mode forwards `/api` and `/ws` to `localhost:8080` so no CORS issues arise.

## Vite Dev Proxy

`vite.config.ts` proxies the following paths to the API Gateway:

```
/api/** → http://localhost:8080/api/**
/ws/**  → http://localhost:8080/ws/**
```

This means the frontend dev server can call `/api/auth/login` and Vite transparently forwards it to `:8080`.

## Routes

| Path | Page | Auth Required |
|---|---|---|
| `/` | Dashboard (home) | No |
| `/login` | Login form | No |
| `/register` | Registration form | No |
| `/leaderboards/:sport` | Live leaderboard per sport (football, cricket, f1) | No |
| `/players` | Player directory | No |
| `/players/:id` | Player profile detail | No |
| `/scores` | Score submission + history | Yes |
| `/admin` | Admin panel (sport management) | Yes (ADMIN) |

Protected routes redirect unauthenticated users to `/login`. Admin routes additionally verify the `ADMIN` role.

## Architecture

### REST Communication

All REST calls go through Axios → API Gateway (`VITE_API_BASE_URL`). The frontend never talks directly to microservices. Key patterns:

- **Centralized Axios instance** (`src/api/`) with base URL from env.
- **JWT interceptor** attaches `Authorization: Bearer <token>` to every request.
- **Refresh interceptor** catches 401 responses, silently refreshes the access token using the stored refresh token, then retries the original request once.

### WebSocket / STOMP

Live leaderboard updates use STOMP over SockJS:

- **Endpoint:** `/ws` (SockJS fallback enabled)
- **Subscribe to:** `/topic/leaderboards/{sport}` (football, cricket, f1)
- **Pattern:** REST fetches initial snapshot, then WebSocket pushes incremental updates.
- **Reconnect:** automatic with exponential backoff (1s → 30s).
- **No polling fallback needed** — STOMP reconnects transparently.
- Server-only publishing; clients subscribe only.

### Auth Flow

1. User registers or logs in via `/api/auth/register` or `/api/auth/login`.
2. Response stores `accessToken` and `refreshToken` in `localStorage`.
3. Axios interceptor attaches the access token to all requests.
4. On 401 (expired token), the refresh interceptor calls `/api/auth/refresh` with the stored refresh token, obtains a new access token, and retries.
5. On logout, tokens are cleared from `localStorage` and the WebSocket connection is closed.

No real player names or mock data exist in production code — all data comes from the live backend.

## Testing

```bash
npm run test          # vitest — 26 tests passing
```

Tests cover reusable components, hooks, and key user flows using Vitest + React Testing Library.

## Production Build

```bash
npm run build         # → frontend/dist/
```

The `dist/` directory contains static assets (HTML, JS, CSS) ready to serve via nginx or any static file server. The production build does not include the Vite dev proxy — the reverse proxy (nginx/API Gateway) must route `/api` and `/ws` appropriately.

## Implementation

Implemented in Phase 11 ([IMPLEMENTATIONPLAN.md](../IMPLEMENTATIONPLAN.md)) · Architecture: [DESIGN.md §10](../DESIGN.md) · Security: [SECURITY.md](../SECURITY.md)
