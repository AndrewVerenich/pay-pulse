#!/usr/bin/env python3
"""Create PayPulse datasets, charts, and dashboard (idempotent)."""
from __future__ import annotations

import json

from superset.app import create_app

DASHBOARD_TITLE = "PayPulse Analytics"
DASHBOARD_SLUG = "paypulse-analytics"
SCHEMA = "paypulse_analytics"

MARTS = [
    "mart_daily_risk_report",
    "mart_daily_revenue",
    "mart_aml_structuring_patterns",
    "mart_customer_risk_rfm",
    "mart_merchant_risk_profile",
    "mart_settlement_latency",
]

METRICS = {
    "mart_daily_risk_report": [("sum_alert_count", "SUM(alert_count)")],
    "mart_daily_revenue": [("sum_total_revenue", "SUM(total_revenue)")],
    "mart_aml_structuring_patterns": [("sum_daily_total", "SUM(daily_total)")],
    "mart_merchant_risk_profile": [("sum_alert_count", "SUM(alert_count)")],
    "mart_settlement_latency": [("avg_latency", "AVG(latency_seconds)")],
}


def timeseries_params(ds_id, viz, x, metric, grain, series=None, categorical=False):
    form = {
        "adhoc_filters": [],
        "datasource": f"{ds_id}__table",
        "viz_type": viz,
        "x_axis": x,
        "metrics": [metric],
        "groupby": [series] if series else [],
        "row_limit": 10000,
        "color_scheme": "supersetColors",
        "show_legend": True,
        "rich_tooltip": True,
        "truncateXAxis": not categorical,
        "markerEnabled": True,
        "markerSize": 8,
        "showValue": True,
    }
    if grain:
        form["time_grain_sqla"] = grain
    if categorical:
        form["xAxisForceCategorical"] = True
        form["truncateXAxis"] = False
    return form


def timeseries_query_context(ds_id, form, x, metric, grain, series=None):
    axis = {
        "columnType": "BASE_AXIS",
        "sqlExpression": x,
        "label": x,
        "expressionType": "SQL",
    }
    if grain:
        axis["timeGrain"] = grain
    columns = [axis] + ([series] if series else [])
    return {
        "datasource": {"id": ds_id, "type": "table"},
        "force": False,
        "queries": [
            {
                "filters": [],
                "extras": {"time_grain_sqla": grain, "having": "", "where": ""},
                "columns": columns,
                "metrics": [metric],
                "orderby": [],
                "series_columns": [series] if series else [],
                "row_limit": 10000,
                "order_desc": False,
                "post_processing": [
                    {
                        "operation": "pivot",
                        "options": {
                            "index": [x],
                            "columns": [series] if series else [],
                            "aggregates": {metric: {"operator": "mean"}},
                            "drop_missing_columns": True,
                        },
                    },
                    {"operation": "flatten"},
                ],
            }
        ],
        "form_data": {**form, "force": False, "result_format": "json", "result_type": "full"},
        "result_format": "json",
        "result_type": "full",
    }


def dist_bar_form(ds_id, groupby, metric, series=None):
    return {
        "adhoc_filters": [],
        "datasource": f"{ds_id}__table",
        "viz_type": "dist_bar",
        "metrics": [metric],
        "groupby": [groupby],
        "columns": [series] if series else [],
        "row_limit": 10000,
        "color_scheme": "supersetColors",
        "show_legend": True,
        "show_bar_value": True,
        "bar_stacked": False,
        "y_axis_format": ",.0f",
        "order_desc": True,
    }


def dist_bar_query_context(ds_id, form, groupby, metric, series=None):
    columns = [groupby] + ([series] if series else [])
    query = {
        "filters": [],
        "extras": {"having": "", "where": ""},
        "columns": columns,
        "metrics": [metric],
        "orderby": [],
        "row_limit": 10000,
        "order_desc": False,
    }
    if series:
        query["series_columns"] = [series]
        query["post_processing"] = [
            {
                "operation": "pivot",
                "options": {
                    "index": [groupby],
                    "columns": [series],
                    "aggregates": {metric: {"operator": "mean"}},
                    "drop_missing_columns": True,
                },
            },
            {"operation": "flatten"},
        ]
    return {
        "datasource": {"id": ds_id, "type": "table"},
        "force": False,
        "queries": [query],
        "form_data": {**form, "force": False, "result_format": "json", "result_type": "full"},
        "result_format": "json",
        "result_type": "full",
    }


def table_form(ds_id, columns, order_col):
    return {
        "adhoc_filters": [],
        "datasource": f"{ds_id}__table",
        "viz_type": "table",
        "query_mode": "raw",
        "all_columns": columns,
        "percent_metrics": [],
        "metrics": [],
        "groupby": [],
        "order_by_cols": [json.dumps([order_col, False])],
        "row_limit": 50,
        "table_timestamp_format": "smart_date",
        "show_cell_bars": True,
        "include_search": True,
        "align_pn": False,
        "color_pn": True,
    }


def table_query_context(ds_id, form, columns, order_col):
    return {
        "datasource": {"id": ds_id, "type": "table"},
        "force": False,
        "queries": [
            {
                "filters": [],
                "extras": {"having": "", "where": ""},
                "columns": columns,
                "metrics": [],
                "orderby": [[order_col, False]],
                "row_limit": 50,
            }
        ],
        "form_data": {**form, "force": False, "result_format": "json", "result_type": "full"},
        "result_format": "json",
        "result_type": "full",
    }


def histogram_form(ds_id, column):
    return {
        "adhoc_filters": [],
        "datasource": f"{ds_id}__table",
        "viz_type": "histogram",
        "all_columns_x": [column],
        "groupby": [],
        "row_limit": 10000,
        "link_length": "25",
        "x_axis_label": column,
        "y_axis_label": "count",
        "global_opacity": 1,
        "normalized": False,
    }


def histogram_query_context(ds_id, form, column):
    return {
        "datasource": {"id": ds_id, "type": "table"},
        "force": False,
        "queries": [
            {
                "filters": [],
                "extras": {"having": "", "where": ""},
                "columns": [column],
                "metrics": [],
                "row_limit": 10000,
            }
        ],
        "form_data": {**form, "force": False, "result_format": "json", "result_type": "full"},
        "result_format": "json",
        "result_type": "full",
    }


def ensure_dataset(db, SqlaTable, SqlMetric, database, admin, table_name):
    table = (
        db.session.query(SqlaTable)
        .filter_by(table_name=table_name, database_id=database.id)
        .one_or_none()
    )
    if table is None:
        table = SqlaTable(
            table_name=table_name,
            schema=SCHEMA,
            database=database,
            owners=[admin],
        )
        db.session.add(table)
        db.session.flush()
        table.fetch_metadata()
        print(f"created dataset {table_name}")
    if admin not in table.owners:
        table.owners.append(admin)
    have = {m.metric_name for m in table.metrics}
    for name, expr in METRICS.get(table_name, []):
        if name not in have:
            db.session.add(
                SqlMetric(
                    metric_name=name,
                    verbose_name=name.replace("_", " "),
                    metric_type="expression",
                    expression=expr,
                    table=table,
                )
            )
            have.add(name)
    return table


def upsert_slice(db, Slice, admin, table, names, viz, params, query_context):
    canonical = names[0]
    slc = (
        db.session.query(Slice)
        .filter(Slice.slice_name.in_(names))
        .order_by(Slice.id)
        .first()
    )
    if slc is None:
        slc = Slice(
            slice_name=canonical,
            viz_type=viz,
            datasource_type="table",
            datasource_id=table.id,
            datasource_name=table.table_name,
            owners=[admin],
            created_by=admin,
            changed_by=admin,
        )
        db.session.add(slc)
        print(f"created chart {canonical}")
    else:
        slc.slice_name = canonical
        print(f"updated chart {canonical}")
    slc.viz_type = viz
    slc.datasource_id = table.id
    slc.datasource_type = "table"
    slc.datasource_name = table.table_name
    slc.params = json.dumps(params)
    slc.query_context = json.dumps(query_context)
    slc.perm = table.get_perm()
    slc.schema_perm = table.schema_perm
    if admin not in slc.owners:
        slc.owners.append(admin)
    return slc


def build_position(rows):
    position = {
        "DASHBOARD_VERSION_KEY": "v2",
        "ROOT_ID": {"type": "ROOT", "id": "ROOT_ID", "children": ["GRID_ID"]},
        "GRID_ID": {"type": "GRID", "id": "GRID_ID", "children": [], "parents": ["ROOT_ID"]},
        "HEADER_ID": {"id": "HEADER_ID", "type": "HEADER", "meta": {"text": DASHBOARD_TITLE}},
    }
    for r_idx, row_slices in enumerate(rows):
        row_id = f"ROW-{r_idx}"
        position["GRID_ID"]["children"].append(row_id)
        chart_ids = []
        for slc in row_slices:
            cid = f"CHART-{slc.id}"
            chart_ids.append(cid)
            position[cid] = {
                "type": "CHART",
                "id": cid,
                "children": [],
                "parents": ["ROOT_ID", "GRID_ID", row_id],
                "meta": {
                    "chartId": slc.id,
                    "uuid": str(slc.uuid),
                    "width": 6,
                    "height": 50,
                    "sliceName": slc.slice_name,
                },
            }
        position[row_id] = {
            "type": "ROW",
            "id": row_id,
            "children": chart_ids,
            "parents": ["ROOT_ID", "GRID_ID"],
            "meta": {"background": "BACKGROUND_TRANSPARENT"},
        }
    return position


def main():
    app = create_app()
    with app.app_context():
        from flask_appbuilder.security.sqla.models import User
        from sqlalchemy.orm.attributes import flag_modified
        from superset import db
        from superset.connectors.sqla.models import SqlaTable, SqlMetric
        from superset.models.core import Database
        from superset.models.dashboard import Dashboard
        from superset.models.slice import Slice

        database = db.session.query(Database).filter_by(database_name="PayPulse ClickHouse").one_or_none()
        if database is None:
            raise SystemExit("PayPulse ClickHouse database is not registered")
        admin = db.session.query(User).filter_by(username="admin").one()

        tables = {
            name: ensure_dataset(db, SqlaTable, SqlMetric, database, admin, name) for name in MARTS
        }
        db.session.flush()

        risk = tables["mart_daily_risk_report"]
        revenue = tables["mart_daily_revenue"]
        aml = tables["mart_aml_structuring_patterns"]
        rfm = tables["mart_customer_risk_rfm"]
        merchant = tables["mart_merchant_risk_profile"]
        latency = tables["mart_settlement_latency"]

        risk_form = dist_bar_form(risk.id, "report_date", "sum_alert_count")
        revenue_form = dist_bar_form(revenue.id, "revenue_date", "sum_total_revenue")
        rfm_form = timeseries_params(rfm.id, "echarts_timeseries_bar", "risk_segment", "count", None)
        merchant_form = timeseries_params(merchant.id, "echarts_timeseries_bar", "merchant_id", "sum_alert_count", None)
        aml_cols = ["account_id", "event_date", "payment_count", "daily_total", "max_single_amount"]
        aml_form = table_form(aml.id, aml_cols, "daily_total")
        hist_form = histogram_form(latency.id, "latency_seconds")

        slices = {
            "Daily Revenue": upsert_slice(
                db, Slice, admin, revenue, ["Daily Revenue"], "dist_bar",
                revenue_form,
                dist_bar_query_context(revenue.id, revenue_form, "revenue_date", "sum_total_revenue"),
            ),
            "Daily Risk": upsert_slice(
                db, Slice, admin, risk, ["Daily Risk", "Daily Risk Alerts"], "dist_bar",
                risk_form,
                dist_bar_query_context(risk.id, risk_form, "report_date", "sum_alert_count"),
            ),
            "Customer RFM": upsert_slice(
                db, Slice, admin, rfm, ["Customer RFM"], "echarts_timeseries_bar",
                rfm_form,
                timeseries_query_context(rfm.id, rfm_form, "risk_segment", "count", None),
            ),
            "Merchant Risk": upsert_slice(
                db, Slice, admin, merchant, ["Merchant Risk"], "echarts_timeseries_bar",
                merchant_form,
                timeseries_query_context(merchant.id, merchant_form, "merchant_id", "sum_alert_count", None),
            ),
            "AML Structuring": upsert_slice(
                db, Slice, admin, aml, ["AML Structuring"], "table",
                aml_form, table_query_context(aml.id, aml_form, aml_cols, "daily_total"),
            ),
            "Settlement Latency": upsert_slice(
                db, Slice, admin, latency, ["Settlement Latency"], "histogram",
                hist_form, histogram_query_context(latency.id, hist_form, "latency_seconds"),
            ),
        }
        db.session.flush()

        layout = [
            [slices["Daily Revenue"], slices["Daily Risk"]],
            [slices["Customer RFM"], slices["Merchant Risk"]],
            [slices["AML Structuring"], slices["Settlement Latency"]],
        ]
        ordered = [slc for row in layout for slc in row]

        dash = db.session.query(Dashboard).filter_by(slug=DASHBOARD_SLUG).one_or_none()
        if dash is None:
            dash = db.session.query(Dashboard).filter_by(dashboard_title=DASHBOARD_TITLE).one_or_none()
        if dash is None:
            dash = Dashboard(dashboard_title=DASHBOARD_TITLE, created_by=admin, changed_by=admin)
            db.session.add(dash)
            print("created dashboard")
        else:
            print("updated dashboard")

        dash.dashboard_title = DASHBOARD_TITLE
        dash.slug = DASHBOARD_SLUG
        dash.published = True
        dash.owners = [admin]
        dash.slices = ordered
        dash.position_json = json.dumps(build_position(layout))
        dash.json_metadata = json.dumps(
            {
                "timed_refresh_immune_slices": [],
                "expanded_slices": {},
                "refresh_frequency": 0,
                "color_scheme": "supersetColors",
                "label_colors": {},
                "shared_label_colors": {},
                "cross_filters_enabled": True,
            }
        )
        flag_modified(dash, "position_json")
        db.session.commit()
        print(f"dashboard ready: /superset/dashboard/{DASHBOARD_SLUG}/")


if __name__ == "__main__":
    main()
