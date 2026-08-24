# ADR 0001 — Temporal balance через `balance_events`, а не aggregate snapshots

- Статус: Принят
- Дата: 2026-05-10
- Владелец контекста: payment-command + projection-balance
- Референс: `distributed-backend-platform/event-sourcing-cqrs-banking` (паттерн snapshots)

## Контекст

PayPulse использует Event Sourcing на write-side (`payment_command.event_store`) и CQRS read-модели. В референсе `event-sourcing-cqrs-banking` вопрос «баланс на момент T / версии V» решается классическим **snapshot-паттерном**:

- таблица `snapshots` хранит полное состояние агрегата на версии V каждые N событий (например, каждые 100);
- для восстановления на момент T берётся последний snapshot не позже T и доигрываются события с `version > snapshot.version` и `occurred_at <= T`;
- плюсы: ограниченная стоимость чтения, учебный ES-паттерн;
- минусы: отдельный snapshotter, дублирование состояния, инвалидация при эволюции схемы.

У PayPulse аналитическая нагрузка другая:

1. Агрегат `Payment` короткий: 1–3 события на `payment_id` (`Initiated → Authorized → Settled`). Snapshot платежа не нужен — полный replay O(3).
2. Temporal-ось запросов — **по счёту**, не по агрегату (`/api/v1/accounts/{id}/balance?asOf=...`). Баланс счёта — сумма по всем платежам аккаунта, то есть пересекает границы агрегатов. Snapshot `Payment` на это не отвечает.
3. Уже есть денормализованная проекция `account_query.balance_events`: строка на применённое событие с `(account_id, occurred_at, balance_after)`. По сути это per-account snapshot с зерном «каждое событие».

## Решение

**Не внедряем** таблицу per-aggregate `snapshots` из banking-референса. Temporal-баланс читаем из `account_query.balance_events`:

```sql
SELECT balance_after
  FROM account_query.balance_events
 WHERE account_id   = :account
   AND occurred_at <= :as_of
 ORDER BY occurred_at DESC, source_event_id DESC
 LIMIT 1
```

Функционально это «последний snapshot ≤ T» при N = 1, без отдельного snapshotting-процесса.

## Последствия

### Плюсы

- На одну таблицу и один фоновый job меньше.
- Temporal-запросы — O(1) lookup по индексу `(account_id, occurred_at)`.
- Эволюция схемы проще: `balance_events` — проекция, её можно пересобрать из `payment.events` с earliest offset.

### Минусы / принятые ограничения

- `balance_events` растёт линейно с числом событий. Принимаем: позже pruning / partitioning по `occurred_at` (Postgres / ClickHouse).
- Дешёвый «загрузить Payment на version V» пропадает, если у платежа появится длинный хвост событий. Тогда snapshots введём **только для агрегата `Payment`**, не ретроактивно для счетов.

## Альтернативы

1. **Зеркалить banking snapshots 1:1** — отклонено: решает чужую проблему (стоимость replay агрегата) и не решает нашу (per-account temporal balance).
2. **Без проекции, replay `event_store` на каждый запрос** — отклонено: O(N) на запрос, неприемлемо для дашборда.
3. **`balance_events` плюс snapshots** — отклонено как premature: snapshots дублируют содержимое `balance_events` почти без выигрыша.

## Когда пересмотреть

Переоткрыть ADR, если:

- агрегат `Payment` стабильно превышает ~50 событий (partial captures, refunds, chargebacks) → snapshots в `payment_command`;
- rebuild `balance_events` из `payment.events` выходит за SLO восстановления (сейчас неформально ~10 мин на демо-датасете).
