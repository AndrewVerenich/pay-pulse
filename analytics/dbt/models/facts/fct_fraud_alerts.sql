{{
  config(
    materialized='table',
    tags=['facts']
  )
}}

with alerts_sk as (
  select
    *,
    {{ dbt_utils.generate_surrogate_key(['alert_id']) }} as alert_sk
  from {{ ref('stg_fraud_alerts') }}
)
select
  al.alert_sk,
  al.alert_id,
  al.user_id,
  al.payment_id,
  al.rule_id,
  al.score,
  al.reasons,
  al.alert_date,
  al.occurred_at,
  a.account_sk,
  r.rule_sk
from alerts_sk as al
left join {{ ref('dim_account') }} as a on al.user_id = a.account_id
left join {{ ref('dim_rule') }} as r on al.rule_id = r.rule_id
