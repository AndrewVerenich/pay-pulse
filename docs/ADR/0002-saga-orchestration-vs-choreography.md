# ADR 0002 — Orchestrated saga vs choreography

- Status: Accepted
- Date: 2026-06-18
- Context owner: payment-saga-orchestrator + participant teams
- Related plan section: [`docs/pay-pulse-platform-plan.md`](../pay-pulse-platform-plan.md) §4 «Saga Orchestration + DSL», stage S2
- Related ADRs: [0003](0003-event-sourcing-scope-ledger-only.md), [0004](0004-single-db-vs-database-per-service.md)

## Context

PayPulse проводит платёж через четыре участника с компенсациями на ранних сбоях:

1. `FRAUD_CHECK` (Compensable) — `participant-fraud-check`
2. `RISK_SCORING` (Compensable) — `participant-risk-scoring`
3. `LEDGER_APPLY` (Pivot) — `participant-ledger-apply`
4. `NOTIFY` (Retryable) — `participant-notification`

Два классических паттерна распределённых транзакций:

- **Choreography**: каждый сервис слушает события предыдущего и сам решает «что дальше». Слабая связность, но глобальное состояние размазано по топикам; stuck detection, force-complete и порядок compensation приходится выводить эвристиками.
- **Orchestration**: центральный координатор владеет порядком шагов, персистит состояние и шлёт command/reply.

Платформенный план (S6) явно требует Ops UI для stuck sagas, force-complete / retry / mark-resolved и трекинга `saga.compensation.failed`. Это плохо ложится на чистую choreography без отдельного «observability-агрегатора состояния».

Reference baseline: `distributed-backend-platform/saga-orchestrator` (Kotlin DSL + SEC model).

## Decision

Используем **orchestrated saga** в модуле `payment-saga-orchestrator`:

- Определение потока — Kotlin DSL в `PaymentSagaConfiguration` (тесты ссылаются как `PaymentSagaDefinition`):
  `FRAUD_CHECK(COMPENSABLE) → RISK_SCORING(COMPENSABLE) → LEDGER_APPLY(PIVOT) → NOTIFY(RETRYABLE)`.
- Движок: shared `saga-orchestrator-engine` / `saga-model` (step types `COMPENSABLE` / `PIVOT` / `RETRYABLE`).
- Состояние: schema `saga` — таблицы `saga_instance`, `saga_step` (+ compensation failure, Liquibase `006-saga-schema.xml`, `008-saga-compensation-failure.xml`).
- Транспорт: Kafka command/reply топики на участника; lifecycle — `payment.saga.events` / `saga.events` для BFF и метрик.
- Admin surface: stuck list + force-complete через orchestrator API → `bff-ops` → `ops-dashboard-ui` `/sagas/stuck`.

Choreography **не** используется как основной control-plane; event-driven реакция остаётся на read-side (проекции, Flink, BFF SSE).

## Consequences

### Positive

- Единый source of truth для статуса платежа в саге: `saga.saga_instance` + `saga.saga_step`.
- Stuck detection и admin recovery — SQL/API по одной схеме, без реконструкции графа из N consumer groups.
- Порядок compensation явный в engine (reverse of completed Compensable steps до Pivot).
- Метрики `paypulse_saga_*` и Grafana «Saga State» читаются из orchestrator без парсинга нескольких event logs.
- Интервью-friendly: SEC model и DSL видны в одном модуле.

### Negative / accepted limitations

- Orchestrator — critical path: недоступность блокирует новые саги (митигация: Postgres state + retry/timeout scheduler).
- Лишний hop `orchestrator → Kafka → participant → reply` vs прямой event chain.
- Изменение порядка шагов требует релиза orchestrator (не «подкрутить подписку» у участника).
- Участники обязаны говорить на command/reply контракте engine, а не на произвольных domain events.

## Alternatives considered

1. **Choreography-only** (каждый participant слушает `payment.events` / соседние replies) — rejected: плохой fit для stuck-saga admin, SEC documentation и единого timeline в `/payments/:id`.
2. **Temporal / Cadence / Conductor** — rejected для MVP: отдельный cluster, worker SDK, ops footprint несовместим с Compose-demo за одну неделю на этап.
3. **2PC / XA** — rejected: cross-service locks, несовместимо с async Kafka и compensation-first дизайном.
4. **Hybrid** (orchestrator только для ledger, остальное choreography) — rejected как premature complexity; четыре шага достаточно малы для одного definition.

## Code pointers

| Area | Path |
|------|------|
| Saga definition (DSL) | `payment-saga-orchestrator/src/main/kotlin/com/paypulse/saga/definition/PaymentSagaConfiguration.kt` |
| Definition tests | `payment-saga-orchestrator/src/test/kotlin/com/paypulse/saga/definition/PaymentSagaDefinitionTest.kt` |
| Compensation / SEC tests | `payment-saga-orchestrator/src/test/kotlin/com/paypulse/saga/SagaStateMachineCompensationTest.kt` |
| Schema | `liquibase/changelog/006-saga-schema.xml`, `008-saga-compensation-failure.xml` |
| Participants | `participant-fraud-check/`, `participant-risk-scoring/`, `participant-ledger-apply/`, `participant-notification/` |
| Stuck UI / BFF | `ops-dashboard-ui/src/pages/StuckSagasPage.tsx`, `bff-ops/.../StuckSagasQueryService.kt` |
| Metrics agg | `kstreams-saga-events-agg/` |

## See also / Revisit

- [ADR 0001](0001-balance-events-vs-snapshots.md) — balance projection остаётся отдельной от saga state.
- [ADR 0003](0003-event-sourcing-scope-ledger-only.md) — ledger пишет в `event_store`; сага не заменяет ES.
- Stage docs: [`docs/stages/S2.md`](../stages/S2.md), [`S6.md`](../stages/S6.md).

**Revisit triggers**

- Число шагов / ветвлений саги делает DSL нечитаемым → рассмотреть Temporal или declarative workflow engine.
- Нужны multi-tenant isolated orchestrators с независимым масштабированием → split DB ([ADR 0004](0004-single-db-vs-database-per-service.md)) + horizontal orchestrator pods.
- Появляется второй длинный процесс (refunds, chargebacks) с другим SEC графом → второй `*SagaDefinition`, не choreography по умолчанию.
