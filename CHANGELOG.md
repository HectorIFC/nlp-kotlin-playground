# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0]

**Breaking change.** Replaces the synchronous in-memory pipeline of v0.0.x with a queue-based distributed architecture. The HTTP API path-param and response shape changed; the deployment shape changed from one container to four.

### Breaking changes

- `POST /upload` now returns **`202 Accepted`** with a `trainingId` instead of blocking the request thread for 30-60 s. Clients must poll `GET /api/training/{trainingId}` for progress and switch to `/explore/{trainingId}` when the training reaches `ready`.
- API path params changed from `{sessionId}` to `{trainingId}`:
  - `POST /api/search/{trainingId}`
  - `POST /api/tokenize/{trainingId}`
  - `POST /api/similarity/{trainingId}`
  - `GET /explore/{trainingId}`
- The legacy `GET /api/status/{sessionId}` endpoint is **removed**. Use `GET /api/training/{trainingId}` (richer payload including the event timeline).
- `POST /pretrained/{name}` still returns a JSON body with the key `sessionId` for client compatibility, but the value is now a `trainingId` persisted in SQLite. The training row has `corpus_blob_key = "bundled:{name}"` and starts in `READY`.
- `docker-compose up` now spins up **four containers** (app, MinIO, MinIO init, RabbitMQ) instead of one. Running the app standalone requires reachable MinIO + RabbitMQ instances.

### Added

- **MinIO** blob storage with two buckets (`corpus-uploads`, `trained-models`) and 1-day ILM rules.
- **RabbitMQ** durable queue topology (`training.exchange` + `training.queue` + DLX + DLQ) with manual acks.
- **SQLite** persistence via JetBrains Exposed (`trainings` + `training_events` tables, WAL mode, idempotent state-machine updates).
- Worker pool (`CONSUMER_CONCURRENCY` default `2`) that drains the queue with prefetch=1, executes the full Tessera + Mosaic pipeline, and uploads model artifacts back to MinIO.
- 8-state training state machine with strict transition validation.
- `GET /api/training/{id}` — training detail + full event timeline.
- `GET /api/trainings` — paginated list with `status=`, `since=`, `limit=` filters.
- `GET /api/trainings/active` — non-terminal trainings only.
- `GET /training/{id}/progress` — full-page progress timeline that auto-redirects to `/explore` on `ready`.
- `GET /trainings` — dashboard page with multi-select filters and 3 s auto-refresh, expandable event timelines per row.
- `GET /metrics` — Prometheus text format with four counters (`playground_trainings_{queued,completed,failed,expired}_total`).
- Structured JSON logging via Logback's `LogstashEncoder`. `training_id` propagated through the SLF4J MDC by both the route layer (Ktor plugin) and the consumer.
- `LOG_FORMAT=plain` env override for human-readable terminal logs.
- `ExpirationScheduler` daemon thread that moves READY trainings past their TTL to EXPIRED.
- Idempotent message processing in the consumer: re-deliveries hit a state-machine-aware short-circuit instead of corrupting in-flight work.

### Changed

- `gradle.properties`: `version=0.1.0`.
- `Dockerfile`: pre-warms the Gradle dependency cache before `COPY src` and creates `/data` owned by `nobody:nogroup` for the SQLite volume. Runtime image still under 350 MB.
- `docker-compose.yml`: four services with `depends_on: condition: service_healthy` chain; persistent named volumes for SQLite and MinIO.
- `Application.kt`: opens MinIO + RabbitMQ + SQLite on startup; ties consumer pool lifecycle to Ktor's `ApplicationStarted` / `ApplicationStopped` events.

### Removed

- `SessionStore` and `SessionEvictionScheduler` (in-memory session cache) — replaced by SQLite + `ExpirationScheduler`.
- `PipelineService` orchestrator — replaced by `TrainingService` (queue-driven) and `TrainingPipelineLoader` (read-side cache).

### Documentation

- README rewritten around the four-container topology, with an architecture Mermaid diagram and a "Why this architecture?" section explaining the MinIO / SQLite / polling trade-offs.
- ARCHITECTURE.md fully rewritten: producer + consumer request flow, state-machine diagram, idempotency contract, cleanup paths, design-decision table.
- New 75-90 s demo video (`docs/demo.mp4`) showing upload → progress → search and a DLQ failure case.

## [0.0.3] — 2026-05-23

Bug-fix release after v0.0.1; container metadata + workflow stabilization. No API changes.

## [0.0.1] — 2026-05-23

Initial public release. Synchronous in-memory playground with three pre-trained corpora.

### Added

- Phase 0 scaffolding: Gradle + Ktor + Docker + GitHub Actions.
- Tessera + Mosaic wiring via JitPack.
- Bundled corpora: Alice in Wonderland, Shakespeare's Sonnets, Kotlin stdlib KDocs.
- Search / Tokenize / Compare endpoints + frontend.
