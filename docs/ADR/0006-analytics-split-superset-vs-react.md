# ADR 0006 — Разделение аналитики: Superset/dbt vs React Ops Dashboard

- Статус: Принят
- Дата: 2026-06-18
- Владелец контекста: analytics + ops-ui
- Связанные ADR: [0005](0005-flink-vs-spark-streaming.md), [0008](0008-no-distributed-tracing-mvp.md)

## Контекст

У PayPulse две аудитории с разными SLO:

1. **Operators / on-call** — live payments, fraud alerts, stuck sagas, rule ack «applied in Flink». Свежесть: секунды. UX: timelines, SSE, action buttons (force-complete, CRUD rules).
2. **Analysts / risk / AML** — daily risk, structuring, RFM, settlement latency, merchant risk, revenue. Свежесть: минуты–часы OK. UX: SQL Lab, drill-down, scheduled charts.

Строить всю аналитику в React (`ops-dashboard-ui`) = дублировать BI (filters, pivots, exports, доступ для non-engineers). Класть live ops в Superset = бороться с SSE/WebSocket и write-actions через BI.

Grafana закрывает **runtime / RED / infra metrics**, не business marts.

## Решение

Явный **трёхслойный split** UI/analytics:

| Concern | Стек | Свежесть |
|---------|------|----------|
| Real-time ops | `bff-ops` (WebFlux + reactor-kafka + SSE) + `ops-dashboard-ui` (Vite/React) | секунды |
| Batch / mart analytics | Kafka → ClickHouse (`clickhouse/init`) → `analytics/dbt` → **Apache Superset** | минуты–часы |
| Оркестрация пайплайна | Airflow DAGs в `airflow/dags/`, `compose.analytics.yml` | по расписанию |
| Runtime observability | Prometheus + Grafana (`grafana/provisioning/...`) | scrape interval |

Marts (dbt): `mart_daily_risk_report`, `mart_aml_structuring_patterns`, `mart_customer_risk_rfm`, `mart_settlement_latency`, `mart_merchant_risk_profile`, `mart_daily_revenue`.

React **не** рендерит dbt marts; Superset **не** владеет saga force-complete / rule push.

## Последствия

### Плюсы

- Каждый UI оптимизирован под persona и SLO.
- dbt tests / Airflow DQ независимы от регрессий Ops UI.
- Flink alerts идут в Ops SSE; hourly metrics и raw events — в CH для BI ([ADR 0005](0005-flink-vs-spark-streaming.md)).
- Grafana — «как система себя чувствует»; Superset — «что с бизнесом/риском».

### Минусы / принятые ограничения

- В демо три dashboard-продукта (React + Grafana + Superset) — onboarding через корневой README и `/health`.
- Возможно дублирование «похожего» KPI (fraud rate в Grafana vs mart) — разные источники; документировать, не сливать.
- Analysts без Ops UI не видят stuck saga actions (by design).

## Альтернативы

1. **Всё в React** (charts на ClickHouse HTTP) — отклонено: объём BI-фич и требование provisioned Superset.
2. **Всё в Superset**, включая live — отклонено: нет хорошего fit для SSE, JWT ops actions, StepTimeline.
3. **Metabase вместо Superset** — viable alternative; Superset ближе к lakehouse-референсу.
4. **Только Grafana + SQL datasource на CH** — отклонено как единственный BI: слабее semantic layer / dbt mart governance.

## Указатели в коде

| Область | Путь |
|---------|------|
| Ops UI | `ops-dashboard-ui/` (`LivePage`, `AlertsPage`, `StuckSagasPage`, `RulesPage`, `HealthPage`) |
| BFF SSE / agg | `bff-ops/` |
| Analytics | `analytics/` (`dbt/`, `superset/`) |
| Airflow | `airflow/dags/paypulse_*.py`, `airflow/Dockerfile` |
| ClickHouse pipeline | `clickhouse/init/002-analytics-pipeline.sql`, `compose.analytics.yml` |
| Grafana | `grafana/provisioning/dashboards/json/paypulse-*.json` |
| Health links | `bff-ops/.../HealthSummaryController.kt`, `ops-dashboard-ui/.../HealthPage.tsx` |

## См. также / когда пересмотреть

- [ADR 0005](0005-flink-vs-spark-streaming.md) — stream path кормит и Ops, и CH.
- [ADR 0008](0008-no-distributed-tracing-mvp.md) — нет Tempo UI; Ops + Grafana заменяют trace drill-down в MVP.

**Триггеры пересмотра**

- Единый portal (одна SPA для ops+BI) → embed Superset или metrics layer в React.
- Real-time BI (sub-second dashboards) → CH Materialized Views + лёгкие React charts, всё ещё не заменяя Ops SSE.
- Нет экспертизы Superset → Metabase / Evidence / Streamlit.
