import { create } from 'zustand';
import type { User } from '../types';
import { authApi, getToken, setToken } from '../api';

interface AuthState {
  user: User | null;
  loading: boolean;
  initialized: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (email: string, username: string, password: string, code: string) => Promise<User>;
  logout: () => void;
  fetchMe: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loading: false,
  initialized: false,

  login: async (email, password) => {
    const res = await authApi.login(email, password);
    setToken(res.token);
    set({ user: res.user });
    return res.user;
  },

  register: (email: string, username: string, password: string, code: string) => {
    return authApi.register(email, username, password, code).then((res) => {
      setToken(res.token);
      set({ user: res.user });
      return res.user;
    });
  },

  logout: () => {
    setToken(null);
    set({ user: null });
  },

  // 应用启动时根据 token 恢复登录状态
  fetchMe: async () => {
    if (!getToken()) {
      set({ initialized: true, loading: false });
      return;
    }
    set({ loading: true });
    try {
      const res = await authApi.me();
      set({ user: res.user, loading: false, initialized: true });
    } catch {
      setToken(null);
      set({ user: null, loading: false, initialized: true });
    }
  },
}));
