import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/HistoryOutlined';
import SyncIcon from '@mui/icons-material/Sync';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import CloudDownloadIcon from '@mui/icons-material/CloudDownload';

import repositoryService from '../services/repositoryService';
import analyticsService from '../services/analyticsService';
import EmptyState from '../components/EmptyState';
import type { Repository, Activity, ActivityType } from '../types';

interface ActivityWithRepo extends Activity {
  repoName: string;
}

export const ActivityPage: React.FC = () => {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [activities, setActivities] = useState<ActivityWithRepo[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [selectedRepoId, setSelectedRepoId] = useState<number | 'ALL'>('ALL');
  const [typeFilter, setTypeFilter] = useState<ActivityType | 'ALL'>('ALL');

  const fetchActivities = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const repos = await repositoryService.getAll();
      setRepositories(repos);

      const allActs: ActivityWithRepo[] = [];
      await Promise.all(
        repos.map(async (repo) => {
          try {
            const acts = await analyticsService.getRepositoryActivities(repo.id);
            acts.forEach((act) => {
              allActs.push({
                ...act,
                repoName: repo.fullName || `${repo.owner}/${repo.name}`,
              });
            });
          } catch {
            // Ignore error for individual repos
          }
        })
      );

      // Sort newest first
      allActs.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setActivities(allActs);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch activity logs.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchActivities();
  }, [fetchActivities]);

  const filteredActivities = activities.filter((act) => {
    if (selectedRepoId !== 'ALL' && act.repositoryId !== selectedRepoId) return false;
    if (typeFilter !== 'ALL') {
      if (typeFilter === 'BUG_TRIAGE') {
        const isBugTriage = act.activityType === 'BUG_TRIAGE' ||
          (act.activityType === 'AI_ANALYSIS_PERFORMED' && !act.description?.toLowerCase().includes('pr'));
        if (!isBugTriage) return false;
      } else if (typeFilter === 'PR_REVIEW') {
        const isPrReview = act.activityType === 'PR_REVIEW' ||
          (act.activityType === 'AI_ANALYSIS_PERFORMED' && act.description?.toLowerCase().includes('pr'));
        if (!isPrReview) return false;
      } else if (typeFilter === 'REPOSITORY_SYNC') {
        if (act.activityType !== 'REPOSITORY_SYNC') return false;
      } else if (typeFilter === 'REPOSITORY_IMPORT') {
        if (act.activityType !== 'REPOSITORY_IMPORT') return false;
      } else if (act.activityType !== typeFilter) {
        return false;
      }
    }
    return true;
  });

  const getActivityChip = (type: ActivityType, description?: string) => {
    switch (type) {
      case 'AI_ANALYSIS_PERFORMED': {
        const isPr = description?.toLowerCase().includes('pr') || description?.toLowerCase().includes('review');
        if (isPr) {
          return (
            <Chip
              icon={<SmartToyIcon fontSize="small" />}
              label="AI PR Review"
              size="small"
              sx={{ bgcolor: 'rgba(99, 102, 241, 0.15)', color: '#818CF8', fontWeight: 600 }}
            />
          );
        }
        return (
          <Chip
            icon={<BugReportIcon fontSize="small" />}
            label="AI Bug Triage"
            size="small"
            sx={{ bgcolor: 'rgba(239, 68, 68, 0.15)', color: '#EF4444', fontWeight: 600 }}
          />
        );
      }
      case 'BUG_TRIAGE':
        return (
          <Chip
            icon={<BugReportIcon fontSize="small" />}
            label="AI Bug Triage"
            size="small"
            sx={{ bgcolor: 'rgba(239, 68, 68, 0.15)', color: '#EF4444', fontWeight: 600 }}
          />
        );
      case 'PR_REVIEW':
        return (
          <Chip
            icon={<SmartToyIcon fontSize="small" />}
            label="PR Review"
            size="small"
            sx={{ bgcolor: 'rgba(99, 102, 241, 0.15)', color: '#818CF8', fontWeight: 600 }}
          />
        );
      case 'REPOSITORY_SYNC':
        return (
          <Chip
            icon={<SyncIcon fontSize="small" />}
            label="GitHub Sync"
            size="small"
            sx={{ bgcolor: 'rgba(16, 185, 129, 0.15)', color: '#10B981', fontWeight: 600 }}
          />
        );
      case 'REPOSITORY_IMPORT':
      default:
        return (
          <Chip
            icon={<CloudDownloadIcon fontSize="small" />}
            label="Repo Import"
            size="small"
            sx={{ bgcolor: 'rgba(56, 189, 248, 0.15)', color: '#38BDF8', fontWeight: 600 }}
          />
        );
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Activity Timeline
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Chronological audit trail of all repository imports, syncs, bug triages, and automated code reviews.
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Filters Bar */}
      <Card sx={{ mb: 3 }}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
          <Box
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', sm: 'row' },
              gap: 2,
              alignItems: { sm: 'center' },
              justifyContent: 'space-between',
            }}
          >
            <FormControl size="small" sx={{ minWidth: 240 }}>
              <InputLabel id="activity-repo-filter">Filter by Repository</InputLabel>
              <Select
                labelId="activity-repo-filter"
                label="Filter by Repository"
                value={selectedRepoId}
                onChange={(e) => setSelectedRepoId(e.target.value as number | 'ALL')}
              >
                <MenuItem value="ALL">All Repositories ({repositories.length})</MenuItem>
                {repositories.map((r) => (
                  <MenuItem key={r.id} value={r.id}>
                    {r.fullName || `${r.owner}/${r.name}`}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              {(
                [
                  'ALL',
                  'REPOSITORY_IMPORT',
                  'REPOSITORY_SYNC',
                  'BUG_TRIAGE',
                  'PR_REVIEW',
                ] as const
              ).map((type) => (
                <Chip
                  key={type}
                  label={
                    type === 'ALL'
                      ? 'ALL'
                      : type === 'REPOSITORY_IMPORT'
                      ? 'Imports'
                      : type === 'REPOSITORY_SYNC'
                      ? 'Syncs'
                      : type === 'BUG_TRIAGE'
                      ? 'Bug Triages'
                      : 'PR Reviews'
                  }
                  size="small"
                  clickable
                  color={typeFilter === type ? 'primary' : 'default'}
                  onClick={() => setTypeFilter(type)}
                  variant={typeFilter === type ? 'filled' : 'outlined'}
                />
              ))}
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Activity Table */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : filteredActivities.length === 0 ? (
        <EmptyState
          icon={<HistoryIcon sx={{ fontSize: 56 }} />}
          title="No Activities Logged"
          description={
            activities.length === 0
              ? 'Import or sync a repository to start recording events.'
              : 'No activities match the selected filters.'
          }
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 140 }}>Activity</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Repository</TableCell>
                <TableCell align="center">Actor</TableCell>
                <TableCell align="right">Timestamp</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredActivities.map((act) => (
                <TableRow key={act.id} hover>
                  <TableCell>{getActivityChip(act.activityType, act.description)}</TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {act.description}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={act.repoName}
                      size="small"
                      variant="outlined"
                      sx={{ fontSize: '0.75rem', borderColor: 'rgba(255,255,255,0.1)' }}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Typography variant="body2" color="primary.light">
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
        </TableContainer>
      )}
    </Box>
  );
};

export default ActivityPage;
