{% test fraud_score_in_range(model, column_name, min=0, max=1) %}
  select *
  from {{ model }}
  where {{ column_name }} < {{ min }} or {{ column_name }} > {{ max }}
{% endtest %}
