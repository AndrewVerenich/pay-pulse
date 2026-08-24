#!/usr/bin/env python3
"""Register ClickHouse datasource in Superset (idempotent best-effort for dev)."""
import os

from superset.app import create_app

app = create_app()

CLICKHOUSE_HOST = os.environ.get("CLICKHOUSE_HOST", "clickhouse")
CLICKHOUSE_PORT = os.environ.get("CLICKHOUSE_PORT", "8123")
SQLALCHEMY_URI = f"clickhousedb://default:@{CLICKHOUSE_HOST}:{CLICKHOUSE_PORT}/paypulse_analytics"

with app.app_context():
    from superset import db
    from superset.models.core import Database

    existing = db.session.query(Database).filter_by(database_name="PayPulse ClickHouse").one_or_none()
    if existing is None:
        database = Database(
            database_name="PayPulse ClickHouse",
            sqlalchemy_uri=SQLALCHEMY_URI,
            expose_in_sqllab=True,
            allow_run_async=True,
        )
        db.session.add(database)
        db.session.commit()
        print("Registered PayPulse ClickHouse database")
    else:
        print("PayPulse ClickHouse database already exists")
