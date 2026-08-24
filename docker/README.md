# docker

Docker Compose environment for the full stack: MySQL, Redis, Kafka, the seven backend services, and the frontend container — target `docker compose up --build` (Phase 14).

Planned contents:

- `docker-compose.yml` — infra + services with healthchecks and dependency ordering
- service Dockerfiles live beside each backend module; frontend Dockerfile in `frontend/`

Secrets are supplied via a git-ignored `.env` (see [`.env.example`](../.env.example)). No production deployment config lives here until Phase 15.
