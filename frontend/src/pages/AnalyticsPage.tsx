import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
  LinearProgress,
} from '@mui/material';
import BarChartIcon from '@mui/icons-material/BarChartOutlined';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import MergeTypeIcon from '@mui/icons-material/MergeTypeOutlined';
import CommitIcon from '@mui/icons-material/Commit';
import PeopleIcon from '@mui/icons-material/PeopleOutlined';

import repositoryService from '../services/repositoryService';
import analyticsService from '../services/analyticsService';
import StatCard from '../components/StatCard';
import EmptyState from '../components/EmptyState';
import type { Repository, Analytics } from '../types';

export const AnalyticsPage: React.FC = () => {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [selectedRepoId, setSelectedRepoId] = useState<number | ''>('');
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [analyticsLoading, setAnalyticsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRepositories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const repos = await repositoryService.getAll();
      setRepositories(repos);
      if (repos.length > 0) {
        setSelectedRepoId(repos[0].id);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load repositories.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRepositories();
  }, [fetchRepositories]);

  const fetchRepoAnalytics = useCallback(async (repoId: number) => {
    setAnalyticsLoading(true);
    setError(null);
    try {
      const data = await analyticsService.getRepositoryAnalytics(repoId);
      setAnalytics(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load analytics for repository.';
      setError(message);
      setAnalytics(null);
    } finally {
      setAnalyticsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedRepoId !== '') {
      fetchRepoAnalytics(Number(selectedRepoId));
    }
  }, [selectedRepoId, fetchRepoAnalytics]);

  return (
    <Box>
      {/* Header */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: { xs: 'flex-start', sm: 'center' },
          flexDirection: { xs: 'column', sm: 'row' },
          gap: 2,
          mb: 4,
        }}
      >
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800 }}>
            Engineering Intelligence & Analytics
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Codebase health metrics, AI severity allocations, and risk distributions.
          </Typography>
        </Box>

        {repositories.length > 0 && (
          <FormControl size="small" sx={{ minWidth: 260 }}>
            <InputLabel id="analytics-repo-select">Active Repository</InputLabel>
            <Select
              labelId="analytics-repo-select"
              label="Active Repository"
              value={selectedRepoId}
              onChange={(e) => setSelectedRepoId(Number(e.target.value))}
            >
              {repositories.map((repo) => (
                <MenuItem key={repo.id} value={repo.id}>
                  {repo.fullName || `${repo.owner}/${repo.name}`}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : repositories.length === 0 ? (
        <EmptyState
          icon={<BarChartIcon sx={{ fontSize: 56 }} />}
          title="No Repositories Available"
          description="Track repositories from GitHub to view intelligence metrics and distributions."
        />
      ) : analyticsLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : analytics ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {/* Top Metric Cards */}
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: {
                xs: '1fr',
                sm: 'repeat(2, 1fr)',
                md: 'repeat(4, 1fr)',
              },
              gap: 2.5,
            }}
          >
            <StatCard
              title="Issue Health"
              value={analytics.totalIssues}
              subtitle={`${analytics.openIssues} Open / ${analytics.closedIssues} Closed`}
              icon={<BugReportIcon />}
              color="#F59E0B"
            />
            <StatCard
              title="Pull Requests"
              value={analytics.totalPullRequests}
              subtitle={`${analytics.openPullRequests} Open / ${analytics.mergedPullRequests} Merged`}
              icon={<MergeTypeIcon />}
              color="#8B5CF6"
            />
            <StatCard
              title="Total Commits"
              value={analytics.totalCommits}
              subtitle="Indexed revisions"
              icon={<CommitIcon />}
              color="#10B981"
            />
            <StatCard
              title="Contributors"
              value={analytics.totalContributors}
              subtitle="Codebase authors"
              icon={<PeopleIcon />}
              color="#38BDF8"
            />
          </Box>

          {/* AI Severity & Risk Distributions */}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3 }}>
            {/* Bug Severity Distribution */}
            <Card>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                  Bug Severity Distribution
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Classified by Gemini AI Bug Triage across repository issues
                </Typography>

                {analytics.issueSeverityDistribution &&
                Object.keys(analytics.issueSeverityDistribution).length > 0 ? (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                    {Object.entries(analytics.issueSeverityDistribution).map(([severity, count]) => {
                      const total = analytics.totalIssues || 1;
                      const pct = Math.min(100, Math.round((count / total) * 100));
                      const color =
                        severity === 'CRITICAL'
                          ? '#EF4444'
                          : severity === 'HIGH'
                          ? '#F97316'
                          : severity === 'MEDIUM'
                          ? '#F59E0B'
                          : '#10B981';

                      return (
                        <Box key={severity}>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {severity}
                            </Typography>
                            <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                              {count} issues ({pct}%)
                            </Typography>
                          </Box>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            sx={{
                              height: 10,
                              borderRadius: 5,
                              bgcolor: 'rgba(255,255,255,0.06)',
                              '& .MuiLinearProgress-bar': { bgcolor: color },
                            }}
                          />
                        </Box>
                      );
                    })}
                  </Box>
                ) : (
                  <Box sx={{ py: 3, textAlign: 'center' }}>
                    <Typography variant="body2" color="text.secondary">
                      No issues have been triaged by AI in this repository yet.
                    </Typography>
                  </Box>
                )}
              </CardContent>
            </Card>

            {/* PR Risk Distribution */}
            <Card>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                  Pull Request Risk Distribution
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Classified by Gemini AI Pull Request Code Review
                </Typography>

                {analytics.prRiskDistribution &&
                Object.keys(analytics.prRiskDistribution).length > 0 ? (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                    {Object.entries(analytics.prRiskDistribution).map(([risk, count]) => {
                      const total = analytics.totalPullRequests || 1;
                      const pct = Math.min(100, Math.round((count / total) * 100));
                      const color =
                        risk === 'CRITICAL'
                          ? '#EF4444'
                          : risk === 'HIGH'
                          ? '#F97316'
                          : risk === 'MEDIUM'
                          ? '#F59E0B'
                          : '#10B981';

                      return (
                        <Box key={risk}>
                          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {risk}
                            </Typography>
                            <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                              {count} PRs ({pct}%)
                            </Typography>
                          </Box>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            sx={{
                              height: 10,
                              borderRadius: 5,
                              bgcolor: 'rgba(255,255,255,0.06)',
                              '& .MuiLinearProgress-bar': { bgcolor: color },
                            }}
                          />
                        </Box>
                      );
                    })}
                  </Box>
                ) : (
                  <Box sx={{ py: 3, textAlign: 'center' }}>
                    <Typography variant="body2" color="text.secondary">
                      No pull requests have been reviewed by AI in this repository yet.
                    </Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
          </Box>
        </Box>
      ) : (
        <EmptyState
          icon={<BarChartIcon sx={{ fontSize: 56 }} />}
          title="No Analytics Found"
          description="Select a repository to inspect its engineering analytics."
        />
      )}
    </Box>
  );
};

export default AnalyticsPage;
