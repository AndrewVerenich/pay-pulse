select
  p.account_id,
  max(p.occurred_at) as last_payment_at,
  count() as payment_count,
  sum(p.amount) as total_amount,
  countIf(a.max_score >= 0.8) as high_risk_payment_count
from {{ ref('stg_payment_events') }} as p
left join (
  select
    payment_id,
    max(score) as max_score
  from {{ ref('stg_fraud_alerts') }}
  group by payment_id
) as a
  on p.payment_id = a.payment_id
group by p.account_id
