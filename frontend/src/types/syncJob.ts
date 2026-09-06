export type SyncJobStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type SyncJobStep =
  | 'QUEUED'
  | 'FETCHING_METADATA'
  | 'FETCHING_ISSUES'
  | 'FETCHING_PULL_REQUESTS'
  | 'FETCHING_COMMITS'
  | 'COMPLETED';

export interface SyncJobResponse {
  jobId: number;
  repositoryId: number;
  repositoryName: string;
  status: SyncJobStatus;
  currentStep: SyncJobStep;
  issuesImported?: number;
  pullRequestsImported?: number;
  commitsImported?: number;
  githubStatus?: number | null;
  rateLimitReset?: number | null;
  errorMessage?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  message?: string | null;
}
