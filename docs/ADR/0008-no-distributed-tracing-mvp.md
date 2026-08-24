# ADR 0008 — No distributed tracing in MVP

- Status: Accepted
- Date: 2026-06-18
- Context owner: observability / platform
- Related ADRs: [0006](0006-analytics-split-superset-vs-react.md), [0005](0005-flink-vs-spark-streaming.md)

## Context

Полный distributed tracing (OpenTelemetry SDK → OTLP collector → Tempo/Jaeger) даёт trace `HTTP → command → Kafka → saga → participants → Flink`, но добавляет:

- collector / backend в Compose;
- context propagation через HTTP **и** Kafka headers **и** Flink operators;
- UI maintenance и sampling policy;
- шум в demo onboarding («ещё один URL»).

Observability MVP уже закрывает операционный контур без traces:

- Micrometer + `metrics-starter` business metrics (`paypulse_*`);
- Prometheus scrape + 6 Grafana dashboards;
- exporters (Kafka, Postgres, cAdvisor, CH, Flink reporter);
- structured logs;
- Ops `/health` и live SSE timelines по `paymentId` / `sagaId`.

Distributed tracing остаётся **optional** для MVP: acceptance — metrics, dashboards и alerts.

## Decision

**Не деплоим** distributed tracing stack в MVP:

- нет Tempo / Jaeger / OTel Collector сервисов в `compose.observability.yml` как обязательных;
- нет обязательного Micrometer Tracing bridge во всех модулях;
- нет требования W3C `traceparent` end-to-end.

Вместо traces используем:

| Signal | Where |
|--------|--------|
| RED / JVM / HTTP | Grafana `paypulse-jvm-http` |
| Kafka lag / health | `paypulse-kafka-health`, kafka-exporter |
| Saga outcomes | orchestrator metrics + `paypulse-saga-state` + Ops stuck page |
| Flink job | PrometheusReporter + `paypulse-flink-job` |
| OLTP / CH | `paypulse-pg-ch` |
| Business overview | `paypulse-business-overview` |
| Cross-service correlate | `paymentId`, `sagaId` in logs / Kafka keys / Ops StepTimeline |
| Failure drills | [`docs/chaos.md`](../chaos.md) |

Документация: [`observability/README.md`](../../observability/README.md).

## Consequences

### Positive

- Меньший Compose footprint и быстрее onboarding (корневой README § observability / URL table).
- Фокус MVP на metrics + dashboards + alerts, которые уже есть в коде.
- Нет ложного ощущения «traces везде», пока Kafka/Flink propagation не доведена.

### Negative / accepted limitations

- Cross-service latency debugging медленнее: склеивать по id в логах и UI, не один waterfall.
- Нет automatic critical path analysis при регрессии p99 gateway→participant.
- Flink internal operator time не связана с HTTP span (и не будет, пока нет tracing).

## Alternatives considered

1. **Full OTel + Tempo now** — deferred: стоимость > ценности для demo SLO; tracing optional в MVP.
2. **Zipkin only on HTTP services** (без Kafka/Flink) — rejected как half-measure: главный path — messaging; HTTP-only traces вводят в заблуждение.
3. **Commercial APM** — out of scope for open Compose portfolio.
4. **Log-only correlation IDs without metrics** — rejected: MVP явно требует Prometheus/Grafana.

## Code pointers

| Area | Path |
|------|------|
| Observability compose | `compose.observability.yml` |
| Observability docs | `observability/README.md` |
| Grafana dashboards | `grafana/provisioning/dashboards/json/paypulse-*.json` |
| Datasource | `grafana/provisioning/datasources/datasource.yml` |
| Health summary | `bff-ops/.../health/HealthSummaryController.kt`, `HealthSummaryService.kt` |
| Ops health page | `ops-dashboard-ui/src/pages/HealthPage.tsx` |
| Chaos / recovery | `docs/chaos.md` |

## See also / Revisit

- [ADR 0006](0006-analytics-split-superset-vs-react.md) — Ops + Grafana/Superset, не trace UI.
- [ADR 0002](0002-saga-orchestration-vs-choreography.md) — saga tables / events как functional timeline.

**Revisit triggers**

- Production p99 SLO требует waterfall across gateway → saga → participants.
- Первый инкремент: W3C `traceparent` в gateway filter + copy в Kafka headers; Flink / consumers — следующим шагом.
- Появление обязательного security/audit «show full request graph» для compliance demos.
- Compose уже тянет collector для других reasons — тогда дешевле включить tracing, чем поддерживать ADR «нет».
