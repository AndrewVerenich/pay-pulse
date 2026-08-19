import "@testing-library/jest-dom/vitest";

class MockEventSource {
  url: string;
  onopen: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  constructor(url: string) {
    this.url = url;
  }
  close() {}
}

// jsdom has no EventSource; provide a stub so useSSE can mount in tests.
(globalThis as unknown as { EventSource: unknown }).EventSource = MockEventSource;
