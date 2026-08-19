export interface AlertFilter {
  userId?: string;
  ruleId?: string;
  minScore?: number;
}

export interface AlertLike {
  userId: string;
  ruleId: string;
  score: number;
  occurredAt: string;
}

/** Client-side фильтр последних N fraud alerts (S6 §8). */
export function filterAlerts<T extends AlertLike>(alerts: T[], filter: AlertFilter): T[] {
  return alerts.filter((alert) => {
    if (filter.userId && !alert.userId.toLowerCase().includes(filter.userId.toLowerCase())) {
      return false;
    }
    if (filter.ruleId && alert.ruleId !== filter.ruleId) {
      return false;
    }
    if (filter.minScore != null && alert.score < filter.minScore) {
      return false;
    }
    return true;
  });
}

/** Группирует алерты по минуте за последние 15 минут для LineChart. */
export function alertsPerMinute(alerts: AlertLike[], windowMinutes = 15): { minute: string; count: number }[] {
  const now = Date.now();
  const windowMs = windowMinutes * 60_000;
  const buckets = new Map<string, number>();

  for (let i = windowMinutes - 1; i >= 0; i--) {
    const t = new Date(now - i * 60_000);
    const key = `${t.getHours().toString().padStart(2, "0")}:${t.getMinutes().toString().padStart(2, "0")}`;
    buckets.set(key, 0);
  }

  for (const alert of alerts) {
    const ts = new Date(alert.occurredAt).getTime();
    if (now - ts > windowMs) continue;
    const d = new Date(ts);
    const key = `${d.getHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}`;
    if (buckets.has(key)) {
      buckets.set(key, (buckets.get(key) ?? 0) + 1);
    }
  }

  return Array.from(buckets.entries()).map(([minute, count]) => ({ minute, count }));
}
