export interface Repository {
  id: number;
  name: string;
  owner: string;
  fullName: string;
  description?: string | null;
  htmlUrl: string;
  defaultBranch: string;
  githubId?: number | null;
  openIssuesCount: number;
  forksCount: number;
  stargazersCount: number;
  createdAt: string;
  updatedAt: string;
  syncedAt?: string | null;
  userId?: number | null;
}

export interface RepositoryRequest {
  owner: string;
  name: string;
  description?: string | null;
  githubUrl?: string | null;
}
