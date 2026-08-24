select
  p.event_id,
  p.payment_id,
  p.account_id,
  p.amount,
  p.currency,
  p.merchant_id,
  p.occurred_at,
  p.event_date,
  a.alert_id,
  a.score as fraud_score,
  a.rule_id,
  a.reasons as fraud_reasons
from {{ ref('stg_payment_events') }} as p
left join {{ ref('stg_fraud_alerts') }} as a
  on p.payment_id = a.payment_id
