export type PullRequestState = 'OPEN' | 'CLOSED' | 'MERGED';

export interface PullRequest {
  id: number;
  githubId?: number | null;
  number: number;
  title: string;
  body?: string | null;
  state: PullRequestState;
  draft?: boolean | null;
  author?: string | null;
  htmlUrl?: string | null;
  sourceBranch?: string | null;
  targetBranch?: string | null;
  additions?: number | null;
  deletions?: number | null;
  changedFiles?: number | null;
  githubCreatedAt?: string | null;
  githubUpdatedAt?: string | null;
  githubClosedAt?: string | null;
  githubMergedAt?: string | null;
  repositoryId: number;
  hasAiAnalysis: boolean;
}
