# PayPulse chaos cookbook

Пять сценариев для демо отказоустойчивости. Перед началом поднимите полный стенд:

```bash
docker compose -f docker-compose.yml -f compose.stream.yml -f compose.observability.yml up -d
```

---

## 1. Kill Kafka broker

**Команда**

```bash
docker kill paypulse-kafka
docker compose up -d kafka
```

**Ожидаемые симптомы**

- Grafana → *PayPulse Kafka Health*: рост `kafka_consumergroup_lag`, возможны DOWN targets у consumer-сервисов.
- Kafka UI (`http://localhost:18088`): брокер offline, ISR уменьшается.
- Ops UI `/live`: задержка новых платежей; саги могут зависнуть на шагах с Kafka.

**Восстановление**

```bash
docker compose up -d kafka kafka-init
```

Дождитесь `healthy` у Kafka, затем перезапустите зависимые сервисы при необходимости:

```bash
docker compose restart payment-saga-orchestrator projection-balance bff-ops
```

**Критерий успеха**

- Consumer lag снижается до baseline (< 100 сообщений).
- Новый платёж проходит сагу до `COMPLETED`.

---

## 2. Kill participant-ledger-apply

**Команда**

```bash
docker kill paypulse-participant-ledger
```

**Ожидаемые симптомы**

- Grafana → *PayPulse Saga State*: рост активных саг, возможны `compensated` / `failed` outcomes.
- Ops UI `/sagas/stuck`: появляются застрявшие саги на шаге ledger.
- `saga.step.retries.total` растёт в Prometheus.

**Восстановление**

```bash
docker compose up -d participant-ledger-apply
```

Для застрявших саг используйте Ops UI `/sagas/stuck` → **Retry** или **Force complete**.

**Критерий успеха**

- Новые платежи снова завершают ledger step.
- Застрявшие саги либо завершены админ-действием, либо дошли до терминального статуса.

---

## 3. Kill projection-balance

**Команда**

```bash
docker kill paypulse-projection-balance
```

**Ожидаемые симптомы**

- Grafana: `paypulse_balance_projection_lag_seconds` растёт.
- Kafka UI: lag у consumer group `projection-balance`.
- `GET /api/v1/accounts/{id}/balance` может отставать от фактических payment events.

**Восстановление**

```bash
docker compose up -d projection-balance
```

**Критерий успеха**

- Lag догоняется (идемпотентность по `source_event_id`).
- Баланс после серии платежей совпадает с суммой deltas (без дублей).

---

## 4. Kill Flink taskmanager

**Команда** (требуется `compose.stream.yml`)

```bash
docker kill paypulse-flink-tm
docker compose -f docker-compose.yml -f compose.stream.yml up -d flink-taskmanager
```

**Ожидаемые симптомы**

- Flink UI (`http://localhost:8081`): job restart, checkpoint recovery.
- Grafana → *PayPulse Flink Job*: возможен всплеск `numFailedCheckpoints`.
- `/alerts`: до одного дублированного fraud alert допустим до dedup в ClickHouse.

**Восстановление**

```bash
docker compose -f docker-compose.yml -f compose.stream.yml up -d flink-taskmanager flink-submit
```

**Критерий успеха**

- Job в состоянии `RUNNING`.
- Fraud alerts снова поступают в `fraud_alerts` и Ops UI `/alerts`.

---

## 5. Kill ClickHouse

**Команда**

```bash
docker kill paypulse-clickhouse
docker compose up -d clickhouse
```

**Ожидаемые симптомы**

- Kafka: очередь для CH Kafka engine / ingest растёт (если analytics overlay поднят).
- Superset / Airflow DAGs: ошибки подключения к ClickHouse.
- Платёжный контур (gateway, saga) продолжает работать — analytics отстаёт.

**Восстановление**

```bash
docker compose up -d clickhouse
# при analytics overlay:
docker compose -f docker-compose.yml -f compose.analytics.yml restart airflow-scheduler superset
```

**Критерий успеха**

- ClickHouse принимает HTTP на `http://localhost:8124`.
- MV/ingest догоняют backlog; dbt/Superset снова отвечают.
