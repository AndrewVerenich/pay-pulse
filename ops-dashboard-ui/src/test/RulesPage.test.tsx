import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FraudRule } from "@/types";

const sampleRule: FraudRule = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "high-amount",
  enabled: true,
  json_spec: JSON.stringify({
    maxAmount: 10000,
    velocityWindowMs: 3600000,
    velocityMaxCount: 50,
    structuringThreshold: 9900,
    structuringWindowHours: 24,
    structuringMinPayments: 3,
  }),
  version: 2,
  updatedAt: null,
};

vi.mock("@/api/rulesClient", () => ({
  listRules: vi.fn(() => Promise.resolve([sampleRule])),
  createRule: vi.fn(),
  updateRule: vi.fn(),
  deleteRule: vi.fn(),
}));

import { RulesPage } from "@/pages/RulesPage";

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <RulesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("RulesPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders the rules table with fetched rule", async () => {
    renderPage();
    expect(screen.getByText("Fraud rules")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /new rule/i })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("high-amount")).toBeInTheDocument());
    expect(screen.getByText("10000")).toBeInTheDocument();
  });
});
