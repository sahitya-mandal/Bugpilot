import api from './api';
import type { PullRequest, PullRequestAnalysis } from '../types';

export const pullRequestService = {
  async getById(id: number): Promise<PullRequest> {
    const response = await api.get<PullRequest>(`/pull-requests/${id}`);
    return response.data;
  },

  async analyze(id: number): Promise<PullRequestAnalysis> {
    const response = await api.post<PullRequestAnalysis>(`/pull-requests/${id}/analyze`);
    return response.data;
  },

  async getAnalysis(id: number): Promise<PullRequestAnalysis> {
    const response = await api.get<PullRequestAnalysis>(`/pull-requests/${id}/analysis`);
    return response.data;
  },
};

export default pullRequestService;
