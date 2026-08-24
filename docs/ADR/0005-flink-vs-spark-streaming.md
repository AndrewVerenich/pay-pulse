# ADR 0005 — Apache Flink vs Spark Structured Streaming для fraud detection

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: flink-payment-intelligence / streaming
- Связанные ADR: [0006](0006-analytics-split-superset-vs-react.md)

## Контекст

Real-time fraud / AML на PayPulse требует:

- **Sub-minute** (лучше seconds-level) latency на keyed state: velocity windows, geo anomaly;
- **Broadcast state** для динамических правил из compact-топика `fraud_rules` без рестарта job;
- **Broadcast** baseline `user_risk_profiles`;
- Event-time окна для hourly metrics (`payment_metrics_hourly`);
- Side outputs: `fraud_alerts`, `user_fraud_scores`, `dead_letter`.

Spark Structured Streaming (micro-batch) умеет streaming joins и watermarking, но:

- broadcast / map-side refresh правил менее идиоматичен, чем Flink `BroadcastProcessFunction`;
- checkpoint / exactly-once и low-latency keyed timers — сильнее сторона Flink;
- референс-портфолио уже на Flink (`data-pipelines/clickstream-analytics-pipeline`).

Batch Spark / nightly jobs остаются в зоне dbt/Airflow на ClickHouse ([ADR 0006](0006-analytics-split-superset-vs-react.md)), не на path алертов.

## Решение

Используем **Apache Flink** (модуль `flink-payment-intelligence`, Kotlin, **JVM 11**) как единственный stream processor fraud path:

- Entry: `PaymentIntelligenceJob` — sources `payment.events`, `fraud_rules`, `user_risk_profiles`.
- `EventParser` + dead-letter side output (`DeadLetterTag`) вместо fail-job на bad JSON.
- `UserRiskEnricher` — broadcast connect profiles.
- Keyed by `accountId` → `FraudDetectionFunction` + broadcast rules; helpers `VelocityChecker`, `GeoAnomalyDetector`, AML `StructuringDetector`, `FraudScorer`.
- Sinks: `fraud_alerts`, compact `user_fraud_scores`, `payment_metrics_hourly`, `dead_letter` (`KafkaSinks` / env через `FlinkJobProperties`).
- Ops: checkpointing + Flink `PrometheusReporter` (`:9249`), дашборды Grafana; chaos — `docs/chaos.md`.

**Spark Structured Streaming / Spark Streaming — вне scope MVP.**

Job: `flink-payment-intelligence` · UI `:8081` · metrics `:9249`. Запуск: [`flink-payment-intelligence/README.md`](../../flink-payment-intelligence/README.md).

### Граф операторов

```mermaid
flowchart TB
  subgraph sources["Sources (Kafka)"]
    PE[payment.events]
    FR[fraud_rules<br/>compact · broadcast]
    UR[user_risk_profiles<br/>compact · broadcast]
  end

  PE --> parse["EventParser<br/>uid: event-parser"]
  parse -->|ok PaymentEvent| enrich["UserRiskEnricher<br/>uid: user-risk-enricher"]
  parse -->|malformed| DL[dead_letter]
  UR -.->|broadcast| enrich

  enrich -->|EnrichedPayment| key["keyBy accountId"]
  FR -.->|broadcast| detect["FraudDetectionFunction<br/>uid: fraud-scorer"]
  key --> detect

  detect --> FA[fraud_alerts]
  detect -->|side| UFS[user_fraud_scores]

  parse -->|PaymentEvent| metrics["HourlyMetricsFunction<br/>uid: hourly-metrics<br/>event-time 1h"]
  metrics --> PMH[payment_metrics_hourly]
```

### Логика детекции (внутри FraudDetectionFunction)

| Checker | Идея |
|---------|------|
| `VelocityChecker` | N платежей / окно на account |
| `GeoAnomalyDetector` | резкая смена geo / mismatch risk tier |
| `StructuringDetector` | много сумм ниже порога (AML) |
| `FraudScorer` | объединяет rule JSON + enrich scores |

Hot-reload правил: update `rule_management.fraud_rule` → outbox CDC → `fraud_rules` → broadcast state **без** рестарта job.

### Операторы

| UID | Тип | Вход | Выход |
|-----|-----|------|-------|
| `event-parser` | FlatMap + side output | raw JSON | `PaymentEvent` / DLT |
| `user-risk-enricher` | Broadcast connect | event + profile | `EnrichedPayment` |
| `fraud-scorer` | Keyed broadcast process | enriched + rule | alerts, scores |
| `hourly-metrics` | Event-time window 1h | `PaymentEvent` | hourly metrics |

### State и checkpoints

- Backend: `HashMapStateBackend`
- Storage: `FileSystemCheckpointStorage` (`/checkpoints`, volume shared JM/TM)
- После `docker kill` TaskManager job восстанавливается из checkpoint ([docs/chaos.md](../chaos.md) §4)
- Стабильные `uid` / `name` обязательны; смена uid = потеря state

## Последствия

### Плюсы

- Native broadcast state: rule CRUD (`rule-management-service`) → CDC → Kafka → Flink без redeploy job.
- Keyed process + timers хорошо выражают velocity / session-like checks.
- Side outputs дают чистый контракт для BFF SSE (`fraud_alerts`) и DLT.
- Prometheus-метрики job стыкуются с observability overlay.
- Согласовано с clickstream-референсом — меньше «двух streaming-стеков» в портфолио.

### Минусы / принятые ограничения

- Отдельный **JVM 11** toolchain vs JDK 21 Spring-сервисов.
- Flink cluster ops (JM/TM, checkpoints) — отдельная поверхность отказа; см. chaos drills.
- Нет unified SQL UI «как Spark»; ad-hoc analytics — ClickHouse/Superset, не Flink SQL.
- Локально TaskManagers тяжелее лёгкого Kafka Streams (частично закрыто `kstreams-saga-events-agg` только для saga metrics).

## Альтернативы

1. **Spark Structured Streaming** — отклонено для fraud alerts: micro-batch latency + слабее story hot-reload правил.
2. **Только Kafka Streams** — рассмотрено для простых aggregations; отклонено как primary fraud engine (нет первоклассного broadcast state / parity с reference AML graph). Точечно: `kstreams-saga-events-agg`.
3. **Правила только в participant-fraud-check (sync Redis score)** — недостаточно: нужен continuous scoring + alerts stream; participant читает last score, Flink его производит.
4. **Только Flink SQL / Table API** — отклонено: custom AML structuring и scoring удобнее DataStream + Kotlin.

## Указатели в коде

| Область | Путь |
|---------|------|
| Job entry / graph | `flink-payment-intelligence/src/main/kotlin/.../PaymentIntelligenceJob.kt` |
| Fraud core | `.../scoring/FraudDetectionFunction.kt`, `FraudScorer.kt` |
| Rules / AML | `.../rules/VelocityChecker.kt`, `GeoAnomalyDetector.kt`, `.../aml/StructuringDetector.kt` |
| Enrichment | `.../enrich/UserRiskEnricher.kt` |
| Parse / DLT | `.../io/EventParser.kt`, `DeadLetterTag.kt` |
| Metrics window | `.../metrics/HourlyMetricsFunction.kt` |
| Config / topics | `.../config/FlinkJobProperties.kt` |
| Dynamic rules | `rule-management-service/`, Debezium `rule-management-outbox.json` |
| README модуля | `flink-payment-intelligence/README.md` |

## См. также / когда пересмотреть

- [ADR 0006](0006-analytics-split-superset-vs-react.md) — Flink → Kafka → ClickHouse; BI = Superset, не Flink UI.
- [ADR 0008](0008-no-distributed-tracing-mvp.md) — нет OTel traces через Flink operators в MVP.
- Chaos: [`docs/chaos.md`](../chaos.md).

**Триггеры пересмотра**

- Команда стандартизирована на Spark и не хочет второй runtime → переоценка (с потерей broadcast DX).
- Нужен unified batch+streaming lakehouse на Spark 3.x — Flink может остаться только для alerts.
- Flink SQL + catalogs закрывают 80% кастомных функций — возможен partial move.
