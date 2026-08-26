# leaderboard-service

Central ranking engine (port 8085): global/sport/daily/weekly/season leaderboards and user rank from Redis Sorted Sets; Kafka consumer (`score-submitted`, idempotent on `eventId`); WebSocket/STOMP broadcasting at `/ws` (SockJS fallback, topics `/topic/leaderboards/{sport}`); period reports backed by MySQL history.

Implementation: Phases 8–10, 12 ([IMPLEMENTATIONPLAN.md](../../IMPLEMENTATIONPLAN.md)) · Redis keyspace: [SCHEMA.md §4](../../SCHEMA.md)
