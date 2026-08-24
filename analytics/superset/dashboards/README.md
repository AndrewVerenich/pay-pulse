# Superset dashboards

Часть аналитического контура PayPulse. Полное описание пайплайна, звезды и витрин: [`analytics/README.md`](../../README.md).

После `dbt run` — UI http://localhost:18089 (`admin`/`admin`), datasource **PayPulse ClickHouse**.

| Dashboard | Dataset | Suggested chart |
|-----------|---------|-----------------|
| Daily Risk | `mart_daily_risk_report` | Line: `report_date` × `alert_count` |
| AML Structuring | `mart_aml_structuring_patterns` | Table: top accounts by `daily_total` |
| Customer RFM | `mart_customer_risk_rfm` | Bar: `risk_segment` |
| Settlement Latency | `mart_settlement_latency` | Histogram: `latency_seconds` |
| Merchant Risk | `mart_merchant_risk_profile` | Bar: `alert_count` |
| Daily Revenue | `mart_daily_revenue` | Line: `total_revenue` by `currency` |

> На snap-docker publish порта иногда висит при healthy-контейнере. Обход: сеть Docker (`http://superset:8088`) или `docker exec`.
