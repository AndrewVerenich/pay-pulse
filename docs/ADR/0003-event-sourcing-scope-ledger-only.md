# ADR 0003 — Граница Event Sourcing: только ledger / payment command

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: payment-command + participant-ledger-apply
- Связанные ADR: [0001](0001-balance-events-vs-snapshots.md), [0002](0002-saga-orchestration-vs-choreography.md)

## Контекст

Полный Event Sourcing на каждом bounded context даёт сильный audit trail и replay, но поднимает стоимость:

- schema evolution на каждом aggregate;
- snapshot / rebuild tooling;
- operational discipline (идемпотентность consumers, versioning payload).

PayPulse нужен:

1. **Canonical audit** инициации платежа и ledger-проводок.
2. **Temporal balance** «как было на момент T» ([ADR 0001](0001-balance-events-vs-snapshots.md)).
3. **Идемпотентное** применение saga-команд на участниках.

Не нужен ES-aggregate на fraud-check stub, notification, rule CRUD или auth sessions — там достаточно CRUD / command-reply + dedup-таблиц.

Референс: `distributed-backend-platform/event-sourcing-cqrs-banking` (`event_store` + outbox + projection).

## Решение

**Граница Event Sourcing узкая:**

| Bounded context | Модель персистентности |
|-----------------|------------------------|
| `payment-command-service` | **ES**: `payment_command.event_store` + transactional outbox → CDC → `payment.events` |
| `participant-ledger-apply` | Append в тот же `payment_command.event_store` + **idempotency** `participant_ledger.processed_commands` по `(saga_id, step_name)` — не отдельный ES-lifecycle |
| `projection-balance` / account-query | **CQRS-проекция** `account_query.balance_events` (не write-side ES) |
| `payment-saga-orchestrator` | State machine в `saga.*` ([ADR 0002](0002-saga-orchestration-vs-choreography.md)) |
| fraud / risk / notification | Command/reply + local `processed_commands` (или эквивалент), **без** event store |
| `rule-management-service` | CRUD в `rule_management` + outbox/CDC в compact `fraud_rules` |
| `auth-gateway` | `auth.refresh_tokens` + опциональный Redis blacklist ([ADR 0007](0007-jwt-hs256-and-redis-blacklist.md)) |

Итого: «event-sourced ledger / payment write path», а не «ES everywhere».

## Последствия

### Плюсы

- Чёткая граница: payment lifecycle events vs saga orchestration vs Flink stream processing.
- Temporal balance и analytics читают `payment.events` / проекции, не требуют replay всех доменов.
- Меньше поверхности schema-evolution; участники эволюционируют независимо.
- Idempotency ledger тестируется явно (`LedgerApplyHandlerIdempotencyTest`).

### Минусы / принятые ограничения

- Нет единого global event log на все домены; cross-service debug = Kafka + `saga_*` + structured logs (`paymentId` / `sagaId`).
- Полная история fraud decisions — в Flink side outputs / `fraud_alerts`, не в `event_store`.
- Rebuild всего «состояния мира» из одного store невозможен by design.

## Альтернативы

1. **ES на каждом participant** — отклонено: overhead без выигрыша для коротких command handlers.
2. **Только outbox без `event_store`** (mutable payment row) — отклонено: теряем immutable audit и parity с banking-портфолио.
3. **Отдельный ledger event store schema** — отложено: сейчас ledger пишет в `payment_command.event_store` (один canonical stream для CDC/ClickHouse); split возможен при database-per-service.
4. **Event sourcing саги** (каждый transition как event) — отклонено: state tables + `saga.events` достаточно для Ops timeline.

## Указатели в коде

| Область | Путь |
|---------|------|
| Payment command / DDL event store | `liquibase/changelog/001-payment-command.xml`, `004-event-store-account-id.xml` |
| Ledger apply + idempotency | `participant-ledger-apply/src/main/kotlin/.../LedgerRepositories.kt` |
| Тесты idempotency | `participant-ledger-apply/src/test/.../LedgerApplyHandlerIdempotencyTest.kt` |
| Balance projection | `liquibase/changelog/002-account-query.xml`, `projection-balance/` |
| CDC → Kafka | `debezium/connectors/payment-event-store.json`, `payment-outbox.json` |
| ClickHouse ingest | `clickhouse/init/` |

## См. также / когда пересмотреть

- [ADR 0001](0001-balance-events-vs-snapshots.md) — почему нет aggregate snapshots.
- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga state ≠ event store.
- [ADR 0005](0005-flink-vs-spark-streaming.md) — fraud stream отдельно от ES write path.

**Триггеры пересмотра**

- Refunds / chargebacks / partial captures → длинный event tail на `payment_id` → snapshots или dedicated Payment aggregate API ([ADR 0001](0001-balance-events-vs-snapshots.md)).
- Compliance требует immutable log решений risk/fraud в том же store → append-only `decision_events` (всё ещё не full ES aggregates).
- Нужен multi-ledger → выделить schema `ledger` и отдельный connector.
