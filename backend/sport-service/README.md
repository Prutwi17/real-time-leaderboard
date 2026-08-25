# sport-service

Sports + competitions catalog (port 8083): public reads, ADMIN management, enable/disable. Owns `leaderboard_sport` (`sports`, `competitions`). Seed data: FOOTBALL, CRICKET, F1 — inserted idempotently at startup by `DefaultSportsInitializer`, never duplicated on restart.

Supported sports are constrained by the closed `SportCode` enum (FOOTBALL, CRICKET, F1); unsupported codes are rejected with HTTP 400. Competitions belong to exactly one sport (`ManyToOne`); deleting a sport with competitions returns 409 CONFLICT.

Security follows the shared JWT architecture: auth-service issues tokens, this service only validates signatures (`security/JwtService` has no generation methods). Reads are public; all writes require ROLE_ADMIN.

Run: set `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `JWT_SECRET` env vars (defaults: `leaderboard_sport` DB, port 8083), then `.\mvnw.cmd spring-boot:run`.

Tests: `.\mvnw.cmd -B clean test` (H2 in-memory, no MySQL needed).

Implementation: Phase 3 of this build-out / Phase 6 of the plan ([IMPLEMENTATIONPLAN.md](../../IMPLEMENTATIONPLAN.md)) · Schema: [SCHEMA.md §3.4](../../SCHEMA.md)
