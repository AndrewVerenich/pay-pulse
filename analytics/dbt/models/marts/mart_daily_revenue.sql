select
  event_date as revenue_date,
  currency,
  count() as payment_count,
  sum(amount) as total_revenue,
  avg(amount) as avg_payment_amount
from {{ ref('fct_payments') }}
group by event_date, currency
order by revenue_date, currency
