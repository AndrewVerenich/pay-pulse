select
  window_start,
  window_end,
  metric_date,
  currency,
  payment_count,
  total_amount
from {{ source('raw', 'payment_metrics_hourly_raw') }}
