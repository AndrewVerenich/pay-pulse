# ADR 0004 — Один PostgreSQL со схемами vs database-per-service

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: platform / Liquibase / Debezium
- Связанные ADR: [0002](0002-saga-orchestration-vs-choreography.md), [0003](0003-event-sourcing-scope-ledger-only.md)

## Контекст

Классический совет по микросервисам — **database-per-service**: изоляция blast radius, независимое масштабирование и ownership миграций.

Для PayPulse первичный target — **portfolio / interview demo** на Docker Compose:

- один `docker compose up` поднимает OLTP + Kafka + analytics overlays;
- один Liquibase init-container применяет весь changelog;
- Debezium connectors читают один Postgres (logical decoding / replication slot).

Настоящий DB-per-service на ноутбуке умножает контейнеры, слоты, credentials и время cold-start без выигрыша для демо-SLO.

## Решение

Один экземпляр PostgreSQL (`paypulse`) с **schema-per-service** (логически отдельные bounded contexts, физически одна БД):

| Schema | Владелец / потребители |
|--------|------------------------|
| `payment_command` | `payment-command-service`, `participant-ledger-apply` (writes в `event_store`) |
| `account_query` | `projection-balance`, account-query reads |
| `saga` | `payment-saga-orchestrator` |
| `participant_fraud` | `participant-fraud-check` |
| `participant_risk` | `participant-risk-scoring` |
| `participant_ledger` | `participant-ledger-apply` (`processed_commands`) |
| `participant_notification` | `participant-notification` |
| `auth` | `auth-gateway` |
| `rule_management` | `rule-management-service` |
| `airflow` | метаданные Airflow (`compose.analytics.yml`, `009-airflow-schema.xml`) |

Миграции: единый runner `liquibase/changelog/db.changelog-master.xml`.

Cross-schema FK **запрещены by design** — связи только по UUID (`paymentId`, `sagaId`, `accountId`).

В демо сервисы могут ходить под одним JDBC user; в production hardening ожидаются per-schema roles + `search_path`.

## Последствия

### Плюсы

- Один Compose Postgres, один Liquibase job, проще Debezium (connectors на схемы/таблицы одного хоста).
- Локальный reset = drop volume + re-run changelog.
- Схемы отражают bounded contexts в коде и документации.
- Метаданным Airflow не нужен отдельный Postgres в MVP analytics overlay.

### Минусы / принятые ограничения

- Нет жёсткой network isolation: ошибочный JDBC URL / `search_path` может читать чужую схему.
- Noisy neighbor: тяжёлый Airflow делит I/O с OLTP (митигация: отдельный schema, не отдельный host — accepted for demo).
- Horizontal scaling write path и независимый backup/PITR per service — отложены.
- Logical decoding slot — single point; падение Postgres роняет весь OLTP.

## Альтернативы

1. **Отдельный Postgres на каждый сервис** — отложено на production hardening; слишком тяжёлый cold-start для Compose-демо.
2. **Общие таблицы без схем** (`public` kitchen sink) — отклонено: стирает границы контекстов, усложняет Debezium include lists.
3. **Postgres OLTP + отдельный Postgres только для Airflow** — разумно позже; сейчас schema `airflow` достаточен.
4. **Schema-per-service + RDS с IAM сразу** — вне scope Compose MVP.

## Указатели в коде

| Область | Путь |
|---------|------|
| Master changelog | `liquibase/changelog/db.changelog-master.xml` |
| Core schemas | `001-payment-command.xml`, `002-account-query.xml`, `005-auth-schema.xml`, `006-saga-schema.xml`, `007-rule-management.xml`, `009-airflow-schema.xml` |
| Compose DB | `docker-compose.yml` (Postgres), `compose.analytics.yml` (Airflow → та же БД / schema `airflow`) |
| Debezium | `debezium/connectors/payment-event-store.json`, `payment-outbox.json`, `rule-management-outbox.json` |

## См. также / когда пересмотреть

- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga state в schema `saga`.
- [ADR 0003](0003-event-sourcing-scope-ledger-only.md) — `event_store` в `payment_command`.
- Analytics overlay: `compose.analytics.yml`.

**Триггеры пересмотра**

- Production SLO / compliance требует изоляции PII или отдельного backup cadence → сначала split `payment_command` (+ ledger).
- Replication slot lag или WAL pressure от смешанной нагрузки Airflow + OLTP → вынести `airflow` в отдельный Postgres.
- Независимое шардирование account/payment → database-per-service или Citus; схемы остаются логической картой миграции.
