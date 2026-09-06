import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Tabs,
  Tab,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  CircularProgress,
  Alert,
  Link,
  LinearProgress,
  Divider,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SyncIcon from '@mui/icons-material/Sync';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import CallSplitIcon from '@mui/icons-material/CallSplit';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import MergeTypeIcon from '@mui/icons-material/MergeTypeOutlined';
import CommitIcon from '@mui/icons-material/Commit';
import BarChartIcon from '@mui/icons-material/BarChartOutlined';
import PeopleIcon from '@mui/icons-material/People';
import HistoryIcon from '@mui/icons-material/HistoryOutlined';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import DoneAllIcon from '@mui/icons-material/DoneAll';

import repositoryService from '../services/repositoryService';
import analyticsService from '../services/analyticsService';
import StatusChip from '../components/StatusChip';
import SeverityChip from '../components/SeverityChip';
import BugAnalysisModal from '../components/BugAnalysisModal';
import PrAnalysisModal from '../components/PrAnalysisModal';
import EmptyState from '../components/EmptyState';
import StatCard from '../components/StatCard';
import type {
  Repository,
  Issue,
  PullRequest,
  Commit,
  Analytics,
  Activity,
} from '../types';

export const RepositoryDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const repoId = Number(id);

  const [tabIndex, setTabIndex] = useState<number>(0);
  const [repository, setRepository] = useState<Repository | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [syncing, setSyncing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Data tabs
  const [issues, setIssues] = useState<Issue[]>([]);
  const [pullRequests, setPullRequests] = useState<PullRequest[]>([]);
  const [commits, setCommits] = useState<Commit[]>([]);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [tabLoading, setTabLoading] = useState<boolean>(false);

  // Filters
  const [issueFilter, setIssueFilter] = useState<'ALL' | 'OPEN' | 'CLOSED'>('ALL');
  const [prFilter, setPrFilter] = useState<'ALL' | 'OPEN' | 'CLOSED' | 'MERGED'>('ALL');

  // AI Modals
  const [selectedIssue, setSelectedIssue] = useState<Issue | null>(null);
  const [bugModalOpen, setBugModalOpen] = useState<boolean>(false);

  const [selectedPr, setSelectedPr] = useState<PullRequest | null>(null);
  const [prModalOpen, setPrModalOpen] = useState<boolean>(false);

  const fetchRepoHeader = useCallback(async () => {
    if (!repoId) return;
    try {
      const data = await repositoryService.getById(repoId);
      setRepository(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch repository details.';
      setError(message);
    }
  }, [repoId]);

  const loadTabData = useCallback(async (index: number) => {
    if (!repoId) return;
    setTabLoading(true);
    try {
      if (index === 0) {
        const data = await repositoryService.getIssues(repoId);
        setIssues(data);
      } else if (index === 1) {
        const data = await repositoryService.getPullRequests(repoId);
        setPullRequests(data);
      } else if (index === 2) {
        const data = await repositoryService.getCommits(repoId);
        setCommits(data);
      } else if (index === 3) {
        const data = await analyticsService.getRepositoryAnalytics(repoId);
        setAnalytics(data);
      } else if (index === 4) {
        const data = await analyticsService.getRepositoryActivities(repoId);
        setActivities(data);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load tab data.';
      setError(message);
    } finally {
      setTabLoading(false);
    }
  }, [repoId]);

  useEffect(() => {
    const init = async () => {
      setLoading(true);
      await fetchRepoHeader();
      await loadTabData(tabIndex);
      setLoading(false);
    };
    init();
  }, [fetchRepoHeader, loadTabData, tabIndex]);

  const syncPollTimerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (syncPollTimerRef.current) {
        clearInterval(syncPollTimerRef.current);
      }
    };
  }, []);

  const handleSync = async () => {
    if (!repoId) return;
    setSyncing(true);
    setError(null);
    setSuccessMsg(null);
    try {
      const job = await repositoryService.syncRepo(repoId);
      setSuccessMsg('Repository synchronization queued. Waiting for background completion...');

      if (syncPollTimerRef.current) {
        clearInterval(syncPollTimerRef.current);
      }

      syncPollTimerRef.current = setInterval(async () => {
        try {
          const updatedJob = await repositoryService.getSyncJob(job.jobId);
          if (updatedJob.status === 'COMPLETED') {
            if (syncPollTimerRef.current) {
              clearInterval(syncPollTimerRef.current);
              syncPollTimerRef.current = null;
            }
            setSyncing(false);
            setSuccessMsg('Repository synchronized with GitHub successfully.');
            await fetchRepoHeader();
            await loadTabData(tabIndex);
          } else if (updatedJob.status === 'FAILED') {
            if (syncPollTimerRef.current) {
              clearInterval(syncPollTimerRef.current);
              syncPollTimerRef.current = null;
            }
            setSyncing(false);
            const rateInfo = updatedJob.rateLimitReset
              ? ` Rate limit resets at ${new Date(updatedJob.rateLimitReset * 1000).toLocaleTimeString()}.`
              : '';
            setError((updatedJob.errorMessage || 'Sync failed.') + rateInfo);
          }
        } catch {
          // Keep polling
        }
      }, 2000);

    } catch (err: any) {
      setSyncing(false);
      const backendMsg = err?.response?.data?.message;
      const message = backendMsg || (err instanceof Error ? err.message : 'Sync failed.');
      setError(message);
    }
  };

  const handleOpenBugModal = (issue: Issue) => {
    setSelectedIssue(issue);
    setBugModalOpen(true);
  };

  const handleOpenPrModal = (pr: PullRequest) => {
    setSelectedPr(pr);
    setPrModalOpen(true);
  };

  const filteredIssues = issues.filter((iss) => {
    if (issueFilter === 'ALL') return true;
    return iss.state === issueFilter;
  });

  const filteredPrs = pullRequests.filter((pr) => {
    if (prFilter === 'ALL') return true;
    return pr.state === prFilter;
  });

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!repository) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          Repository not found.
        </Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/repositories')}>
          Back to Repositories
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      {/* Top Back Navigation & Actions */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/repositories')}
          color="inherit"
          size="small"
        >
          All Repositories
        </Button>

        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Button
            variant="outlined"
            startIcon={syncing ? <CircularProgress size={18} color="inherit" /> : <SyncIcon />}
            onClick={handleSync}
            disabled={syncing}
          >
            {syncing ? 'Syncing...' : 'Sync Now'}
          </Button>

          {repository.htmlUrl && (
            <Button
              variant="contained"
              color="primary"
              endIcon={<OpenInNewIcon fontSize="small" />}
              component={Link}
              href={repository.htmlUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              GitHub
            </Button>
          )}
        </Box>
      </Box>

      {/* Messages */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {successMsg && (
        <Alert severity="success" sx={{ mb: 3 }} onClose={() => setSuccessMsg(null)}>
          {successMsg}
        </Alert>
      )}

      {/* Repository Header Card */}
      <Card sx={{ mb: 4, p: 1 }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
            <Typography variant="h4" sx={{ fontWeight: 800, color: 'primary.light' }}>
              {repository.fullName || `${repository.owner}/${repository.name}`}
            </Typography>
            <Chip
              label={repository.defaultBranch || 'main'}
              size="small"
              variant="outlined"
              sx={{ fontWeight: 600 }}
            />
          </Box>

          <Typography variant="body1" color="text.secondary" sx={{ mb: 2.5, maxWidth: 800 }}>
            {repository.description || 'No description provided for this repository.'}
          </Typography>

          <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'center' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
              <StarBorderIcon fontSize="small" sx={{ color: '#F59E0B' }} />
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {repository.stargazersCount || 0} Stars
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
              <CallSplitIcon fontSize="small" sx={{ color: 'text.secondary' }} />
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {repository.forksCount || 0} Forks
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
              <BugReportIcon fontSize="small" sx={{ color: '#EF4444' }} />
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {repository.openIssuesCount || 0} Open Issues
              </Typography>
            </Box>
            <Divider orientation="vertical" flexItem sx={{ display: { xs: 'none', sm: 'block' } }} />
            <Typography variant="caption" color="text.secondary">
              Last synced:{' '}
              {repository.syncedAt
                ? new Date(repository.syncedAt).toLocaleString()
                : 'Pending initial sync'}
            </Typography>
          </Box>
        </CardContent>
      </Card>

      {/* Navigation Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs
          value={tabIndex}
          onChange={(_, newVal) => {
            setTabIndex(newVal);
            loadTabData(newVal);
          }}
          textColor="primary"
          indicatorColor="primary"
        >
          <Tab icon={<BugReportIcon fontSize="small" />} iconPosition="start" label="Issues" />
          <Tab icon={<MergeTypeIcon fontSize="small" />} iconPosition="start" label="Pull Requests" />
          <Tab icon={<CommitIcon fontSize="small" />} iconPosition="start" label="Commits" />
          <Tab icon={<BarChartIcon fontSize="small" />} iconPosition="start" label="Analytics" />
          <Tab icon={<HistoryIcon fontSize="small" />} iconPosition="start" label="Activity Log" />
        </Tabs>
      </Box>

      {/* Tab Loading Indicator */}
      {tabLoading && (
        <Box sx={{ width: '100%', mb: 2 }}>
          <LinearProgress />
        </Box>
      )}

      {/* TAB 0: ISSUES */}
      {tabIndex === 0 && (
        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Issues ({filteredIssues.length})
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {(['ALL', 'OPEN', 'CLOSED'] as const).map((filter) => (
                <Chip
                  key={filter}
                  label={filter}
                  size="small"
                  clickable
                  color={issueFilter === filter ? 'primary' : 'default'}
                  onClick={() => setIssueFilter(filter)}
                  variant={issueFilter === filter ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
          </Box>

          {filteredIssues.length === 0 ? (
            <EmptyState
              icon={<BugReportIcon sx={{ fontSize: 48 }} />}
              title="No Issues Found"
              description={
                issueFilter !== 'ALL'
                  ? `No ${issueFilter.toLowerCase()} issues found for this repository.`
                  : 'Sync with GitHub to pull in tracked issues and run AI bug triage.'
              }
              actionText="Sync with GitHub"
              onAction={handleSync}
            />
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 80 }}>#</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell align="center">Status</TableCell>
                    <TableCell align="center">Author</TableCell>
                    <TableCell align="center">AI Triage Status</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredIssues.map((issue) => (
                    <TableRow key={issue.id} hover>
                      <TableCell sx={{ fontWeight: 600, color: 'text.secondary' }}>
                        #{issue.number}
                      </TableCell>
                      <TableCell>
                        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                          {issue.title}
                        </Typography>
                        {issue.body && (
                          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 450 }}>
                            {issue.body}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <StatusChip status={issue.state} />
                      </TableCell>
                      <TableCell align="center">
                        <Typography variant="body2">{issue.author || 'unknown'}</Typography>
                      </TableCell>
                      <TableCell align="center">
                        {issue.hasAiAnalysis ? (
                          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
                            <Chip
                              icon={<DoneAllIcon fontSize="small" />}
                              label="Triaged"
                              size="small"
                              color="success"
                              variant="outlined"
                            />
                            {issue.aiSeverity && (
                              <SeverityChip severity={issue.aiSeverity} size="small" />
                            )}
                          </Box>
                        ) : (
                          <Chip
                            label="Pending"
                            size="small"
                            variant="outlined"
                            sx={{ color: 'text.secondary', borderColor: 'divider' }}
                          />
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          variant={issue.hasAiAnalysis ? 'outlined' : 'contained'}
                          size="small"
                          color="primary"
                          startIcon={<AutoFixHighIcon fontSize="small" />}
                          onClick={() => handleOpenBugModal(issue)}
                        >
                          {issue.hasAiAnalysis ? 'View AI Triage' : 'AI Triage'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}

      {/* TAB 1: PULL REQUESTS */}
      {tabIndex === 1 && (
        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Pull Requests ({filteredPrs.length})
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {(['ALL', 'OPEN', 'CLOSED', 'MERGED'] as const).map((filter) => (
                <Chip
                  key={filter}
                  label={filter}
                  size="small"
                  clickable
                  color={prFilter === filter ? 'primary' : 'default'}
                  onClick={() => setPrFilter(filter)}
                  variant={prFilter === filter ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
          </Box>

          {filteredPrs.length === 0 ? (
            <EmptyState
              icon={<MergeTypeIcon sx={{ fontSize: 48 }} />}
              title="No Pull Requests Found"
              description={
                prFilter !== 'ALL'
                  ? `No ${prFilter.toLowerCase()} pull requests found.`
                  : 'Sync with GitHub to pull open or merged pull requests.'
              }
              actionText="Sync with GitHub"
              onAction={handleSync}
            />
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 80 }}>#</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell align="center">Status</TableCell>
                    <TableCell align="center">Changes</TableCell>
                    <TableCell align="center">Author</TableCell>
                    <TableCell align="center">AI Review</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredPrs.map((pr) => (
                    <TableRow key={pr.id} hover>
                      <TableCell sx={{ fontWeight: 600, color: 'text.secondary' }}>
                        #{pr.number}
                      </TableCell>
                      <TableCell>
                        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                          {pr.title}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {pr.sourceBranch} → {pr.targetBranch}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <StatusChip status={pr.state} />
                      </TableCell>
                      <TableCell align="center">
                        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                          <Typography variant="caption" sx={{ color: '#10B981', fontWeight: 600 }}>
                            +{pr.additions || 0}
                          </Typography>
                          <Typography variant="caption" sx={{ color: '#EF4444', fontWeight: 600 }}>
                            -{pr.deletions || 0}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="center">
                        <Typography variant="body2">{pr.author || 'unknown'}</Typography>
                      </TableCell>
                      <TableCell align="center">
                        {pr.hasAiAnalysis ? (
                          <Chip
                            icon={<DoneAllIcon fontSize="small" />}
                            label="Reviewed"
                            size="small"
                            color="success"
                            variant="outlined"
                          />
                        ) : (
                          <Chip
                            label="Pending"
                            size="small"
                            variant="outlined"
                            sx={{ color: 'text.secondary', borderColor: 'divider' }}
                          />
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          variant={pr.hasAiAnalysis ? 'outlined' : 'contained'}
                          size="small"
                          color="primary"
                          startIcon={<SmartToyIcon fontSize="small" />}
                          onClick={() => handleOpenPrModal(pr)}
                        >
                          {pr.hasAiAnalysis ? 'View AI Review' : 'Run AI Review'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}

      {/* TAB 2: COMMITS */}
      {tabIndex === 2 && (
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2.5 }}>
            Indexed Commits ({commits.length})
          </Typography>

          {commits.length === 0 ? (
            <EmptyState
              icon={<CommitIcon sx={{ fontSize: 48 }} />}
              title="No Commits Indexed"
              description="Sync this repository to fetch recent commits and contributor records from GitHub."
              actionText="Sync with GitHub"
              onAction={handleSync}
            />
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 120 }}>SHA</TableCell>
                    <TableCell>Commit Message</TableCell>
                    <TableCell>Author</TableCell>
                    <TableCell align="right">Committed At</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {commits.map((commit) => (
                    <TableRow key={commit.id || commit.sha} hover>
                      <TableCell>
                        <Chip
                          label={commit.sha.substring(0, 7)}
                          size="small"
                          component={commit.htmlUrl ? Link : 'div'}
                          href={commit.htmlUrl || undefined}
                          target="_blank"
                          clickable={!!commit.htmlUrl}
                          sx={{
                            fontFamily: 'monospace',
                            bgcolor: 'rgba(255, 255, 255, 0.05)',
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {commit.message}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{commit.authorName || 'Unknown'}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {commit.authorEmail}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" color="text.secondary">
                          {new Date(commit.committedAt).toLocaleString()}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      )}

      {/* TAB 3: ANALYTICS */}
      {tabIndex === 3 && (
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2.5 }}>
            Repository Analytics & AI Insights
          </Typography>

          {analytics ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              {/* Metrics Grid */}
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(5, 1fr)' },
                  gap: 2,
                }}
              >
                <StatCard
                  title="Total Issues"
                  value={analytics.totalIssues}
                  subtitle={`${analytics.openIssues} Open / ${analytics.closedIssues} Closed`}
                  icon={<BugReportIcon />}
                  color="#F59E0B"
                />
                <StatCard
                  title="AI Triage Coverage"
                  value={`${analytics.analyzedIssuesCount || 0} / ${analytics.totalIssues || 0}`}
                  subtitle={
                    analytics.totalIssues > 0
                      ? `${Math.round(((analytics.analyzedIssuesCount || 0) / analytics.totalIssues) * 100)}% coverage`
                      : '0 issues'
                  }
                  icon={<AutoFixHighIcon />}
                  color="#6366F1"
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
                  subtitle="Indexed codebase history"
                  icon={<CommitIcon />}
                  color="#10B981"
                />
                <StatCard
                  title="Contributors"
                  value={analytics.totalContributors}
                  subtitle="Active authors"
                  icon={<PeopleIcon />}
                  color="#38BDF8"
                />
              </Box>

              {/* Distributions */}
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 3 }}>
                {/* Issue Severity Distribution */}
                <Card>
                  <CardContent sx={{ p: 2.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                        Bug Severity Distribution
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {analytics.analyzedIssuesCount || 0} triaged
                      </Typography>
                    </Box>
                    {analytics.issueSeverityDistribution &&
                    Object.keys(analytics.issueSeverityDistribution).length > 0 ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        {Object.entries(analytics.issueSeverityDistribution).map(([sev, count]) => {
                          const total = analytics.analyzedIssuesCount || 0;
                          const pct = total > 0 ? Math.min(100, Math.round((count / total) * 100)) : 0;
                          const color =
                            sev === 'CRITICAL'
                              ? '#EF4444'
                              : sev === 'HIGH'
                              ? '#F97316'
                              : sev === 'MEDIUM'
                              ? '#F59E0B'
                              : '#10B981';

                          return (
                            <Box key={sev}>
                              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                  {sev}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {count} ({pct}%)
                                </Typography>
                              </Box>
                              <LinearProgress
                                variant="determinate"
                                value={pct}
                                sx={{
                                  height: 8,
                                  borderRadius: 4,
                                  bgcolor: 'rgba(255,255,255,0.06)',
                                  '& .MuiLinearProgress-bar': { bgcolor: color },
                                }}
                              />
                            </Box>
                          );
                        })}
                      </Box>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No AI bug triage evaluations completed for this repository yet.
                      </Typography>
                    )}
                  </CardContent>
                </Card>

                {/* PR Risk Distribution */}
                <Card>
                  <CardContent sx={{ p: 2.5 }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 2 }}>
                      Pull Request Risk Distribution
                    </Typography>
                    {analytics.prRiskDistribution &&
                    Object.keys(analytics.prRiskDistribution).length > 0 ? (
                      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
                              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                  {risk}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {count} ({pct}%)
                                </Typography>
                              </Box>
                              <LinearProgress
                                variant="determinate"
                                value={pct}
                                sx={{
                                  height: 8,
                                  borderRadius: 4,
                                  bgcolor: 'rgba(255,255,255,0.06)',
                                  '& .MuiLinearProgress-bar': { bgcolor: color },
                                }}
                              />
                            </Box>
                          );
                        })}
                      </Box>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No AI pull request reviews completed for this repository yet.
                      </Typography>
                    )}
                  </CardContent>
                </Card>
              </Box>
            </Box>
          ) : (
            <EmptyState
              icon={<BarChartIcon sx={{ fontSize: 48 }} />}
              title="No Analytics Data"
              description="Analytics will be generated as issues and pull requests are analyzed."
            />
          )}
        </Box>
      )}

      {/* TAB 4: ACTIVITY LOG */}
      {tabIndex === 4 && (
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2.5 }}>
            Repository Activity Timeline ({activities.length})
          </Typography>

          {activities.length === 0 ? (
            <EmptyState
              icon={<HistoryIcon sx={{ fontSize: 48 }} />}
              title="No Activities Logged"
              description="Events such as repository syncs and AI triages will be recorded here."
            />
          ) : (
            <Paper variant="outlined">
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Event</TableCell>
                    <TableCell>Description</TableCell>
                    <TableCell>Actor</TableCell>
                    <TableCell align="right">Timestamp</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {activities.map((act) => (
                    <TableRow key={act.id} hover>
                      <TableCell>
                        <Chip
                          label={
                            act.activityType === 'AI_ANALYSIS_PERFORMED'
                              ? (act.description?.toLowerCase().includes('pr') || act.description?.toLowerCase().includes('review')
                                ? 'AI PR Review'
                                : 'AI Bug Triage')
                              : act.activityType === 'BUG_TRIAGE'
                              ? 'AI Bug Triage'
                              : act.activityType === 'PR_REVIEW'
                              ? 'AI PR Review'
                              : act.activityType === 'REPOSITORY_SYNC'
                              ? 'GitHub Sync'
                              : act.activityType === 'REPOSITORY_IMPORT'
                              ? 'Repo Import'
                              : act.activityType.replace(/_/g, ' ')
                          }
                          size="small"
                          sx={{
                            fontWeight: 600,
                            bgcolor:
                              (act.activityType === 'AI_ANALYSIS_PERFORMED' && !act.description?.toLowerCase().includes('pr')) || act.activityType === 'BUG_TRIAGE'
                                ? 'rgba(239, 68, 68, 0.15)'
                                : 'rgba(99, 102, 241, 0.15)',
                            color:
                              (act.activityType === 'AI_ANALYSIS_PERFORMED' && !act.description?.toLowerCase().includes('pr')) || act.activityType === 'BUG_TRIAGE'
                                ? '#EF4444'
                                : '#818CF8',
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{act.description}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">
                          {act.actor}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="caption" color="text.secondary">
                          {new Date(act.createdAt).toLocaleString()}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          )}
        </Box>
      )}

      {/* Modals */}
      <BugAnalysisModal
        issueId={selectedIssue?.id || null}
        issueTitle={selectedIssue?.title}
        issueNumber={selectedIssue?.number}
        open={bugModalOpen}
        onClose={() => setBugModalOpen(false)}
        onAnalyzed={() => loadTabData(0)}
      />

      <PrAnalysisModal
        prId={selectedPr?.id || null}
        prTitle={selectedPr?.title}
        prNumber={selectedPr?.number}
        open={prModalOpen}
        onClose={() => setPrModalOpen(false)}
        onAnalyzed={() => loadTabData(1)}
      />
    </Box>
  );
};

export default RepositoryDetailPage;
