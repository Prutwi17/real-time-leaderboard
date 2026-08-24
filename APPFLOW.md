# Application Flows

**Product:** Real-Time Leaderboard System
**Related:** [DESIGN.md](DESIGN.md) · [SCHEMA.md](SCHEMA.md) · [SECURITY.md](SECURITY.md)

All external flows pass through the API Gateway. `lb://` denotes Eureka-based load-balanced resolution. Mermaid diagrams illustrate the target architecture (Phase 0 — not yet implemented; see [TRACKER.md](TRACKER.md)).

---

## Flow 1 — Registration

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant R as React (/register)
    participant GW as API Gateway :8080
    participant A as auth-service :8081
    participant DB as MySQL auth_db

    U->>R: email, username, password
    R->>GW: POST /api/auth/register
    GW->>A: route /api/auth/** (no JWT required)
    A->>A: validate input (Bean Validation)
    A->>DB: SELECT user WHERE email/username (uniqueness)
    alt duplicate
        A-->>GW: 409 Conflict + problem+json
    else unique
        A->>A: BCrypt hash password (strength >= 10)
        A->>DB: INSERT users (role=USER)
        A-->>GW: 201 Created {userId, username}
    end
    GW-->>R: response
```

Failure paths: validation error → 400; unexpected → 500 with correlation ID.

---

## Flow 2 — Login

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant R as React (/login)
    participant GW as API Gateway
    participant A as auth-service
    participant DB as MySQL

    U->>R: credentials
    R->>GW: POST /api/auth/login
    GW->>A: forward
    A->>DB: load user by username/email
    alt user missing or BCrypt mismatch
        A-->>R: 401 generic "Invalid credentials" (no user enumeration)
        A->>A: record failure -> progressive backoff per account/IP
    else match
        A->>DB: INSERT refresh_tokens (opaque, hashed, 7d expiry)
        A-->>R: 200 {accessToken(JWT), refreshToken, expiresIn}
    end
    R->>R: store tokens (memory + refresh in httpOnly-capable storage)
```

---

## Flow 3 — JWT Authentication (every protected request)

```mermaid
sequenceDiagram
    autonumber
    participant R as React
    participant GW as API Gateway
    participant S as Target microservice

    R->>GW: request + Authorization: Bearer <JWT>
    GW->>GW: filter verifies signature (HS256), exp, issuer
    alt invalid/expired
        GW-->>R: 401 (client triggers refresh flow)
    else valid
        GW->>S: route + X-User-Id, X-User-Role headers (set by gateway only)
        S->>S: re-check claims needed for the endpoint (defense in depth)
        S-->>R: response via gateway
    end
```

Notes: services never accept identity headers from outside the gateway (network-level trust boundary); stateless — no session store.

---

## Flow 4 — Score Submission (core write path)

```mermaid
sequenceDiagram
    autonumber
    actor U as Player
    participant R as React
    participant GW as API Gateway
    participant SC as score-service
    participant SP as sport-service (Feign)
    participant MY as MySQL score_db
    participant RD as Redis
    participant K as Kafka (score-submitted)

    U->>R: submit {sportId, score}
    R->>GW: POST /api/scores (Bearer JWT)
    GW->>SC: lb://score-service + identity headers
    SC->>SC: validate body, rate limit (FR-20)
    SC->>SP: GET /internal/sports/{id} (Feign, cached)
    SP-->>SC: sport exists AND enabled?
    alt invalid user/sport/score
        SC-->>U: 400/404 problem+json (nothing persisted)
    else valid
        SC->>MY: INSERT scores + score_history (single tx)
        Note over SC,K: publish AFTER commit
        SC->>RD: ZINCRBY leaderboard:{code} score userId<br/>ZINCRBY daily/weekly keys
        SC->>K: publish ScoreSubmittedEvent{eventId,...}
        SC-->>U: 201 {scoreId, currentRank?}
    end
```

Design decisions:
- User validity comes from gateway-verified JWT claims (`X-User-Id`), so no user-service call is needed on this hot path.
- Redis update happens synchronously here for immediate read-your-write on boards; Kafka remains the source that drives *broadcast* and any reconciliation.
- If Redis or Kafka are briefly down, MySQL commit still succeeds and a reconciliation job replays history ([DESIGN.md §Failure scenarios](DESIGN.md)).

---

## Flow 5 — Redis Leaderboard Update

```mermaid
flowchart TD
    A[Valid submission committed to MySQL] --> B[ZINCRBY leaderboard:sportCode score userId]
    A --> C[ZINCRBY leaderboard:global score userId]
    B --> D[ZINCRBY windowed key e.g. code:daily:date]
    C --> D2[global daily/weekly variants]
    D --> E[Set TTL on new windowed keys]
    E --> F[Live reads served by ZREVRANGE / ZREVRANK / ZSCORE]
    F --> G[Bootstrap path when cold:<br/>ZADD from score_history rebuild]
```

Command rationale lives in [SCHEMA.md §Redis commands](SCHEMA.md). `{sportCode}` always comes from the sports table — never hardcoded.

---

## Flow 6 — Kafka Event Flow

```mermaid
flowchart LR
    subgraph Producer
        SC[score-service] -- "publish after MySQL commit" --> T[[topic: score-submitted<br/>key=userId, 3 partitions]]
    end
    T --> C1[L1 leaderboard-service consumer]
    T -.-> FX[future consumers: analytics, fraud]
    C1 --> IDEM{eventId seen before?}
    IDEM -- yes --> SKIP[ack & skip]
    IDEM -- no --> RD[apply to Redis ZSETs]
    RD --> WS[broadcast over WebSocket]
    RD --> ACK[offset commit]
    C1 -- repeated failure --> RETRY[retry w/ backoff x3]
    RETRY -- still failing --> DLT[[score-submitted.dlt + alert log]]
```

Event payload, versioning, and idempotency rules: [SCHEMA.md §Kafka](SCHEMA.md) / [DESIGN.md §Kafka](DESIGN.md).

---

## Flow 7 — Real-Time WebSocket Update

```mermaid
sequenceDiagram
    autonumber
    participant LB as leaderboard-service
    participant WS as STOMP broker endpoint /ws/leaderboard
    participant C1 as Client A (React)
    participant C2 as Client B (React)

    C1->>WS: CONNECT (SockJS fallback enabled)
    C2->>WS: CONNECT
    C1->>WS: SUBSCRIBE /topic/leaderboard/f1
    LB->>LB: consume event, recompute top-N + rank deltas (coalesce <= 1 msg/sec/topic)
    LB->>WS: SEND /topic/leaderboard/f1 {type, entries, updatedAt}
    WS-->>C1: push frame
    WS-->>C2: push frame
    Note over C1,C2: UI re-renders without reload
    C1->>WS: disconnect -> server cleans session within heartbeat window
```

Client lifecycle: auto-reconnect with exponential backoff 1 s→30 s; on reconnect fetch REST snapshot first, then resubscribe. Fallback polling every 15 s if socket unavailable.

---

## Flow 8 — Global Leaderboard (read)

```mermaid
flowchart LR
    V[Visitor or logged-in user] --> FE["GET /api/leaderboard/global?page=1&size=50"]
    FE --> GW[API Gateway]
    GW --> LB[lb://leaderboard-service]
    LB --> R[(Redis ZREVRANGE leaderboard:global WITHSCORES)]
    R --> H{cache hit?}
    H -- yes --> RESP[paginated DTO list]
    H -- no --> R
    RESP --> V
```

Pagination uses ZSET range windows (`start/stop` offsets). Ties share rank per PRD §14.

---

## Flow 9 — Sport Leaderboard

Same as Flow 8 but `/api/leaderboard/{sportCode}`; service resolves the sport code against the sports catalog (cached Feign call), then reads `leaderboard:{sportCode}`. Unknown/disabled sport → 404/400. Because the code is data, a future `TENNIS` row instantly yields `leaderboard:tennis` reads with no code change.

Daily/weekly variants append `:daily:{date}` / `:weekly:{yyyy}-W{ww}` segments resolved from query params (defaults: today/current ISO week, UTC).

---

## Flow 10 — User Rank ("Where do I stand?")

```mermaid
sequenceDiagram
    actor P as Player
    participant GW as Gateway
    participant LB as leaderboard-service
    participant RD as Redis

    P->>GW: GET /api/leaderboard/{scope}/me (JWT)
    GW->>LB: forward + X-User-Id
    LB->>RD: ZREVRANK key userId (0-based -> convert to 1-based)
    LB->>RD: ZSCORE key userId
    LB->>RD: ZCARD key (total participants)
    LB-->>P: 200 {rank, score, totalPlayers}
    alt user absent from set (no submissions yet)
        LB-->>P: 200 {rank: null, totalPlayers}
    end
```

---

## Flow 11 — Score History

`GET /api/scores/me?page=&size=` → score-service → MySQL `score_history` (index: `(user_id, created_at DESC)`), paginated newest-first. Includes sport name resolved from cached catalog. Immutable records — corrections are new rows, never updates (auditability).

---

## Flow 12 — Top Players Report

```mermaid
flowchart LR
    AD[Admin or report page] -->|"GET /api/reports/top-players?sport=f1&from=2026-08-01&to=2026-08-25"| GW[Gateway: ADMIN role check]
    GW --> LB[leaderboard-service reporting component]
    LB --> MY[(MySQL score_db aggregates via read API)]
    MY --> CSV[Optional CSV export]
```

Reports intentionally read **MySQL**, not Redis: they answer historical windows whose Redis keys may have expired. Numbers must reconcile with SQL aggregates (PRD RPT-4).

---

## Flow 13 — Admin Flow (sport management)

```mermaid
sequenceDiagram
    actor AD as Admin
    participant R as React /admin
    participant GW as Gateway
    participant SP as sport-service

    AD->>R: create sport TENNIS
    R->>GW: POST /api/sports (Bearer ADMIN JWT)
    GW->>SP: route (gateway + service both check role claim)
    SP->>SP: validate unique code
    SP->>SP: INSERT sports row (active=true)
    SP-->>AD: 201 {id, code:"TENNIS", ...}
    Note over SP,RD: catalog caches evicted;<br/>leaderboard:tennis keys/topics derive lazily
```

Disable flow: PATCH `/api/sports/{id}/status` → subsequent submissions rejected 400; historical data and boards remain intact.

---

## Flow 14 — Error Flow (uniform)

```mermaid
flowchart TD
    REQ[Request] --> VAL{Bean Validation OK?}
    VAL -- no --> E400[400 problem+json field errors]
    VAL -- yes --> AUTHZ{Authenticated + authorized?}
    AUTHZ -- no --> E401403[401 or 403]
    AUTHZ -- yes --> BIZ{Business rules pass?}
    BIZ -- no --> EX[E409/E404/E422 domain errors]
    BIZ -- yes --> OK[2xx]
    EX --> H[Global @RestControllerAdvice maps exceptions to RFC 7807]
    E400 --> H
    E401403 --> H
    H --> LOG[structured error log + correlationId]
    H --> CL[client shows typed message]
```

Every 5xx carries a `correlationId`; the client surfaces it for support. Contract details in [DESIGN.md §Error handling](DESIGN.md).

---

## Flow 15 — Logout / Token Refresh

```mermaid
sequenceDiagram
    autonumber
    participant R as React
    participant GW as Gateway
    participant A as auth-service
    participant DB as MySQL

    Note over R: access token expired (or 401 received)
    R->>GW: POST /api/auth/refresh {refreshToken}
    GW->>A: forward
    A->>DB: lookup hashed refresh token
    alt valid & unrevoked & unexpired
        A->>DB: revoke old token, INSERT rotated token (reuse detected => revoke family)
        A-->>R: 200 {new accessToken, new refreshToken}
    else invalid/reused
        A-->>R: 401 -> redirect to /login
    end

    Note over R: explicit logout
    R->>GW: POST /api/auth/logout {refreshToken}
    GW->>A: forward
    A->>DB: mark refresh token revoked
    A-->>R: 204 ; client clears local tokens, closes WebSocket
```
