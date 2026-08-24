"""Run dbt star + marts: staging → intermediate → dimensions → facts → marts."""
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator

DBT_DIR = "/opt/dbt"
DBT_ENV = (
    "export CLICKHOUSE_HOST=${CLICKHOUSE_HOST:-clickhouse} "
    "CLICKHOUSE_PORT=${CLICKHOUSE_PORT:-8123} "
    "DBT_PROFILES_DIR=/opt/dbt "
    "DBT_TARGET_PATH=/tmp/dbt-target "
    "DBT_LOG_PATH=/tmp/dbt-logs"
)
# dbt-clickhouse 1.8: --target-path is invalid; use DBT_TARGET_PATH env instead.
DBT_FLAGS = "--profiles-dir /opt/dbt --project-dir /opt/dbt --log-path /tmp/dbt-logs"


def dbt_cmd(select: str) -> str:
    return (
        f"{DBT_ENV} && mkdir -p /tmp/dbt-target /tmp/dbt-logs && cd {DBT_DIR} "
        f"&& dbt run --select {select} {DBT_FLAGS}"
    )


with DAG(
    dag_id="paypulse_dbt_dag",
    description="dbt star schema and marts for PayPulse",
    schedule="0 */6 * * *",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=["paypulse", "dbt"],
    default_args={"retries": 1, "retry_delay": timedelta(minutes=5)},
) as dag:
    # Prefer vendored dbt_packages/; fall back to dbt deps when git is available.
    dbt_deps = BashOperator(
        task_id="dbt_deps",
        bash_command=(
            f"{DBT_ENV} && mkdir -p /tmp/dbt-target /tmp/dbt-logs && cd {DBT_DIR} && "
            "if [ -d dbt_packages/dbt_utils ]; then "
            "echo 'dbt_packages already present'; "
            "elif command -v git >/dev/null 2>&1; then "
            f"dbt deps {DBT_FLAGS}; "
            "else "
            "echo 'ERROR: dbt_packages missing and git not installed' >&2; exit 1; "
            "fi"
        ),
    )
    run_staging = BashOperator(task_id="run_staging", bash_command=dbt_cmd("tag:staging"))
    run_intermediate = BashOperator(task_id="run_intermediate", bash_command=dbt_cmd("tag:intermediate"))
    run_dimensions = BashOperator(task_id="run_dimensions", bash_command=dbt_cmd("tag:dimensions"))
    run_facts = BashOperator(task_id="run_facts", bash_command=dbt_cmd("tag:facts"))
    run_marts = BashOperator(task_id="run_marts", bash_command=dbt_cmd("tag:marts"))

    dbt_deps >> run_staging >> run_intermediate >> run_dimensions >> run_facts >> run_marts
