export type AnalysisSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface BugAnalysis {
  id: number;
  issueId: number;
  issueNumber: number;
  issueTitle: string;
  summary: string;
  probableRootCause: string;
  severity: AnalysisSeverity;
  suggestedFix: string;
  affectedArea: string;
  recommendedNextSteps: string;
  analyzedAt: string;
  analysisSource?: 'GEMINI' | 'HEURISTIC' | null;
}

export interface PullRequestAnalysis {
  id: number;
  pullRequestId: number;
  pullRequestNumber: number;
  pullRequestTitle: string;
  summary: string;
  potentialBugs: string;
  codeQualityConcerns: string;
  riskLevel: RiskLevel;
  recommendations: string;
  analyzedAt: string;
}
