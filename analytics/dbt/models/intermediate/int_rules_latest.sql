select
  rule_id,
  min(occurred_at) as first_seen_at,
  max(occurred_at) as last_seen_at,
  count() as alert_count
from {{ ref('stg_fraud_alerts') }}
group by rule_id
