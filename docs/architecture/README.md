# PayPulse — Architecture docs index

Оглавление архитектурных материалов и связанных технических документов.

## ADR index

| ADR | Title |
|-----|-------|
| [0001](../ADR/0001-balance-events-vs-snapshots.md) | Temporal balance via `balance_events` (no aggregate snapshots) |
| [0002](../ADR/0002-saga-orchestration-vs-choreography.md) | Orchestrated saga vs choreography |
| [0003](../ADR/0003-event-sourcing-scope-ledger-only.md) | Event sourcing scope: payment command + ledger only |
| [0004](../ADR/0004-single-db-vs-database-per-service.md) | Single Postgres with schema-per-service |
| [0005](../ADR/0005-flink-vs-spark-streaming.md) | Flink vs Spark for fraud streaming |
| [0006](../ADR/0006-analytics-split-superset-vs-react.md) | Superset/dbt vs React Ops Dashboard |
| [0007](../ADR/0007-jwt-hs256-and-redis-blacklist.md) | JWT HS256 + Redis `jti` blacklist |
| [0008](../ADR/0008-no-distributed-tracing-mvp.md) | No distributed tracing in MVP |

## Related ops docs

- [`docs/chaos.md`](../chaos.md) — failure drills (Kafka, Flink TM, saga compensation)
- [`observability/README.md`](../../observability/README.md) — Prometheus / Grafana / exporters
- Root [`README.md`](../../README.md) — stack, URLs, demo scenarios
