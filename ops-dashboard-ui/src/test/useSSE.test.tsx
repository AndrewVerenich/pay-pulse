import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useSSE } from "@/hooks/useSSE";

interface OpenableEventSource {
  onopen: ((e: Event) => void) | null;
  onmessage: ((e: MessageEvent) => void) | null;
  instances: OpenableEventSource[];
}

describe("useSSE", () => {
  it("opens a connection and buffers parsed events", () => {
    const created: OpenableEventSource[] = [];
    class Recording {
      onopen: ((e: Event) => void) | null = null;
      onmessage: ((e: MessageEvent) => void) | null = null;
      onerror: ((e: Event) => void) | null = null;
      constructor() {
        created.push(this as unknown as OpenableEventSource);
      }
      close() {}
    }
    (globalThis as unknown as { EventSource: unknown }).EventSource = Recording;

    const { result } = renderHook(() => useSSE<{ id: string }>("/api/live/payments/stream"));

    expect(created.length).toBe(1);

    act(() => {
      created[0].onopen?.(new Event("open"));
      created[0].onmessage?.({ data: JSON.stringify({ id: "p1" }) } as MessageEvent);
    });

    expect(result.current.status).toBe("open");
    expect(result.current.data).toEqual([{ id: "p1" }]);
  });

  it("ignores malformed (keep-alive) frames", () => {
    const created: OpenableEventSource[] = [];
    class Recording {
      onopen: ((e: Event) => void) | null = null;
      onmessage: ((e: MessageEvent) => void) | null = null;
      onerror: ((e: Event) => void) | null = null;
      constructor() {
        created.push(this as unknown as OpenableEventSource);
      }
      close() {}
    }
    (globalThis as unknown as { EventSource: unknown }).EventSource = Recording;

    const { result } = renderHook(() => useSSE<{ id: string }>("/api/live/payments/stream"));
    act(() => {
      created[0].onmessage?.({ data: "not-json" } as MessageEvent);
    });
    expect(result.current.data).toEqual([]);
  });
});
