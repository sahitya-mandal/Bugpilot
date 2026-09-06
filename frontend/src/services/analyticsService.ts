import api from './api';
import type { Analytics, Activity } from '../types';

export const analyticsService = {
  async getRepositoryAnalytics(repositoryId: number): Promise<Analytics> {
    const response = await api.get<Analytics>(`/repositories/${repositoryId}/analytics`);
    return response.data;
  },

  async getRepositoryActivities(repositoryId: number): Promise<Activity[]> {
    const response = await api.get<Activity[]>(`/repositories/${repositoryId}/activities`);
    return response.data;
  },
};

export default analyticsService;
