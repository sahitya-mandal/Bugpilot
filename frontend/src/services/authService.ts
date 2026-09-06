import api from './api';
import type { LoginRequest, LoginResponse, RegisterRequest, JwtPayload, User, Role } from '../types';

export const authService = {
  async register(data: RegisterRequest): Promise<User> {
    const response = await api.post<User>('/auth/register', data);
    return response.data;
  },

  async login(credentials: LoginRequest): Promise<{ token: string; user: User }> {
    const response = await api.post<LoginResponse>('/auth/login', credentials);
    const { token, user: backendUser } = response.data;

    // Store token
    localStorage.setItem('bugpilot_token', token);

    let user: User;
    if (backendUser) {
      user = backendUser;
    } else {
      // Decode token to extract email and role as fallback
      const payload = authService.decodeToken(token);
      const role: Role = (payload?.role as Role) || 'DEVELOPER';
      const email = payload?.sub || credentials.email;
      user = {
        email,
        role,
        name: email.split('@')[0],
      };
    }

    localStorage.setItem('bugpilot_user', JSON.stringify(user));
    return { token, user };
  },

  logout(): void {
    localStorage.removeItem('bugpilot_token');
    localStorage.removeItem('bugpilot_user');
  },

  getToken(): string | null {
    return localStorage.getItem('bugpilot_token');
  },

  getStoredUser(): User | null {
    const raw = localStorage.getItem('bugpilot_user');
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },

  decodeToken(token: string): JwtPayload | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const base64Url = parts[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload) as JwtPayload;
    } catch {
      return null;
    }
  },

  isTokenExpired(token: string): boolean {
    const payload = authService.decodeToken(token);
    if (!payload || !payload.exp) return false;
    return payload.exp * 1000 < Date.now();
  },
};

export default authService;
