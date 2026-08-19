import { useAuthStore } from "@/stores/authStore";
import * as authClient from "@/api/authClient";

export function useAuth() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const username = useAuthStore((s) => s.username);

  return {
    accessToken,
    username,
    isAuthenticated: Boolean(accessToken),
    login: authClient.login,
    logout: authClient.logout,
  };
}
