# Security Policy & Design

**Product:** Real-Time Leaderboard System
**Status legend:** ✅ **Implemented & tested** · **P** = Planned (phase-tracked). See [TRACKER.md](TRACKER.md) for exact state.

### Implemented as of Phase 6 (leaderboard-service)

- BCrypt (strength 10) password hashing; plaintext never stored or returned.
- JWT HS256 access tokens: secret from `JWT_SECRET` (≥ 32 bytes enforced; documented placeholder value refuses startup), default TTL 15 min (`JWT_ACCESS_TOKEN_EXPIRATION`).
- Opaque refresh tokens (512-bit random): only SHA-256 hash persisted, TTL 7 days (`JWT_REFRESH_TOKEN_EXPIRATION`), revocable via logout.
- Spring Security 6 stateless filter chain; public: register/login/refresh + actuator health; `/api/auth/admin/**` requires ROLE_ADMIN.
- Uniform JSON error responses; generic login failure message (no account enumeration); duplicate registration → 409.
- Verified live end-to-end through the API Gateway against MySQL (register/login/refresh/logout/RBAC/invalid-token paths).

#### Sport Service authorization (Phase 3)

- **One JWT system:** auth-service issues tokens; sport-service only *validates* signatures with the shared `JWT_SECRET`. Its `JwtService` deliberately has no generation methods and there is no user table or credential logic in sport-service.
- **Public read endpoints** (no token required): `GET /api/sports`, `GET /api/sports/{id}`, `GET /api/sports/code/{code}`, `GET /api/competitions`, `GET /api/competitions/{id}`, `GET /api/sports/{sportId}/competitions`.
- **ADMIN-only management** (`hasRole("ADMIN")` on POST/PUT/PATCH/DELETE): sport create/update/status/delete and competition create/update/status/delete — including the nested `/api/sports/{sportId}/competitions` POST. Missing token → 401; valid USER token → 403.
- Invalid/expired/garbage Bearer tokens are rejected by the filter (context cleared → anonymous); tampered role claims break the signature and are ignored.
- Verified live through the gateway: anonymous reads 200, USER writes 403, ADMIN writes 201/200, no-token writes 401.

#### Score Service authorization (Phase 4)

- **JWT validation-only:** score-service validates tokens using the shared `JWT_SECRET` via its own `JwtService` (no token generation). Same pattern as sport-service — tokens issued by auth-service, validated locally.
- **Authenticated endpoints** (any valid token): `POST /api/scores` (submit), `GET /api/scores/me` (own history), `GET /api/scores/{id}` (by ID — ownership enforced: USER sees own scores only, ADMIN sees any).
- **ADMIN-only endpoints:** `GET /api/scores` (search with filters — userId, sportId, eventId, scoreType, from, to), `DELETE /api/scores/{id}` (hard delete).
- **Ownership enforcement:** `GET /api/scores/{id}` returns 403 Forbidden when a USER attempts to access another user's score. ADMIN is exempt.
- **Duplicate submission prevention:** optional `submissionId` is unique per user (`uk_scores_user_submission`); resubmitting returns 409 Conflict.
- **Sport validation at submit time:** score-service calls sport-service via `@LoadBalanced RestTemplate` to verify the sport exists and is active. Returns 404 if sport missing, 409 if inactive, 503 if sport-service is unreachable. This cross-service call is the only synchronous dependency; its failure correctly rejects the submission.
- Missing token → 401; valid USER token on ADMIN endpoint → 403.
- Verified live through the gateway: user submits Football/Cricket/F1 scores (201), user reads own history (200), user denied other user's score (403), admin searches with filters (200), admin deletes score (200), invalid sport returns 404, duplicate submission returns 409, no auth returns 401.

#### User Service authorization (Phase 5)

- **JWT validation-only:** user-service validates tokens using the shared `JWT_SECRET` via its own `JwtService` (no token generation). Same pattern as sport/score-service — tokens issued by auth-service, validated locally.
- **Public read endpoints** (no token required): `GET /api/players` (paginated list, active players only), `GET /api/players/{id}` (by ID). Search via `?search=` query parameter is also public.
- **Authenticated endpoints** (any valid token): `POST /api/players` (create player profile).
- **ADMIN-only endpoints** (require `ROLE_ADMIN`): `PUT /api/players/{id}` (update profile), `PUT /api/players/{id}/deactivate`, `PUT /api/players/{id}/activate`, `DELETE /api/players/{id}` (hard delete).
- **Email uniqueness:** duplicate email on create/update → 409 Conflict.
- **Soft-delete pattern:** `active` flag; deactivated players are filtered from list queries but still accessible by ID.
- Missing token on protected endpoint → 401; valid USER token on ADMIN endpoint → 403.
- Verified live through the gateway: public list/read (200), create with auth (201), update by ADMIN (200), deactivate/activate (204), delete by ADMIN (204), USER cannot update/delete (403), no token on protected (401), duplicate email (409), invalid email (400).

#### Leaderboard Service authorization (Phase 6)

- **JWT validation-only:** leaderboard-service validates tokens using the shared `JWT_SECRET` via its own `JwtService` (no token generation). Same pattern as other services.
- **Public read endpoints** (no token required): `GET /api/leaderboards/{sport}/top`, `/api/leaderboards/{sport}` (paginated), `/api/leaderboards/{sport}/players/{userId}/rank`, `/api/leaderboards/{sport}/players/{userId}/nearby`, `/api/leaderboards/{sport}/size`.
- **Authenticated endpoint:** `GET /api/leaderboards/{sport}/me` (returns current user's rank; `@AuthenticationPrincipal` extracts userId from JWT).
- **Internal-only endpoints** (shared-secret protected): `POST /internal/leaderboards/scores` (score update notification from score-service), `POST /internal/leaderboards/{sport}/rebuild` (rebuild board from score-service data). Protected by `X-Internal-Service-Secret` header; wrong/missing secret → 403 Forbidden.
- **CSRF disabled, stateless session** (no cookies; Bearer tokens only).
- Missing/invalid token on `/me` → 401; wrong internal secret → 403; unknown sport → 400.
- Verified live through the gateway: leaderboard returns correct rankings after score submission; internal API rejects wrong secret (403) and accepts correct secret (200).

### Still planned (not yet implemented)

- Refresh-token rotation with reuse detection (currently validate-and-revoke only).
- API Gateway-level JWT signature validation (services enforce today).
- Brute-force lockout/backoff, per-user rate limiting on auth endpoints.
- TLS termination, dependency-CVE scanning, secret-rotation runbooks.

---

## 1. Authentication

| Control | Decision |
|---|---|
| Password storage | BCrypt, strength factor ≥ 10. Plain passwords exist only transiently in the registration/login request DTO and are never logged. |
| Access token | JWT, HS256, secret = `JWT_SECRET` env var (≥ 32 random bytes), TTL = `JWT_EXPIRATION` ms. Claims: `sub`(userId), `username`, `role`, `iat`, `exp`, `jti`. |
| Refresh token | Opaque random (≥ 256-bit; implementation uses 512-bit) value; **only its SHA-256 hash is stored** (`refresh_tokens.token_hash`). TTL 7 days. Logout revokes the token. **Rotation-on-use and reuse-of-revoked-token family revocation are planned hardening — not yet implemented.** |
| Logout | Revokes server-side refresh token; client discards access token (short TTL bounds its residual value). |
| Transport | TLS terminated at gateway in deployed environments; local Compose traffic stays inside the private Docker network. |

## 2. Authorization

- Roles: `USER`, `ADMIN` (static assignment in V1; seeded admin via env-configured bootstrap credentials on first run).
- Enforcement layers:
  1. API Gateway: route-level auth (public vs authenticated) + signature/expiry validation.
  2. Service: re-checks claims per endpoint; ADMIN endpoints require role claim.
- Identity propagation: gateway injects `X-User-Id` / `X-User-Role`; services ignore these headers on any request not originating from inside the trusted network.
- Ownership checks: profile/history endpoints operate only on the token's own userId.

## 3. Secrets Management

Principles:
1. Secrets live in **environment variables only** — never source code, never application.yml literals, never docs.
2. Local development uses a git-ignored `.env` file (Compose + IDE env injection read it).
3. `.env.example` documents every required variable with placeholder values only.
4. CI/CD uses GitHub Secrets; deployment hosts use their native secret stores.
5. Rotation: JWT secret rotation invalidates outstanding tokens (acceptable during off-hours early on); DB credential rotation documented in the deploy runbook (Phase 15).

### Required Environment Variables

| Variable | Consumed by | Placeholder example | Notes |
|---|---|---|---|
| `MYSQL_HOST` | all MySQL-backed services | `localhost` | |
| `MYSQL_PORT` | all MySQL-backed services | `3306` | |
| `MYSQL_DATABASE` | per-service (one each) | `leaderboard` | services override with their schema name via service-specific vars as they land |
| `MYSQL_USERNAME` | all MySQL-backed services | `your_username` | least-privileged user per schema |
| `MYSQL_PASSWORD` | all MySQL-backed services | `your_password` | never committed anywhere |
| `REDIS_HOST` | score/leaderboard services | `localhost` | |
| `REDIS_PORT` | score/leaderboard services | `6379` | |
| `REDIS_PASSWORD` | score/leaderboard services | *(empty locally)* | required in any network-exposed Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | score/leaderboard services | `localhost:9092` | |
| `JWT_SECRET` | auth-service, sport-service, score-service, api-gateway, leaderboard-service | `replace_with_secure_random_secret` | ≥ 32 bytes; the literal placeholder value is rejected at startup |
| `INTERNAL_SERVICE_SECRET` | score-service, leaderboard-service | `replace_with_secure_random_secret` | Shared secret for internal service-to-service API calls |
| `JWT_ACCESS_TOKEN_EXPIRATION` | auth-service | `900000` | ms; default 15 minutes |
| `JWT_REFRESH_TOKEN_EXPIRATION` | auth-service | `604800000` | ms; default 7 days |
| `EUREKA_SERVER_URL` | all services | `http://localhost:8761/eureka` | |
| `API_GATEWAY_URL` | frontend build | `http://localhost:8080` | |

Service-specific additions (ports, CORS origin, Kafka DLT settings) will be appended here as phases introduce them.

## 4. .gitignore Security Posture

Root `.gitignore` enforces:

```gitignore
.env
.env.*
!.env.example
```

plus build output (`target/`, `dist/`, `node_modules/`), IDE files (`.idea/`, `.vscode/`, `*.iml`), logs, OS junk, and coverage reports. Documentation files are explicitly never ignored.

Pre-commit habit (until tooling lands): `git status` review before every commit; any accidental secret staging → rotate the secret immediately, do not just delete the file from history.

## 5. Threat Model & Mitigations

Status legend: **P** = Planned (phase-tracked). Nothing is claimed complete pre-implementation.

| # | Threat | Vector | Mitigation | Status |
|---|---|---|---|---|
| T-01 | Password theft from DB | Dump of `users` table | BCrypt hashing; no reversible storage; salts built-in | P (Phase 4) |
| T-02 | Password theft in transit | Sniffed HTTP login | TLS at edge (deployed); credentials never logged | P |
| T-03 | JWT theft | XSS / stolen localStorage token | Short access TTL; refresh rotation with family revocation on reuse; strict CSP later; no tokens in URLs | P |
| T-04 | Brute-force login | Credential stuffing on `/api/auth/login` | Progressive lockout/backoff per account+IP; gateway rate limits; generic error messages (no enumeration) | P |
| T-05 | SQL injection | Malformed inputs to queries | JPA parameterized queries exclusively; validation layer; no string-concatenated SQL | P |
| T-06 | XSS | Script injection through usernames/bios/profile fields | React auto-escaping; no `dangerouslySetInnerHTML`; input length/format caps | P |
| T-07 | CSRF | Cross-site state-changing requests | Stateless Bearer-token APIs (no cookie sessions ⇒ CSRF largely moot); WS STOMP requires explicit connect headers | P |
| T-08 | CORS misconfiguration | Overly broad `Access-Control-Allow-Origin` | Allow-list single frontend origin from env; no wildcard with credentials | P (Phase 3) |
| T-09 | Sensitive data leakage in logs/errors | Stack traces or payloads logged | Problem+json responses without internals; secret/token log scrubbing; correlation IDs instead of context dumps | P |
| T-10 | Redis exposure | Public bind / no auth | Bind inside Compose network only; `REDIS_PASSWORD` mandatory in any shared environment; no `KEYS`/dangerous commands exposed via app | P (Phase 8/14) |
| T-11 | Kafka exposure | External producer injecting events | Broker bound to private network; ACLs documented for hosted deployment; consumers validate event shape/version | P (Phase 9/14) |
| T-12 | Secret leakage via Git | Committed `.env`, hardcoded keys | Ignored by default; `.env.example` placeholders only; review checklist; immediate rotation policy on accident | Active policy |
| T-13 | Unauthorized score submission | Forged/direct service calls bypassing gateway | Services unreachable publicly (gateway-only ingress); JWT required; identity headers set solely by gateway | P |
| T-14 | Score tampering | Client-side manipulation, replayed submissions | Server-side validation of ranges/rates; immutable history rows; rate limiting; anomaly reporting (Phase 12+) | P |
| T-15 | Replay / double-processing | Kafka redelivery duplicating increments | Consumer idempotency on `eventId` (Redis marker + unique DB constraint backstop) | P (Phase 9) |
| T-16 | Dependency vulnerabilities | Known CVEs in libraries | Boot/Cloud managed versions kept current; dependency-check scan added with CI hardening (Phase 13) | P |

## 6. Reporting & Response

- Suspected vulnerability or leaked credential: open a limited-detail issue tagged `security` and rotate affected secrets immediately.
- Incident runbooks (compromised JWT secret, Redis poisoning, DLT backlog) are deliverables of Phases 9–15 and will be linked here.
