import api from './api';
import type { User } from '../types';

export interface UserCreateRequest {
  name: string;
  email: string;
  password?: string;
  role: string;
}

export const userService = {
  async getAll(): Promise<User[]> {
    const response = await api.get<User[]>('/users');
    return response.data;
  },

  async getById(id: number): Promise<User> {
    const response = await api.get<User>(`/users/${id}`);
    return response.data;
  },

  async create(user: UserCreateRequest): Promise<User> {
    const response = await api.post<User>('/users', user);
    return response.data;
  },

  async update(id: number, user: UserCreateRequest): Promise<User> {
    const response = await api.put<User>(`/users/${id}`, user);
    return response.data;
  },

  async deleteUser(id: number): Promise<string> {
    const response = await api.delete<string>(`/users/${id}`);
    return response.data;
  },
};

export default userService;
