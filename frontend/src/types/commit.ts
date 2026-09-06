export interface Commit {
  id: number;
  sha: string;
  message: string;
  authorName?: string | null;
  authorEmail?: string | null;
  committedAt: string;
  htmlUrl?: string | null;
  repositoryId: number;
}
