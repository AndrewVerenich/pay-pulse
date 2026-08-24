# ADR 0003 — Event sourcing scope: ledger / payment command only

- Status: Accepted
- Date: 2026-06-18
- Context owner: payment-command + participant-ledger-apply teams
- Related plan section: [`docs/pay-pulse-platform-plan.md`](../pay-pulse-platform-plan.md) §4 «Event Sourcing для ledger», S0–S1
- Related ADRs: [0001](0001-balance-events-vs-snapshots.md), [0002](0002-saga-orchestration-vs-choreography.md)

## Context

Полный Event Sourcing на каждом bounded context даёт сильный audit trail и replay, но поднимает стоимость:

- schema evolution на каждом aggregate;
- snapshot / rebuild tooling;
- operational discipline (идемпотентность consumers, versioning payload).

PayPulse нужен:

1. **Canonical audit** инициации платежа и ledger-проводок.
2. **Temporal balance** «как было на момент T» ([ADR 0001](0001-balance-events-vs-snapshots.md)).
3. **Idempotent** применение saga-команд на участниках.

Не нужен ES-aggregate на fraud-check stub, notification, rule CRUD или auth sessions — там достаточно CRUD / command-reply + dedup tables.

Reference: `distributed-backend-platform/event-sourcing-cqrs-banking` (`event_store` + outbox + projection).

## Decision

**Граница Event Sourcing узкая:**

| Bounded context | Persistence model |
|-----------------|-------------------|
| `payment-command-service` | **ES**: `payment_command.event_store` + transactional outbox → CDC → `payment.events` |
| `participant-ledger-apply` | Append в тот же `payment_command.event_store` + **idempotency** `participant_ledger.processed_commands` по `(saga_id, step_name)` — не отдельный ES-aggregate lifecycle |
| `projection-balance` / account-query | **CQRS projection** `account_query.balance_events` (не write-side ES) |
| `payment-saga-orchestrator` | State machine tables в `saga.*` ([ADR 0002](0002-saga-orchestration-vs-choreography.md)) |
| fraud / risk / notification participants | Command/reply + local `processed_commands` (или эквивалент), **без** event store |
| `rule-management-service` | CRUD в `rule_management` + outbox/CDC в compact `fraud_rules` |
| `auth-gateway` | `auth.refresh_tokens` + optional Redis blacklist ([ADR 0007](0007-jwt-hs256-and-redis-blacklist.md)) |

Итого: «event-sourced ledger / payment write path», не «event-sourced platform everywhere».

## Consequences

### Positive

- Чёткая граница: payment lifecycle events vs saga orchestration vs Flink stream processing.
- Temporal balance и analytics читают `payment.events` / projections, не требуют replay всех доменов.
- Меньше schema-evolution поверхности; участники эволюционируют независимо.
- Ledger idempotency тестируется явно (`LedgerApplyHandlerIdempotencyTest`).

### Negative / accepted limitations

- Нет единого global event log на все домены; cross-service debug = Kafka topics + `saga_*` + structured logs (`paymentId` / `sagaId`).
- «Полная история» fraud decisions живёт в Flink side outputs / `fraud_alerts`, не в `event_store`.
- Rebuild всего «состояния мира» из одного store невозможен by design.

## Alternatives considered

1. **ES на каждом participant** — rejected: overhead без выигрыша для short-lived command handlers.
2. **Только outbox без `event_store`** (mutable payment row) — rejected: теряем immutable audit и reference-parity с banking portfolio.
3. **Отдельный ledger event store schema** — deferred: сейчас ledger пишет в `payment_command.event_store` (один canonical stream для CDC/ClickHouse); split возможен при database-per-service.
4. **Event sourcing саги** (каждый step transition как event) — rejected: state tables + `saga.events` достаточно для Ops timeline.

## Code pointers

| Area | Path |
|------|------|
| Payment command / event store DDL | `liquibase/changelog/001-payment-command.xml`, `004-event-store-account-id.xml` |
| Ledger apply + idempotency | `participant-ledger-apply/src/main/kotlin/.../LedgerRepositories.kt` |
| Idempotency tests | `participant-ledger-apply/src/test/.../LedgerApplyHandlerIdempotencyTest.kt` |
| Balance projection | `liquibase/changelog/002-account-query.xml`, `projection-balance/` |
| CDC → Kafka | `debezium/connectors/payment-event-store.json`, `payment-outbox.json` |
| ClickHouse raw ingest | `clickhouse/init/` |

## See also / Revisit

- [ADR 0001](0001-balance-events-vs-snapshots.md) — почему нет aggregate snapshots.
- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga state ≠ event store.
- [ADR 0005](0005-flink-vs-spark-streaming.md) — fraud stream отдельно от ES write path.

**Revisit triggers**

- Refunds / chargebacks / partial captures → длинный event tail на `payment_id` → snapshots или dedicated Payment aggregate API ([ADR 0001](0001-balance-events-vs-snapshots.md)).
- Compliance требует immutable log решений risk/fraud в том же store → append-only `decision_events` (всё ещё не full ES aggregates).
- Нужен multi-ledger (multi-currency books) → выделить schema `ledger` и отдельный connector.
