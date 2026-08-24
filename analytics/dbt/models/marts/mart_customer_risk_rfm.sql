-- RFM-lite: Recency (дни с последнего платежа), Frequency, Monetary.
select
  account_id,
  dateDiff('day', last_payment_at, now()) as recency_days,
  payment_count as frequency,
  total_amount as monetary,
  high_risk_payment_count,
  case
    when high_risk_payment_count >= 3 then 'HIGH'
    when high_risk_payment_count >= 1 then 'MEDIUM'
    else 'LOW'
  end as risk_segment
from {{ ref('int_customer_activity') }}
