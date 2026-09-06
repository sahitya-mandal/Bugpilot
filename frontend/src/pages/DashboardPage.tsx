import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
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
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import FolderSpecialIcon from '@mui/icons-material/FolderSpecialOutlined';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import MergeTypeIcon from '@mui/icons-material/MergeTypeOutlined';
import CommitIcon from '@mui/icons-material/Commit';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import HistoryIcon from '@mui/icons-material/History';
import SyncIcon from '@mui/icons-material/Sync';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';
import AddIcon from '@mui/icons-material/Add';

import { useAuth } from '../context/AuthContext';
import repositoryService from '../services/repositoryService';
import analyticsService from '../services/analyticsService';
import StatCard from '../components/StatCard';
import EmptyState from '../components/EmptyState';
import ImportRepoDialog from '../components/ImportRepoDialog';
import type { Repository, Activity } from '../types';

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [syncingId, setSyncingId] = useState<number | null>(null);
  const [importModalOpen, setImportModalOpen] = useState<boolean>(false);

  // Metrics
  const [stats, setStats] = useState({
    totalRepos: 0,
    openIssues: 0,
    openPRs: 0,
    totalCommits: 0,
    aiAnalysesCount: 0,
  });

  const loadDashboardData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const repos = await repositoryService.getAll();
      setRepositories(repos);

      let totalIssues = 0;
      let totalCommits = 0;
      let totalPRs = 0;
      let aiCount = 0;
      const allActivities: Activity[] = [];

      // Collect analytics & activities for repos
      await Promise.all(
        repos.map(async (repo) => {
          try {
            const [analytics, repoActs] = await Promise.all([
              analyticsService.getRepositoryAnalytics(repo.id),
              analyticsService.getRepositoryActivities(repo.id),
            ]);
            totalIssues += analytics.openIssues || 0;
            totalPRs += analytics.openPullRequests || 0;
            totalCommits += analytics.totalCommits || 0;
            allActivities.push(...repoActs);

            if (analytics.issueSeverityDistribution) {
              Object.values(analytics.issueSeverityDistribution).forEach(
                (cnt) => (aiCount += cnt)
              );
            }
            if (analytics.prRiskDistribution) {
              Object.values(analytics.prRiskDistribution).forEach(
                (cnt) => (aiCount += cnt)
              );
            }
          } catch {
            // Repo might have no analytics yet
            totalIssues += repo.openIssuesCount || 0;
          }
        })
      );

      // Sort activities newest first
      allActivities.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setActivities(allActivities.slice(0, 10));

      setStats({
        totalRepos: repos.length,
        openIssues: totalIssues,
        openPRs: totalPRs,
        totalCommits: totalCommits,
        aiAnalysesCount: aiCount,
      });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Failed to fetch dashboard intelligence metrics.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  const syncPollTimers = React.useRef<{ [jobId: number]: ReturnType<typeof setInterval> }>({});

  useEffect(() => {
    return () => {
      Object.values(syncPollTimers.current).forEach((timer) => clearInterval(timer));
    };
  }, []);

  const handleSyncRepo = async (id: number) => {
    setSyncingId(id);
    try {
      const job = await repositoryService.syncRepo(id);
      const timer = setInterval(async () => {
        try {
          const updatedJob = await repositoryService.getSyncJob(job.jobId);
          if (updatedJob.status === 'COMPLETED') {
            clearInterval(timer);
            delete syncPollTimers.current[job.jobId];
            setSyncingId(null);
            await loadDashboardData();
          } else if (updatedJob.status === 'FAILED') {
            clearInterval(timer);
            delete syncPollTimers.current[job.jobId];
            setSyncingId(null);
            setError(updatedJob.errorMessage || 'Failed to sync repository.');
          }
        } catch {
          // Keep polling
        }
      }, 2000);
      syncPollTimers.current[job.jobId] = timer;
    } catch (err: any) {
      setSyncingId(null);
      const backendMsg = err?.response?.data?.message;
      const message = backendMsg || (err instanceof Error ? err.message : 'Failed to sync repository.');
      setError(message);
    }
  };

  const getActivityIcon = (type: string, description?: string) => {
    switch (type) {
      case 'AI_ANALYSIS_PERFORMED': {
        const isPr = description?.toLowerCase().includes('pr') || description?.toLowerCase().includes('review');
        if (isPr) {
          return <SmartToyIcon fontSize="small" sx={{ color: '#6366F1' }} />;
        }
        return <BugReportIcon fontSize="small" sx={{ color: '#EF4444' }} />;
      }
      case 'BUG_TRIAGE':
        return <BugReportIcon fontSize="small" sx={{ color: '#EF4444' }} />;
      case 'PR_REVIEW':
        return <SmartToyIcon fontSize="small" sx={{ color: '#6366F1' }} />;
      case 'REPOSITORY_SYNC':
        return <SyncIcon fontSize="small" sx={{ color: '#10B981' }} />;
      case 'REPOSITORY_IMPORT':
      default:
        return <CloudDownloadIcon fontSize="small" sx={{ color: '#38BDF8' }} />;
    }
  };

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
            Engineering Overview
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Welcome back, {user?.name || user?.email}. Real-time intelligence across your repositories.
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadDashboardData}
            disabled={loading}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={() => setImportModalOpen(true)}
          >
            Track Repository
          </Button>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Stat Cards Grid */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            sm: 'repeat(2, 1fr)',
            md: 'repeat(3, 1fr)',
            lg: 'repeat(5, 1fr)',
          },
          gap: 2.5,
          mb: 4,
        }}
      >
        <StatCard
          title="Tracked Repos"
          value={stats.totalRepos}
          subtitle="Monitored in BugPilot"
          icon={<FolderSpecialIcon />}
          color="#6366F1"
        />
        <StatCard
          title="Open Issues"
          value={stats.openIssues}
          subtitle="Active bug tickets"
          icon={<BugReportIcon />}
          color="#F59E0B"
        />
        <StatCard
          title="Open PRs"
          value={stats.openPRs}
          subtitle="Pending code reviews"
          icon={<MergeTypeIcon />}
          color="#8B5CF6"
        />
        <StatCard
          title="Total Commits"
          value={stats.totalCommits}
          subtitle="Indexed codebase history"
          icon={<CommitIcon />}
          color="#10B981"
        />
        <StatCard
          title="AI Analyses"
          value={stats.aiAnalysesCount}
          subtitle="AI Triages & Reviews run"
          icon={<AutoFixHighIcon />}
          color="#38BDF8"
        />
      </Box>

      {/* Main Grid: Repositories & Recent Activity */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' },
          gap: 3,
        }}
      >
        {/* Tracked Repositories Table */}
        <Card sx={{ height: 'fit-content' }}>
          <CardContent sx={{ p: 2.5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Tracked Repositories
              </Typography>
              <Button
                size="small"
                endIcon={<ArrowForwardIcon />}
                onClick={() => navigate('/repositories')}
              >
                View All
              </Button>
            </Box>

            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
                <CircularProgress size={36} />
              </Box>
            ) : repositories.length === 0 ? (
              <EmptyState
                icon={<FolderSpecialIcon sx={{ fontSize: 48 }} />}
                title="No Repositories Tracked Yet"
                description="Import your first GitHub repository to start collecting commits, triaging bugs, and running AI reviews."
                actionText="Track Repository"
                onAction={() => setImportModalOpen(true)}
              />
            ) : (
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Repository</TableCell>
                      <TableCell align="center">Stars</TableCell>
                      <TableCell align="center">Open Issues</TableCell>
                      <TableCell align="center">Last Synced</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {repositories.slice(0, 5).map((repo) => (
                      <TableRow
                        key={repo.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => navigate(`/repositories/${repo.id}`)}
                      >
                        <TableCell>
                          <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'primary.light' }}>
                            {repo.fullName || `${repo.owner}/${repo.name}`}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 280 }}>
                            {repo.description || 'No description provided'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center">
                          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
                            <StarBorderIcon fontSize="small" sx={{ color: '#F59E0B' }} />
                            <Typography variant="body2">{repo.stargazersCount || 0}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell align="center">
                          <Chip
                            label={repo.openIssuesCount || 0}
                            size="small"
                            color={repo.openIssuesCount > 0 ? 'warning' : 'default'}
                            sx={{ fontWeight: 600 }}
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Typography variant="caption" color="text.secondary">
                            {repo.syncedAt ? new Date(repo.syncedAt).toLocaleDateString() : 'Pending sync'}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                          <Tooltip title="Sync with GitHub">
                            <IconButton
                              size="small"
                              onClick={() => handleSyncRepo(repo.id)}
                              disabled={syncingId === repo.id}
                            >
                              {syncingId === repo.id ? (
                                <CircularProgress size={16} />
                              ) : (
                                <SyncIcon fontSize="small" />
                              )}
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </CardContent>
        </Card>

        {/* Activity Feed */}
        <Card sx={{ height: 'fit-content' }}>
          <CardContent sx={{ p: 2.5 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Recent Activity
              </Typography>
              <Button size="small" endIcon={<ArrowForwardIcon />} onClick={() => navigate('/activity')}>
                Feed
              </Button>
            </Box>

            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
                <CircularProgress size={36} />
              </Box>
            ) : activities.length === 0 ? (
              <EmptyState
                icon={<HistoryIcon sx={{ fontSize: 40 }} />}
                title="No Activity Yet"
                description="Activities such as repository imports, syncs, and AI reviews will appear here in chronological order."
              />
            ) : (
              <List disablePadding>
                {activities.map((act, idx) => (
                  <React.Fragment key={act.id || idx}>
                    <ListItem alignItems="flex-start" sx={{ px: 0, py: 1.5 }}>
                      <ListItemIcon sx={{ minWidth: 36, mt: 0.5 }}>
                        {getActivityIcon(act.activityType, act.description)}
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {act.description}
                          </Typography>
                        }
                        secondary={
                          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 0.5 }}>
                            <Typography variant="caption" color="primary.light">
                              {act.actor}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              • {new Date(act.createdAt).toLocaleString()}
                            </Typography>
                          </Box>
                        }
                      />
                    </ListItem>
                    {idx < activities.length - 1 && <Divider component="li" />}
                  </React.Fragment>
                ))}
              </List>
            )}
          </CardContent>
        </Card>
      </Box>

      {/* Import Repo Dialog */}
      <ImportRepoDialog
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={() => loadDashboardData()}
      />
    </Box>
  );
};

export default DashboardPage;
