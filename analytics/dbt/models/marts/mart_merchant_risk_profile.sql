select
  p.merchant_id,
  count() as payment_count,
  sum(p.amount) as total_amount,
  countIf(a.alert_id is not null) as alert_count,
  avgIf(a.score, a.alert_id is not null) as avg_fraud_score,
  maxIf(a.score, a.alert_id is not null) as max_fraud_score
from {{ ref('fct_payments') }} as p
left join {{ ref('fct_fraud_alerts') }} as a
  on p.payment_id = a.payment_id
group by p.merchant_id
