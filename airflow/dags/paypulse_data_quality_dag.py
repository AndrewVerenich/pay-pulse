"""Data freshness checks against ClickHouse raw tables."""
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator

CH_HOST = "${CLICKHOUSE_HOST:-clickhouse}"
CH_PORT = "${CLICKHOUSE_PORT:-8123}"

FRESHNESS_SQL = (
    "SELECT throwIf(max(toDate(occurred_at)) < today() - 1, 'payment_events_raw is stale') "
    "FROM paypulse_analytics.payment_events_raw FORMAT Null"
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
            f'curl -sf "http://{CH_HOST}:{CH_PORT}/" --data-binary \'{FRESHNESS_SQL}\''
        ),
    )
