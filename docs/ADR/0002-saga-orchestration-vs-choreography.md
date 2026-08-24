# ADR 0002 — Orchestrated saga vs choreography

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: payment-saga-orchestrator + participants
- Связанные ADR: [0003](0003-event-sourcing-scope-ledger-only.md), [0004](0004-single-db-vs-database-per-service.md)

## Контекст

PayPulse проводит платёж через четыре участника с компенсациями на ранних сбоях:

1. `FRAUD_CHECK` (Compensable) — `participant-fraud-check`
2. `RISK_SCORING` (Compensable) — `participant-risk-scoring`
3. `LEDGER_APPLY` (Pivot) — `participant-ledger-apply`
4. `NOTIFY` (Retryable) — `participant-notification`

Два классических паттерна распределённых транзакций:

- **Choreography**: каждый сервис слушает события предыдущего и сам решает «что дальше». Слабая связность, но глобальное состояние размазано по топикам; stuck detection, force-complete и порядок compensation приходится выводить эвристиками.
- **Orchestration**: центральный координатор владеет порядком шагов, персистит состояние и шлёт command/reply.

Ops UI требует stuck sagas, force-complete / retry / mark-resolved и трекинг `saga.compensation.failed`. Это плохо ложится на чистую choreography без отдельного агрегатора состояния.

Референс: `distributed-backend-platform/saga-orchestrator` (Kotlin DSL + модель SEC).

## Решение

Используем **orchestrated saga** в модуле `payment-saga-orchestrator`:

- Определение потока — Kotlin DSL в `PaymentSagaConfiguration` (в тестах — `PaymentSagaDefinition`):
  `FRAUD_CHECK(COMPENSABLE) → RISK_SCORING(COMPENSABLE) → LEDGER_APPLY(PIVOT) → NOTIFY(RETRYABLE)`.
- Движок: shared `saga-orchestrator-engine` / `saga-model` (типы шагов `COMPENSABLE` / `PIVOT` / `RETRYABLE`).
- Состояние: schema `saga` — `saga_instance`, `saga_step` (+ compensation failure; Liquibase `006-saga-schema.xml`, `008-saga-compensation-failure.xml`).
- Транспорт: Kafka command/reply на участника; lifecycle — `payment.saga.events` / `saga.events` для BFF и метрик.
- Admin: stuck list + force-complete через API orchestrator → `bff-ops` → `ops-dashboard-ui` `/sagas/stuck`.

Choreography **не** используется как основной control-plane; event-driven реакция остаётся на read-side (проекции, Flink, BFF SSE).

## Последствия

### Плюсы

- Единый source of truth статуса платежа в саге: `saga.saga_instance` + `saga.saga_step`.
- Stuck detection и admin recovery — SQL/API по одной схеме, без реконструкции графа из N consumer groups.
- Порядок compensation явный в engine (reverse завершённых Compensable до Pivot).
- Метрики `paypulse_saga_*` и Grafana «Saga State» читаются из orchestrator без парсинга нескольких event logs.
- Удобно для интервью: SEC и DSL видны в одном модуле.

### Минусы / принятые ограничения

- Orchestrator на critical path: недоступность блокирует новые саги (митигация: Postgres state + retry/timeout scheduler).
- Лишний hop `orchestrator → Kafka → participant → reply` vs прямая цепочка событий.
- Смена порядка шагов требует релиза orchestrator.
- Участники обязаны говорить на command/reply контракте engine, а не на произвольных domain events.

## Альтернативы

1. **Только choreography** — отклонено: плохой fit для stuck-saga admin, документации SEC и единого timeline в `/payments/:id`.
2. **Temporal / Cadence / Conductor** — отклонено для MVP: отдельный cluster, worker SDK, ops footprint несовместим с Compose-демо.
3. **2PC / XA** — отклонено: cross-service locks, несовместимо с async Kafka и compensation-first.
4. **Hybrid** (orchestrator только для ledger) — отклонено как premature complexity; четыре шага достаточно малы для одного definition.

## Указатели в коде

| Область | Путь |
|---------|------|
| Определение саги (DSL) | `payment-saga-orchestrator/src/main/kotlin/com/paypulse/saga/definition/PaymentSagaConfiguration.kt` |
| Тесты definition | `payment-saga-orchestrator/src/test/kotlin/com/paypulse/saga/definition/PaymentSagaDefinitionTest.kt` |
| Compensation / SEC | `payment-saga-orchestrator/src/test/kotlin/com/paypulse/saga/SagaStateMachineCompensationTest.kt` |
| Schema | `liquibase/changelog/006-saga-schema.xml`, `008-saga-compensation-failure.xml` |
| Participants | `participant-fraud-check/`, `participant-risk-scoring/`, `participant-ledger-apply/`, `participant-notification/` |
| Stuck UI / BFF | `ops-dashboard-ui/src/pages/StuckSagasPage.tsx`, `bff-ops/.../StuckSagasQueryService.kt` |
| Метрики | `kstreams-saga-events-agg/` |

## См. также / когда пересмотреть

- [ADR 0001](0001-balance-events-vs-snapshots.md) — balance projection отдельно от saga state.
- [ADR 0003](0003-event-sourcing-scope-ledger-only.md) — ledger пишет в `event_store`; сага не заменяет ES.

**Триггеры пересмотра**

- Число шагов / ветвлений делает DSL нечитаемым → Temporal или declarative workflow engine.
- Нужны multi-tenant isolated orchestrators → split DB ([ADR 0004](0004-single-db-vs-database-per-service.md)) + horizontal pods.
- Второй длинный процесс (refunds, chargebacks) с другим SEC-графом → второй `*SagaDefinition`, не choreography по умолчанию.
