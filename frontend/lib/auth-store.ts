"use client";

import { create } from "zustand";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  email: string | null;
  displayName: string | null;
  isAuthenticated: boolean;
  setAuth: (auth: {
    accessToken: string;
    refreshToken: string;
    userId: string;
    email: string;
    displayName: string;
  }) => void;
  clearAuth: () => void;
  hydrate: () => void;
}

const STORAGE_KEY = "hearintent_auth";

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  userId: null,
  email: null,
  displayName: null,
  isAuthenticated: false,

  setAuth: (auth) => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
    }
    set({ ...auth, isAuthenticated: true });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      window.localStorage.removeItem(STORAGE_KEY);
    }
    set({
      accessToken: null,
      refreshToken: null,
      userId: null,
      email: null,
      displayName: null,
      isAuthenticated: false,
    });
  },

  hydrate: () => {
    if (typeof window === "undefined") return;
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    try {
      const parsed = JSON.parse(raw);
      set({ ...parsed, isAuthenticated: true });
    } catch {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  },
}));
