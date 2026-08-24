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
echo "Superset bootstrap complete."
