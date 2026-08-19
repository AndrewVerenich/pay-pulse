import { create } from "zustand";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  username: string | null;
  setTokens: (accessToken: string, refreshToken: string, username?: string | null) => void;
  clear: () => void;
}

const ACCESS_KEY = "paypulse.accessToken";
const REFRESH_KEY = "paypulse.refreshToken";
const USER_KEY = "paypulse.username";

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem(ACCESS_KEY),
  refreshToken: localStorage.getItem(REFRESH_KEY),
  username: localStorage.getItem(USER_KEY),
  setTokens: (accessToken, refreshToken, username) => {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    if (username) localStorage.setItem(USER_KEY, username);
    set({ accessToken, refreshToken, username: username ?? null });
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    set({ accessToken: null, refreshToken: null, username: null });
  },
}));
