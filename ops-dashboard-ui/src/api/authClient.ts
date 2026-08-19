import { useAuthStore } from "@/stores/authStore";
import type { TokenPair } from "@/types";

export class AuthError extends Error {}

export async function login(username: string, password: string): Promise<TokenPair> {
  const res = await fetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    throw new AuthError(res.status === 401 ? "Invalid credentials" : `Login failed (${res.status})`);
  }
  const tokens = (await res.json()) as TokenPair;
  useAuthStore.getState().setTokens(tokens.accessToken, tokens.refreshToken, username);
  return tokens;
}

export async function logout(): Promise<void> {
  const { refreshToken, clear } = useAuthStore.getState();
  try {
    if (refreshToken) {
      await fetch("/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
    }
  } finally {
    clear();
  }
}

async function refresh(): Promise<string | null> {
  const { refreshToken, setTokens } = useAuthStore.getState();
  if (!refreshToken) return null;
  const res = await fetch("/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) return null;
  const tokens = (await res.json()) as TokenPair;
  const { username } = useAuthStore.getState();
  setTokens(tokens.accessToken, tokens.refreshToken, username);
  return tokens.accessToken;
}

/**
 * fetch-обёртка с авто-рефрешем: на 401 однократно дёргает /auth/refresh и повторяет запрос.
 * На повторный 401 — гасит сессию (вызывающий код увидит ошибку и редиректит на /login).
 */
export async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const doFetch = (token: string | null) =>
    fetch(url, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });

  let { accessToken } = useAuthStore.getState();
  let res = await doFetch(accessToken);

  if (res.status === 401) {
    const refreshed = await refresh();
    if (refreshed) {
      accessToken = refreshed;
      res = await doFetch(accessToken);
    }
  }

  if (res.status === 401) {
    useAuthStore.getState().clear();
    throw new AuthError("Session expired");
  }
  if (!res.ok) {
    throw new Error(`Request failed (${res.status})`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
