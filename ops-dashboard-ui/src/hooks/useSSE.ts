import { useEffect, useState } from "react";
import { useAuthStore } from "@/stores/authStore";

export type SSEStatus = "idle" | "open" | "error" | "closed";

interface UseSSEOptions {
  withAuth?: boolean;
  bufferSize?: number;
  enabled?: boolean;
  onError?: (e: Event) => void;
}

/**
 * Подписка на SSE-поток. Поскольку нативный EventSource не умеет заголовки,
 * при withAuth токен подмешивается в query (?token=...). Хранит кольцевой буфер последних событий.
 */
export function useSSE<T>(
  url: string | null,
  options: UseSSEOptions = {},
): { data: T[]; status: SSEStatus } {
  const { withAuth = false, bufferSize = 200, enabled = true, onError } = options;
  const [data, setData] = useState<T[]>([]);
  const [status, setStatus] = useState<SSEStatus>("idle");
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (!url || !enabled) return;
    if (withAuth && !accessToken) return;

    const fullUrl = withAuth && accessToken
      ? `${url}${url.includes("?") ? "&" : "?"}token=${encodeURIComponent(accessToken)}`
      : url;

    const es = new EventSource(fullUrl);
    setStatus("idle");

    es.onopen = () => setStatus("open");
    es.onmessage = (ev: MessageEvent) => {
      try {
        const parsed = JSON.parse(ev.data) as T;
        setData((prev) => {
          const next = [parsed, ...prev];
          return next.length > bufferSize ? next.slice(0, bufferSize) : next;
        });
      } catch {
        /* ignore keep-alive / malformed frames */
      }
    };
    es.onerror = (ev: Event) => {
      setStatus("error");
      onError?.(ev);
    };

    return () => {
      es.close();
      setStatus("closed");
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, withAuth, accessToken, enabled]);

  return { data, status };
}
