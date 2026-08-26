# Project Rules

Non-negotiable development rules for this repository. PRs violating these are rejected regardless of other merits.

---

## Architecture Rules
- Respect microservice boundaries. **No monolith, ever** — no shared business-logic library across services.
- The frontend talks to the API Gateway only. Direct service URLs must never appear in frontend code or config.
- No service reads/writes another service's database tables. Cross-service access = OpenFeign API call or Kafka event.
- Synchronous call chains on hot paths max depth: one hop. Deeper = use events.
- Sports data is configuration in MySQL. Zero hardcoded sport codes (`FOOTBALL`/`CRICKET`/`F1`) in business logic; keys/topics derive from `sports.code`.
- Redis Sorted Sets are the only mechanism for *current* leaderboard ranking. MySQL `ORDER BY` serves historical reports only.

## Java Rules
- Java 17 only (LTS). Do not use Java 21+ features (virtual threads, sequenced collections, record patterns). Parent POM pins `<maven.compiler.release>17</maven.compiler.release>`.
- Clean code: meaningful names, small methods, no cleverness that needs a comment to survive review.
- Avoid speculative abstraction — three concrete usages before generalizing.
- Prefer records for immutable DTOs; keep entities out of API contracts.

## Spring Rules
- Constructor injection exclusively (no field `@Autowired`; no setter injection).
- DTOs at every boundary. Entities never serialized to/from REST.
- Validate every incoming request with Bean Validation (`@Valid` + constraints); never trust size/range from the client silently.
- Centralize exception translation in one `@RestControllerAdvice` per service; return RFC 7807 problem+json.
- Correct HTTP semantics: 201 created, 204 deleted, 400 validation, 401 unauthenticated, 403 forbidden, 404 unknown id, 409 conflict, 422 rule violation, 429 throttled.
- Configuration values come from environment placeholders (`${JWT_SECRET}`), never literals for anything secret or environment-specific.

## Database Rules
- Use JPA/Hibernate deliberately: fetch joins or `@EntityGraph` to dodge N+1; pagination mandatory on list endpoints; indexes added with the query they serve ([SCHEMA.md](SCHEMA.md)).
- Migrations via Flyway from the first table; no schema drift by hand.
- Never store plain-text passwords or unhashed refresh tokens.
- Money/precision-sensitive numbers use DECIMAL, not floating point.

## Redis Rules
- Sorted Sets mandatory for leaderboards; consistent key naming per [SCHEMA.md §4.1](SCHEMA.md) — any new key pattern requires a doc update in the same PR.
- Windowed keys always get TTLs at creation time.
- Idempotency markers (`processed:event:{eventId}`) checked before applying consumer effects.
- No blocking commands (`KEYS`) in request paths.

## Kafka Rules
- Events carry `eventId` + `eventVersion`; additive evolution only within a version; breaking change ⇒ new `.v2` topic.
- Consumers must be idempotent where effects are non-idempotent (score increments).
- Publish only after the corresponding DB commit succeeds.
- No sensitive data in events: ids and scores, not credentials or PII beyond usernames-adjacent display data.

## WebSocket Rules
- Server is the sole publisher to `/topic/**`; clients may only subscribe.
- Clients must not be allowed to publish to `/topic/**` or send arbitrary leaderboard data.
- Broadcast only occurs after successful Redis update; Redis failure → no broadcast.
- WebSocket message schema is server-controlled; `eventType` is `LEADERBOARD_UPDATED` (future types may be added).
- CORS origins are configured via `WEBSOCKET_ALLOWED_ORIGINS`; no wildcard in production.
- No JWT or secrets in WebSocket messages; leaderboard topics are public (read-only).

## Git Rules
- No direct commits to `main` or `develop` — everything through feature branches + PRs.
- Branch names: `feature/*`, `release/*`, `hotfix/*`. Never create long-lived per-service branches.
- Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`) with meaningful scopes.
- Small PRs: one logical change; description links the tracker item it advances.
- **No secrets in Git, ever** — including "temporary" ones. `.env` is git-ignored; see [SECURITY.md](SECURITY.md).

## Frontend Rules
- TypeScript `strict: true`; no `any` without an inline justification comment and reviewer sign-off.
- Reusable components over copy-paste; leaderboard widgets parameterized by sport code from data.
- One centralized API client (base URL = gateway) with token refresh interception; components never build URLs themselves.
- Tokens handled safely: refresh token never exposed to third-party scripts; logout clears storage and closes sockets.
- WebSocket lifecycle owned by one hook/provider: connect, heartbeat-aware reconnect (exponential backoff), snapshot resync, clean teardown on unmount/logout.

## Security Rules
- Never commit credentials, tokens, or connection strings. Never log passwords, JWTs, or secrets.
- Never expose secrets to the frontend bundle.
- Validate all input server-side even if the client validates too.
- Identity headers (`X-User-Id`, `X-User-Role`) are set by the gateway only; services reject them from any external origin.
- Security-relevant changes require updating [SECURITY.md](SECURITY.md) in the same PR.
