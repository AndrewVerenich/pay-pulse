select
  alert_date as report_date,
  count() as alert_count,
  avg(score) as avg_fraud_score,
  max(score) as max_fraud_score,
  uniqExact(user_id) as affected_users,
  uniqExact(rule_id) as triggered_rules
from {{ ref('fct_fraud_alerts') }}
group by alert_date
order by report_date
