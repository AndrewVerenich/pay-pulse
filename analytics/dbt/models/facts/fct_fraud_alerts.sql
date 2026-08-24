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
  al.alert_sk as alert_sk,
  al.alert_id as alert_id,
  al.user_id as user_id,
  al.payment_id as payment_id,
  al.rule_id as rule_id,
  al.score as score,
  al.reasons as reasons,
  al.alert_date as alert_date,
  al.occurred_at as occurred_at,
  a.account_sk as account_sk,
  r.rule_sk as rule_sk
from alerts_sk as al
left join {{ ref('dim_account') }} as a on al.user_id = a.account_id
left join {{ ref('dim_rule') }} as r on al.rule_id = r.rule_id
