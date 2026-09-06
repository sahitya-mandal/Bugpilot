export type ActivityType =
  | 'REPOSITORY_IMPORT'
  | 'REPOSITORY_SYNC'
  | 'BUG_TRIAGE'
  | 'PR_REVIEW'
  | 'AI_ANALYSIS_PERFORMED'
  | 'ISSUE_CREATED'
  | 'ISSUE_CLOSED'
  | 'PR_OPENED'
  | 'PR_MERGED'
  | 'COMMIT_IMPORTED';

export interface Activity {
  id: number;
  repositoryId: number;
  activityType: ActivityType;
  description: string;
  actor: string;
  createdAt: string;
}
