# ADR 0001 — Temporal balance via `balance_events` table instead of aggregate snapshots

- Status: Accepted
- Date: 2026-05-10
- Context owner: payment-command + projection-balance teams
- Related plan section: [`docs/pay-pulse-platform-plan.md`](../pay-pulse-platform-plan.md) §3, §4 "Core data flow"
- Reference baseline: `distributed-backend-platform/event-sourcing-cqrs-banking` (snapshot pattern)

## Context

PayPulse uses Event Sourcing for the canonical write side (`payment_command.event_store`)
and CQRS read models. The reference platform `event-sourcing-cqrs-banking` solves
"give me the balance as of T or as of version V" via the classic **snapshot pattern**:

- A `snapshots` table stores the full aggregate state at version V every N events
  (configurable, e.g. every 100).
- To rebuild balance at any point in time the service loads the most recent snapshot
  not after T and replays events with `version > snapshot.version` and
  `occurred_at <= T`.
- Pros: bounded read amplification per query, classic ES textbook pattern.
- Cons: extra moving part (snapshotter job), state duplication, snapshot
  invalidation on schema evolution.

PayPulse's analytical workload differs:

1. The single aggregate today (`Payment`) is short-lived: 1–3 events per `payment_id`
   in steady state (`Initiated → Authorized → Settled` planned for S2). Snapshotting
   a payment is unnecessary — full replay is O(3).
2. The actually queried temporal axis is **per-account**, not per-aggregate
   (`/api/v1/accounts/{id}/balance?asOf=...`). Account balance is the
   running sum of *all* payments for that account, i.e. it crosses aggregate
   boundaries. Snapshots of `Payment` aggregates do not help answer this.
3. We already maintain a denormalized projection `account_query.balance_events`,
   one row per applied event with `(account_id, occurred_at, balance_after)`.
   This row already *is* a per-account snapshot at the resolution of "every event".

## Decision

We **do not implement** the per-aggregate `snapshots` table from the banking
reference. Temporal balance reads are served by `account_query.balance_events`:

```sql
SELECT balance_after
  FROM account_query.balance_events
 WHERE account_id   = :account
   AND occurred_at <= :as_of
 ORDER BY occurred_at DESC, source_event_id DESC
 LIMIT 1
```

This is functionally equivalent to "the last snapshot <= T" with N = 1, with no
separate snapshotting process.

## Consequences

Positive

- One fewer table, one fewer background job.
- Temporal queries are already O(1) index lookups
  (`balance_events` is indexed on `(account_id, occurred_at)`).
- Schema evolution is simpler: `balance_events` is a projection and can be
  rebuilt by replaying `payment.events` from earliest offset.

Negative / accepted limitations

- `balance_events` grows linearly with the number of events. We accept this:
  partition pruning by `occurred_at` (planned S7 with ClickHouse / Postgres
  partitioning) keeps it bounded.
- We lose the ability to "load the Payment aggregate at version V" cheaply
  *if* in S2 a payment ever accumulates a long event tail. If that becomes
  real, we will introduce snapshots **only for the `Payment` aggregate**, not
  retroactively for accounts.

## Alternatives considered

1. **Mirror banking snapshots 1:1** — rejected: solves a problem we don't
   have (per-aggregate replay cost) and does not solve the problem we do have
   (per-account temporal balance).
2. **No projection, replay `event_store` on every query** — rejected: O(N)
   per query, unacceptable for the dashboard.
3. **`balance_events` plus snapshots** — rejected as premature: snapshots
   would duplicate `balance_events` content for negligible gain.

## Revisit triggers

Re-open this ADR when *any* of the following happens:

- A `Payment` aggregate routinely exceeds ~50 events (e.g. partial captures,
  refunds, chargebacks). Then introduce per-aggregate snapshots in
  `payment_command`.
- Rebuild time for `balance_events` from `payment.events` exceeds the SLO for
  recovery (currently informal ~10 min for the demo dataset).
