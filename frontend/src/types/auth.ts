export type Role = 'ADMIN' | 'DEVELOPER' | 'TESTER';

export interface User {
  id?: number;
  name?: string;
  email: string;
  role: Role;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  role?: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user?: User;
}

export interface JwtPayload {
  sub: string;
  role?: Role;
  exp?: number;
  iat?: number;
}
