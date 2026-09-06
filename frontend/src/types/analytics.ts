export interface Analytics {
  repositoryId: number;
  repositoryName: string;
  totalIssues: number;
  openIssues: number;
  closedIssues: number;
  totalPullRequests: number;
  openPullRequests: number;
  mergedPullRequests: number;
  totalCommits: number;
  totalContributors: number;
  analyzedIssuesCount: number;
  issueSeverityDistribution: Record<string, number>;
  prRiskDistribution: Record<string, number>;
}
