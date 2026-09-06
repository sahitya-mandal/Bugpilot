import api from './api';
import type { Issue, BugAnalysis } from '../types';

export const issueService = {
  async getById(id: number): Promise<Issue> {
    const response = await api.get<Issue>(`/issues/${id}`);
    return response.data;
  },

  async analyze(id: number): Promise<BugAnalysis> {
    const response = await api.post<BugAnalysis>(`/issues/${id}/analyze`);
    return response.data;
  },

  async getAnalysis(id: number): Promise<BugAnalysis> {
    const response = await api.get<BugAnalysis>(`/issues/${id}/analysis`);
    return response.data;
  },
};

export default issueService;
