import { describe, expect, it } from "vitest";
import { alertsPerMinute, filterAlerts } from "@/lib/alertsFilter";
import type { FraudAlert } from "@/types";

const sample: FraudAlert[] = [
  {
    alertId: "a1",
    userId: "acc-1",
    paymentId: "p1",
    score: 0.9,
    reasons: ["velocity"],
    ruleId: "rule-default",
    occurredAt: new Date().toISOString(),
  },
  {
    alertId: "a2",
    userId: "acc-2",
    paymentId: "p2",
    score: 0.5,
    reasons: ["geo"],
    ruleId: "high-amount",
    occurredAt: new Date().toISOString(),
  },
];

describe("filterAlerts", () => {
  it("filters by userId substring", () => {
    expect(filterAlerts(sample, { userId: "acc-1" })).toHaveLength(1);
  });

  it("filters by ruleId exact match", () => {
    expect(filterAlerts(sample, { ruleId: "high-amount" })).toHaveLength(1);
  });

  it("filters by minimum score", () => {
    expect(filterAlerts(sample, { minScore: 0.8 })).toHaveLength(1);
    expect(filterAlerts(sample, { minScore: 0.8 })[0].alertId).toBe("a1");
  });

  it("combines filters", () => {
    expect(filterAlerts(sample, { userId: "acc", minScore: 0.6 })).toHaveLength(1);
  });
});

describe("alertsPerMinute", () => {
  it("returns 15 minute buckets", () => {
    const buckets = alertsPerMinute(sample);
    expect(buckets).toHaveLength(15);
    expect(buckets.some((b) => b.count >= 1)).toBe(true);
  });
});
