# score-service

Score submission, validation, and history (port 8084). Owns `score_db` (`scores`, `score_history`). Write path: validate → persist to MySQL → update Redis Sorted Sets → publish `score-submitted` event to Kafka.

Implementation: Phase 7 ([IMPLEMENTATIONPLAN.md](../../IMPLEMENTATIONPLAN.md)) · Flow: [APPFLOW.md §Flow 4](../../APPFLOW.md) · Schema: [SCHEMA.md §3.5–3.6](../../SCHEMA.md)
