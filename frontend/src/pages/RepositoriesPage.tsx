import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  TextField,
  Card,
  CardContent,
  Chip,
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Link,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import SyncIcon from '@mui/icons-material/Sync';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import CallSplitIcon from '@mui/icons-material/CallSplit';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import FolderSpecialIcon from '@mui/icons-material/FolderSpecialOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';

import { useAuth } from '../context/AuthContext';
import repositoryService from '../services/repositoryService';
import EmptyState from '../components/EmptyState';
import ImportRepoDialog from '../components/ImportRepoDialog';
import type { Repository } from '../types';

export const RepositoriesPage: React.FC = () => {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();

  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [filteredRepos, setFilteredRepos] = useState<Repository[]>([]);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const [syncingId, setSyncingId] = useState<number | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState<boolean>(false);
  const [repoToDelete, setRepoToDelete] = useState<Repository | null>(null);
  const [deleting, setDeleting] = useState<boolean>(false);
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);

  const fetchRepositories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await repositoryService.getAll();
      setRepositories(data);
      setFilteredRepos(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch repositories.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRepositories();
  }, [fetchRepositories]);

  useEffect(() => {
    if (!searchTerm.trim()) {
      setFilteredRepos(repositories);
    } else {
      const lower = searchTerm.toLowerCase();
      setFilteredRepos(
        repositories.filter(
          (r) =>
            r.name.toLowerCase().includes(lower) ||
            r.owner.toLowerCase().includes(lower) ||
            (r.fullName && r.fullName.toLowerCase().includes(lower)) ||
            (r.description && r.description.toLowerCase().includes(lower))
        )
      );
    }
  }, [searchTerm, repositories]);

  const syncPollTimers = React.useRef<{ [jobId: number]: ReturnType<typeof setInterval> }>({});

  useEffect(() => {
    return () => {
      Object.values(syncPollTimers.current).forEach((timer) => clearInterval(timer));
    };
  }, []);

  const handleSync = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setSyncingId(id);
    setError(null);
    setSuccessMsg(null);
    try {
      const job = await repositoryService.syncRepo(id);
      setSuccessMsg('Repository synchronization queued. Waiting for background completion...');

      const pollTimer = setInterval(async () => {
        try {
          const updatedJob = await repositoryService.getSyncJob(job.jobId);
          if (updatedJob.status === 'COMPLETED') {
            clearInterval(pollTimer);
            delete syncPollTimers.current[job.jobId];
            setSyncingId(null);
            setSuccessMsg('Repository synced successfully with GitHub.');
            fetchRepositories();
          } else if (updatedJob.status === 'FAILED') {
            clearInterval(pollTimer);
            delete syncPollTimers.current[job.jobId];
            setSyncingId(null);
            const rateInfo = updatedJob.rateLimitReset
              ? ` Rate limit resets at ${new Date(updatedJob.rateLimitReset * 1000).toLocaleTimeString()}.`
              : '';
            setError((updatedJob.errorMessage || 'Sync failed.') + rateInfo);
          }
        } catch {
          // Keep polling if transient network issue
        }
      }, 2000);

      syncPollTimers.current[job.jobId] = pollTimer;
    } catch (err: any) {
      setSyncingId(null);
      const backendMsg = err?.response?.data?.message;
      const message = backendMsg || (err instanceof Error ? err.message : 'Sync failed. Please check network/GitHub connection.');
      setError(message);
    }
  };

  const confirmDelete = (repo: Repository, e: React.MouseEvent) => {
    e.stopPropagation();
    setRepoToDelete(repo);
    setDeleteDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!repoToDelete) return;
    setDeleting(true);
    setError(null);
    try {
      await repositoryService.deleteRepo(repoToDelete.id);
      setSuccessMsg(`Repository ${repoToDelete.fullName || repoToDelete.name} deleted successfully.`);
      setDeleteDialogOpen(false);
      setRepoToDelete(null);
      await fetchRepositories();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to delete repository.';
      setError(message);
    } finally {
      setDeleting(false);
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
            Repositories
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Manage and monitor indexed GitHub and custom source code repositories.
          </Typography>
        </Box>

        <Button
          variant="contained"
          color="primary"
          startIcon={<AddIcon />}
          onClick={() => setImportDialogOpen(true)}
        >
          Track Repository
        </Button>
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

      {/* Search Filter */}
      <Box sx={{ mb: 3 }}>
        <TextField
          placeholder="Filter repositories by name, owner, or keyword..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          fullWidth
          slotProps={{
            input: {
              startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
            },
          }}
          sx={{ maxWidth: 500 }}
        />
      </Box>

      {/* Repositories Cards Grid */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : filteredRepos.length === 0 ? (
        <EmptyState
          icon={<FolderSpecialIcon sx={{ fontSize: 56 }} />}
          title={searchTerm ? 'No Repositories Match Your Search' : 'No Repositories Tracked Yet'}
          description={
            searchTerm
              ? 'Try adjusting your search query or clear the filter.'
              : 'Import repositories from GitHub to start indexing issues, pull requests, and commits.'
          }
          actionText={searchTerm ? 'Clear Search' : 'Track Repository'}
          onAction={() => (searchTerm ? setSearchTerm('') : setImportDialogOpen(true))}
        />
      ) : (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' },
            gap: 2.5,
          }}
        >
          {filteredRepos.map((repo) => (
            <Card
              key={repo.id}
              onClick={() => navigate(`/repositories/${repo.id}`)}
              sx={{
                cursor: 'pointer',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                transition: 'all 0.2s ease',
                '&:hover': {
                  borderColor: 'primary.main',
                  transform: 'translateY(-2px)',
                  boxShadow: '0 8px 24px -4px rgba(0, 0, 0, 0.4)',
                },
              }}
            >
              <CardContent sx={{ p: 2.5 }}>
                {/* Title & Actions */}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'primary.light', lineHeight: 1.3 }}>
                    {repo.fullName || `${repo.owner}/${repo.name}`}
                  </Typography>

                  <Box sx={{ display: 'flex', gap: 0.5 }} onClick={(e) => e.stopPropagation()}>
                    <Tooltip title="Sync with GitHub">
                      <IconButton
                        size="small"
                        onClick={(e) => handleSync(repo.id, e)}
                        disabled={syncingId === repo.id}
                      >
                        {syncingId === repo.id ? (
                          <CircularProgress size={16} />
                        ) : (
                          <SyncIcon fontSize="small" />
                        )}
                      </IconButton>
                    </Tooltip>

                    {repo.htmlUrl && (
                      <Tooltip title="View on GitHub">
                        <IconButton
                          size="small"
                          component={Link}
                          href={repo.htmlUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          <OpenInNewIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}

                    {isAdmin && (
                      <Tooltip title="Delete Repository (Admin)">
                        <IconButton
                          size="small"
                          color="error"
                          onClick={(e) => confirmDelete(repo, e)}
                        >
                          <DeleteOutlinedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Box>
                </Box>

                {/* Description */}
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{
                    mb: 2.5,
                    minHeight: 40,
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                  }}
                >
                  {repo.description || 'No description provided.'}
                </Typography>

                {/* Stats / Badges */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <StarBorderIcon fontSize="small" sx={{ color: '#F59E0B' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {repo.stargazersCount || 0}
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <CallSplitIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {repo.forksCount || 0}
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <BugReportIcon fontSize="small" sx={{ color: '#EF4444' }} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {repo.openIssuesCount || 0} issues
                    </Typography>
                  </Box>
                </Box>

                {/* Footer info */}
                <Box
                  sx={{
                    pt: 1.5,
                    borderTop: '1px solid rgba(255, 255, 255, 0.06)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <Chip
                    label={`branch: ${repo.defaultBranch || 'main'}`}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: '0.75rem' }}
                  />
                  <Typography variant="caption" color="text.secondary">
                    {repo.syncedAt
                      ? `Synced ${new Date(repo.syncedAt).toLocaleDateString()}`
                      : 'Pending sync'}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => !deleting && setDeleteDialogOpen(false)}>
        <DialogTitle>Confirm Repository Deletion</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete repository{' '}
            <strong>{repoToDelete?.fullName || repoToDelete?.name}</strong>?
            This will remove all indexed issues, pull requests, commits, and AI analysis records for this repository.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={() => setDeleteDialogOpen(false)} disabled={deleting} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleDelete}
            color="error"
            variant="contained"
            disabled={deleting}
            startIcon={deleting ? <CircularProgress size={18} color="inherit" /> : null}
          >
            {deleting ? 'Deleting...' : 'Delete Repository'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Import Modal */}
      <ImportRepoDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        onSuccess={() => fetchRepositories()}
      />
    </Box>
  );
};

export default RepositoriesPage;
