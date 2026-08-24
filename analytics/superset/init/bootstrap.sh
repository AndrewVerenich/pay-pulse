#!/bin/bash
set -euo pipefail

export FLASK_APP=superset
superset db upgrade
superset fab create-admin \
  --username admin \
  --firstname PayPulse \
  --lastname Admin \
  --email admin@paypulse.local \
  --password admin || true
superset init

python3 /app/init/register_clickhouse.py
python3 /app/init/provision_dashboard.py || echo "WARN: dashboard provision skipped (dbt marts missing?)"
echo "Superset bootstrap complete."
