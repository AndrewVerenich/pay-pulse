# ADR 0008 — Без distributed tracing в MVP

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: observability / platform
- Связанные ADR: [0006](0006-analytics-split-superset-vs-react.md), [0005](0005-flink-vs-spark-streaming.md)

## Контекст

Полный distributed tracing (OpenTelemetry SDK → OTLP collector → Tempo/Jaeger) даёт trace `HTTP → command → Kafka → saga → participants → Flink`, но добавляет:

- collector / backend в Compose;
- context propagation через HTTP **и** Kafka headers **и** Flink operators;
- поддержку UI и sampling policy;
- шум в demo onboarding («ещё один URL»).

Observability MVP уже закрывает операционный контур без traces:

- Micrometer + `metrics-starter` business metrics (`paypulse_*`);
- Prometheus scrape + 6 Grafana dashboards;
- exporters (Kafka, Postgres, cAdvisor, Flink reporter);
- structured logs;
- Ops `/health` и live SSE timelines по `paymentId` / `sagaId`.

Distributed tracing остаётся **опциональным** для MVP: acceptance — metrics, dashboards и alerts.

## Решение

**Не деплоим** distributed tracing stack в MVP:

- нет Tempo / Jaeger / OTel Collector как обязательных сервисов в `compose.observability.yml`;
- нет обязательного Micrometer Tracing bridge во всех модулях;
- нет требования W3C `traceparent` end-to-end.

Вместо traces используем:

| Сигнал | Где |
|--------|-----|
| RED / JVM / HTTP | Grafana `paypulse-jvm-http` |
| Kafka lag / health | `paypulse-kafka-health`, kafka-exporter |
| Saga outcomes | метрики orchestrator + `paypulse-saga-state` + Ops stuck page |
| Flink job | PrometheusReporter + `paypulse-flink-job` |
| OLTP / CH | `paypulse-pg-ch` |
| Business overview | `paypulse-business-overview` |
| Cross-service correlate | `paymentId`, `sagaId` в логах / Kafka keys / Ops StepTimeline |
| Failure drills | [`docs/chaos.md`](../chaos.md) |

Документация: [`observability/README.md`](../../observability/README.md).

## Последствия

### Плюсы

- Меньший Compose footprint и быстрее onboarding (корневой README § observability / URL).
- Фокус MVP на metrics + dashboards + alerts, которые уже есть в коде.
- Нет ложного ощущения «traces везде», пока Kafka/Flink propagation не доведена.

### Минусы / принятые ограничения

- Cross-service latency debugging медленнее: склеивать по id в логах и UI, не один waterfall.
- Нет automatic critical path analysis при регрессии p99 gateway→participant.
- Внутреннее время Flink operators не связано с HTTP span (и не будет, пока нет tracing).

## Альтернативы

1. **Full OTel + Tempo сейчас** — отложено: стоимость > ценности для demo SLO; tracing optional в MVP.
2. **Zipkin только на HTTP** (без Kafka/Flink) — отклонено как half-measure: главный path — messaging; HTTP-only traces вводят в заблуждение.
3. **Commercial APM** — вне scope open Compose portfolio.
4. **Только log correlation IDs без metrics** — отклонено: MVP явно требует Prometheus/Grafana.

## Указатели в коде

| Область | Путь |
|---------|------|
| Observability compose | `compose.observability.yml` |
| Observability docs | `observability/README.md` |
| Grafana dashboards | `grafana/provisioning/dashboards/json/paypulse-*.json` |
| Datasource | `grafana/provisioning/datasources/datasource.yml` |
| Health summary | `bff-ops/.../health/HealthSummaryController.kt`, `HealthSummaryService.kt` |
| Ops health page | `ops-dashboard-ui/src/pages/HealthPage.tsx` |
| Chaos / recovery | `docs/chaos.md` |

## См. также / когда пересмотреть

- [ADR 0006](0006-analytics-split-superset-vs-react.md) — Ops + Grafana/Superset, не trace UI.
- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga tables / events как functional timeline.

**Триггеры пересмотра**

- Production p99 SLO требует waterfall gateway → saga → participants.
- Первый инкремент: W3C `traceparent` в gateway filter + copy в Kafka headers; Flink / consumers — следующим шагом.
- Обязательный security/audit «show full request graph» для compliance demos.
- Compose уже тянет collector по другим причинам — тогда дешевле включить tracing, чем держать ADR «нет».
