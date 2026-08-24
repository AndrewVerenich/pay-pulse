select
  alert_id,
  user_id,
  payment_id,
  score,
  reasons,
  coalesce(nullIf(rule_id, ''), 'unknown') as rule_id,
  alert_date,
  occurred_at
from {{ source('raw', 'fraud_alerts_raw') }}
