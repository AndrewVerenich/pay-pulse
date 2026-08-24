"""Run dbt tests after mart builds."""
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator

DBT_DIR = "/opt/dbt"
DBT_ENV = (
    "export CLICKHOUSE_HOST=${CLICKHOUSE_HOST:-clickhouse} "
    "CLICKHOUSE_PORT=${CLICKHOUSE_PORT:-8123} "
    "DBT_PROFILES_DIR=/opt/dbt"
)
DBT_FLAGS = "--profiles-dir /opt/dbt --project-dir /opt/dbt --target-path /tmp/dbt-target --log-path /tmp/dbt-logs"

with DAG(
    dag_id="paypulse_dbt_test_dag",
    description="dbt test for PayPulse analytics",
    schedule="30 */6 * * *",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["paypulse", "dbt", "test"],
    default_args={"retries": 1, "retry_delay": timedelta(minutes=5)},
) as dag:
    BashOperator(
        task_id="dbt_test",
        bash_command=f"{DBT_ENV} && mkdir -p /tmp/dbt-target /tmp/dbt-logs && cd {DBT_DIR} && dbt test {DBT_FLAGS}",
    )
