# ADR 0006 — Analytics split: Superset/dbt vs React Ops Dashboard

- Status: Accepted
- Date: 2026-06-18
- Context owner: analytics + ops-ui teams
- Related ADRs: [0005](0005-flink-vs-spark-streaming.md), [0008](0008-no-distributed-tracing-mvp.md)

## Context

У PayPulse две аудитории с разными SLO:

1. **Operators / on-call** — live payments, fraud alerts, stuck sagas, rule ack «applied in Flink». Свежесть: секунды. UX: timelines, SSE, action buttons (force-complete, CRUD rules).
2. **Analysts / risk / AML** — daily risk report, structuring patterns, RFM, settlement latency, merchant risk, revenue. Свежесть: минуты–часы OK. UX: SQL Lab, drill-down, scheduled charts.

Строить всю аналитику в React (`ops-dashboard-ui`) = дублировать BI (filters, pivots, exports, access for non-engineers). Класть live ops в Superset = бороться с SSE/WebSocket и write-actions через BI.

Grafana закрывает **runtime / RED / infra metrics**, не business marts.

## Decision

Явный **трёхслойный split UI/analytics**:

| Concern | Stack | Freshness |
|---------|--------|-----------|
| Real-time ops | `bff-ops` (WebFlux + reactor-kafka + SSE) + `ops-dashboard-ui` (Vite/React) | seconds |
| Batch / mart analytics | Kafka → ClickHouse (`clickhouse/init`) → `analytics/dbt` (staging → intermediate → marts) → **Apache Superset** | minutes–hours |
| Pipeline orchestration | Airflow DAGs in `airflow/dags/` (`paypulse_dbt_dag`, `paypulse_dbt_test_dag`, `paypulse_data_quality_dag`), `compose.analytics.yml` | scheduled |
| Runtime observability | Prometheus + Grafana provisioning (`grafana/provisioning/...`) | scrape interval |

Marts (dbt): `mart_daily_risk_report`, `mart_aml_structuring_patterns`, `mart_customer_risk_rfm`, `mart_settlement_latency`, `mart_merchant_risk_profile`, `mart_daily_revenue`.

React **не** рендерит dbt marts; Superset **не** владеет saga force-complete / rule push.

## Consequences

### Positive

- Каждый UI оптимизирован под persona и SLO.
- dbt tests / Airflow DQ независимы от Ops UI регрессий.
- Flink alerts идут в Ops SSE path; hourly metrics и raw events — в CH для BI ([ADR 0005](0005-flink-vs-spark-streaming.md)).
- Grafana остаётся «как система себя чувствует»; Superset — «что происходит с бизнесом/риском».

### Negative / accepted limitations

- В демо три dashboard-продукта (React + Grafana + Superset) — onboarding через корневой README URL table и `/health` links.
- Дублирование «похожего» KPI возможно (fraud rate в Grafana vs mart) — разные источники; документировать, не сливать.
- Analysts без доступа к Ops UI не видят stuck saga actions (by design).

## Alternatives considered

1. **Всё в React** (charts на ClickHouse HTTP) — rejected: объём BI-фич и требование provisioned Superset dashboards.
2. **Всё в Superset** включая live — rejected: нет хорошего fit для SSE, JWT ops actions, StepTimeline.
3. **Metabase вместо Superset** — viable alternative; Superset выбран ближе к lakehouse reference.
4. **Grafana только + SQL datasource на CH** — rejected как единственный BI: слабее semantic layer / dbt mart governance story.

## Code pointers

| Area | Path |
|------|------|
| Ops UI | `ops-dashboard-ui/` (`LivePage`, `AlertsPage`, `StuckSagasPage`, `RulesPage`, `HealthPage`) |
| BFF SSE / agg | `bff-ops/` (`PaymentLiveController`, `AlertsLiveController`, `SagaLiveController`, …) |
| Analytics | `analytics/` (`dbt/`, `superset/`) |
| Airflow | `airflow/dags/paypulse_*.py`, `airflow/Dockerfile` |
| ClickHouse pipeline | `clickhouse/init/002-analytics-pipeline.sql`, `compose.analytics.yml` |
| Grafana | `grafana/provisioning/dashboards/json/paypulse-*.json` |
| Health links | `bff-ops/.../HealthSummaryController.kt`, `ops-dashboard-ui/.../HealthPage.tsx` |

## See also / Revisit

- [ADR 0005](0005-flink-vs-spark-streaming.md) — stream path feeds both Ops and CH.
- [ADR 0008](0008-no-distributed-tracing-mvp.md) — нет Tempo UI; Ops + Grafana заменяют trace drill-down в MVP.

**Revisit triggers**

- Единый portal requirement (одна SPA для ops+BI) → embed Superset или Cube/metrics layer в React.
- Real-time BI (sub-second dashboards) → CH Materialized Views + lightweight React charts, всё ещё не заменяя Ops SSE.
- Headcount без Superset expertise → Metabase / Evidence / Streamlit evaluation.
