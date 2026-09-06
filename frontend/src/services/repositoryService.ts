import api from './api';
import type {
  Repository,
  RepositoryRequest,
  Issue,
  PullRequest,
  Commit,
  SyncJobResponse,
} from '../types';

export const repositoryService = {
  async getAll(): Promise<Repository[]> {
    const response = await api.get<Repository[]>('/repositories');
    return response.data;
  },

  async getById(id: number): Promise<Repository> {
    const response = await api.get<Repository>(`/repositories/${id}`);
    return response.data;
  },

  async create(request: RepositoryRequest): Promise<Repository> {
    const response = await api.post<Repository>('/repositories', request);
    return response.data;
  },

  async importRepo(request: RepositoryRequest): Promise<SyncJobResponse> {
    const response = await api.post<SyncJobResponse>('/repositories/import', request);
    return response.data;
  },

  async syncRepo(id: number): Promise<SyncJobResponse> {
    const response = await api.post<SyncJobResponse>(`/repositories/${id}/sync`);
    return response.data;
  },

  async getSyncJob(jobId: number): Promise<SyncJobResponse> {
    const response = await api.get<SyncJobResponse>(`/sync-jobs/${jobId}`);
    return response.data;
  },

  async getRepoSyncJobs(id: number): Promise<SyncJobResponse[]> {
    const response = await api.get<SyncJobResponse[]>(`/repositories/${id}/sync-jobs`);
    return response.data;
  },

  async deleteRepo(id: number): Promise<string> {
    const response = await api.delete<string>(`/repositories/${id}`);
    return response.data;
  },

  async getIssues(id: number): Promise<Issue[]> {
    const response = await api.get<Issue[]>(`/repositories/${id}/issues`);
    return response.data;
  },

  async getPullRequests(id: number): Promise<PullRequest[]> {
    const response = await api.get<PullRequest[]>(`/repositories/${id}/pull-requests`);
    return response.data;
  },

  async getCommits(id: number): Promise<Commit[]> {
    const response = await api.get<Commit[]>(`/repositories/${id}/commits`);
    return response.data;
  },
};

export default repositoryService;
