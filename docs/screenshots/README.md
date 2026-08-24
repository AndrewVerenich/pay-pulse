# PayPulse demo screenshots

Capture these after the full demo stack is running (UI, Grafana, Flink, Superset).

**Minimum resolution:** 1280×720 PNG.

| File | Source | Notes |
|------|--------|-------|
| `react-live.png` | http://localhost:3000/live | Live payments table with SSE updates |
| `react-alerts.png` | http://localhost:3000/alerts | Fraud alerts with filters |
| `react-payment-timeline.png` | http://localhost:3000/payments/{id} | Saga step timeline for a payment |
| `grafana-business-overview.png` | http://localhost:3001 | Dashboard **PayPulse Business Overview** |
| `flink-job-graph.png` | http://localhost:8081 | Flink job graph / overview |
| `superset-daily-risk.png` | http://localhost:18089/superset/dashboard/paypulse-analytics/ | Dashboard **PayPulse Analytics** |

## Capture checklist

1. Start stack: `docker compose -f docker-compose.yml -f compose.stream.yml -f compose.observability.yml -f compose.analytics.yml up -d`
2. Run generator scenarios: `docs/demo/fraud-burst.http`
3. Wait for data to flow (~2–5 minutes)
4. Screenshot each URL above
5. Save PNG files in this directory with exact names from the table

PNG files are **optional in git** (binary assets). This README is the contract for filenames and content.
