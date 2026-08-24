{{
  config(
    materialized='table',
    tags=['dimensions']
  )
}}

select
  {{ dbt_utils.generate_surrogate_key(['rule_id']) }} as rule_sk,
  rule_id,
  first_seen_at,
  last_seen_at,
  alert_count
from {{ ref('int_rules_latest') }}
