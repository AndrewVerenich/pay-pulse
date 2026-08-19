import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { LoginPage } from "@/pages/LoginPage";
import { LivePage } from "@/pages/LivePage";
import { PaymentDetailsPage } from "@/pages/PaymentDetailsPage";
import { RulesPage } from "@/pages/RulesPage";
import { AlertsPage } from "@/pages/AlertsPage";
import { StuckSagasPage } from "@/pages/StuckSagasPage";
import { HealthPage } from "@/pages/HealthPage";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/live" element={<LivePage />} />
            <Route path="/payments/:id" element={<PaymentDetailsPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/sagas/stuck" element={<StuckSagasPage />} />
            <Route path="/health" element={<HealthPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/live" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
