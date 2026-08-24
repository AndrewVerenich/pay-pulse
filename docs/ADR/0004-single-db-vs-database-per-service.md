# ADR 0004 — Single PostgreSQL with schemas vs database-per-service

- Status: Accepted
- Date: 2026-06-18
- Context owner: platform / Liquibase / Debezium
- Related ADRs: [0002](0002-saga-orchestration-vs-choreography.md), [0003](0003-event-sourcing-scope-ledger-only.md)

## Context

Классический microservice advice — **database-per-service**: изоляция blast radius, независимый scaling и ownership миграций.

Для PayPulse первичный target — **portfolio / interview demo** на Docker Compose:

- один `docker compose up` поднимает OLTP + Kafka + analytics overlays;
- один Liquibase init-container применяет весь changelog;
- Debezium connectors читают один Postgres (logical decoding / replication slot).

True DB-per-service на ноутбуке умножает контейнеры, слоты, credentials и время cold-start без выигрыша для демо-SLO.

## Decision

Один экземпляр PostgreSQL (`paypulse`) с **schema-per-service** (логически отдельные bounded contexts, физически одна БД):

| Schema | Owner / consumers |
|--------|-------------------|
| `payment_command` | `payment-command-service`, `participant-ledger-apply` (writes to `event_store`) |
| `account_query` | `projection-balance`, account-query reads |
| `saga` | `payment-saga-orchestrator` |
| `participant_fraud` | `participant-fraud-check` |
| `participant_risk` | `participant-risk-scoring` |
| `participant_ledger` | `participant-ledger-apply` (`processed_commands`) |
| `participant_notification` | `participant-notification` |
| `auth` | `auth-gateway` |
| `rule_management` | `rule-management-service` |
| `airflow` | Airflow metadata (`compose.analytics.yml`, `009-airflow-schema.xml`) |

Миграции: единый runner `liquibase/changelog/db.changelog-master.xml`.

Cross-schema FK **запрещены by design** — связи только по UUID (`paymentId`, `sagaId`, `accountId`).

В demo все сервисы могут ходить под одним JDBC user; в production hardening ожидаются per-schema roles + `search_path`.

## Consequences

### Positive

- Один Compose Postgres, один Liquibase job, проще Debezium (connectors на схемы/таблицы одного хоста).
- Локальный reset = drop volume + re-run changelog.
- Схемы всё ещё отражают bounded contexts в коде и ADR/docs.
- Airflow metadata не требует отдельного Postgres в MVP analytics overlay.

### Negative / accepted limitations

- Нет жёсткой network isolation: ошибочный JDBC URL / `search_path` может читать чужую схему.
- Noisy neighbor: тяжёлый Airflow/analytics metadata делит I/O с OLTP (митигация: отдельный schema, не отдельный host — accepted for demo).
- Horizontal scaling write path и independent backup/PITR per service — отложены.
- Logical decoding slot — single point; падение Postgres роняет весь OLTP.

## Alternatives considered

1. **Отдельный Postgres на каждый сервис** — deferred на production hardening; слишком тяжёлый cold-start для Compose demo.
2. **Shared tables без схем** (`public` kitchen sink) — rejected: стирает границы контекстов, усложняет Debezium include lists.
3. **Postgres OLTP + отдельный Postgres только для Airflow** — reasonable later; сейчас schema `airflow` достаточен.
4. **Schema-per-service + Postgres RDS с IAM roles сразу** — out of scope для Compose MVP.

## Code pointers

| Area | Path |
|------|------|
| Master changelog | `liquibase/changelog/db.changelog-master.xml` |
| Core schemas | `001-payment-command.xml`, `002-account-query.xml`, `005-auth-schema.xml`, `006-saga-schema.xml` (incl. `participant_*`), `007-rule-management.xml`, `009-airflow-schema.xml` |
| Compose DB | `docker-compose.yml` (Postgres service), `compose.analytics.yml` (Airflow → same DB / schema `airflow`) |
| Debezium | `debezium/connectors/payment-event-store.json`, `payment-outbox.json`, `rule-management-outbox.json` |

## See also / Revisit

- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga state в schema `saga`.
- [ADR 0003](0003-event-sourcing-scope-ledger-only.md) — `event_store` в `payment_command`.
- Analytics overlay: `compose.analytics.yml`.

**Revisit triggers**

- Production SLO / compliance требует изоляции PII или отдельного backup cadence → split `payment_command` (+ ledger) first.
- Replication slot lag или WAL pressure от смешанной нагрузки Airflow + OLTP → вынести `airflow` в отдельный Postgres.
- Независимое шардирование account/payment → database-per-service или Citus; схемы остаются логической картой миграции.
