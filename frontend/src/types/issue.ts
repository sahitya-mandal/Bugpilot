import type { AnalysisSeverity } from './analysis';

export type IssueState = 'OPEN' | 'CLOSED';

export interface Issue {
  id: number;
  githubId?: number | null;
  number: number;
  title: string;
  body?: string | null;
  state: IssueState;
  author?: string | null;
  htmlUrl?: string | null;
  githubCreatedAt?: string | null;
  githubUpdatedAt?: string | null;
  githubClosedAt?: string | null;
  repositoryId: number;
  hasAiAnalysis: boolean;
  aiSeverity?: AnalysisSeverity | null;
}
