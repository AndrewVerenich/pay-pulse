FROM apache/airflow:2.8.3-python3.11

USER airflow
RUN pip install --no-cache-dir "dbt-clickhouse>=1.8.0,<1.9" "clickhouse-connect>=0.7.0"
