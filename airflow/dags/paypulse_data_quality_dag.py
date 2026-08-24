"""Data freshness checks against ClickHouse raw tables."""
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator

# Avoid nested quotes in bash: throwIf(x) without custom message.
FRESHNESS_SQL = (
    "SELECT throwIf("
    "count() = 0 OR max(toDate(occurred_at)) < today() - 1"
    ") FROM paypulse_analytics.payment_events_raw FORMAT Null"
)

with DAG(
    dag_id="paypulse_data_quality_dag",
    description="ClickHouse freshness checks for PayPulse analytics",
    schedule="0 7 * * *",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["paypulse", "quality"],
    default_args={"retries": 1, "retry_delay": timedelta(minutes=10)},
) as dag:
    BashOperator(
        task_id="check_payment_events_freshness",
        bash_command=(
            'curl -sf "http://${CLICKHOUSE_HOST:-clickhouse}:${CLICKHOUSE_PORT:-8123}/" '
            f"--data-binary \"{FRESHNESS_SQL}\""
        ),
    )
