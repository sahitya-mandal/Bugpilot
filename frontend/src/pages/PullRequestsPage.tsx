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
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MergeTypeIcon from '@mui/icons-material/MergeTypeOutlined';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import DoneAllIcon from '@mui/icons-material/DoneAll';

import repositoryService from '../services/repositoryService';
import StatusChip from '../components/StatusChip';
import PrAnalysisModal from '../components/PrAnalysisModal';
import EmptyState from '../components/EmptyState';
import type { Repository, PullRequest } from '../types';

interface PrWithRepo extends PullRequest {
  repoName: string;
}

export const PullRequestsPage: React.FC = () => {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [pullRequests, setPullRequests] = useState<PrWithRepo[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [selectedRepoId, setSelectedRepoId] = useState<number | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OPEN' | 'CLOSED' | 'MERGED'>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Modal
  const [selectedPr, setSelectedPr] = useState<PullRequest | null>(null);
  const [prModalOpen, setPrModalOpen] = useState<boolean>(false);

  const fetchPrData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const repos = await repositoryService.getAll();
      setRepositories(repos);

      const allPrs: PrWithRepo[] = [];
      await Promise.all(
        repos.map(async (repo) => {
          try {
            const repoPrs = await repositoryService.getPullRequests(repo.id);
            repoPrs.forEach((pr) => {
              allPrs.push({
                ...pr,
                repoName: repo.fullName || `${repo.owner}/${repo.name}`,
              });
            });
          } catch {
            // Ignore error for individual repos
          }
        })
      );

      allPrs.sort((a, b) => b.number - a.number);
      setPullRequests(allPrs);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch pull requests.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPrData();
  }, [fetchPrData]);

  const filteredPrs = pullRequests.filter((pr) => {
    if (selectedRepoId !== 'ALL' && pr.repositoryId !== selectedRepoId) return false;
    if (statusFilter !== 'ALL' && pr.state !== statusFilter) return false;
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      return (
        pr.title.toLowerCase().includes(q) ||
        (pr.author && pr.author.toLowerCase().includes(q)) ||
        pr.number.toString().includes(q)
      );
    }
    return true;
  });

  const handleOpenReview = (pr: PullRequest) => {
    setSelectedPr(pr);
    setPrModalOpen(true);
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Pull Requests & AI Code Review
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Autonomous code review and risk intelligence. Scan PR changes for edge cases, regression risks, and architectural concerns.
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
              flexDirection: { xs: 'column', md: 'row' },
              gap: 2,
              alignItems: { md: 'center' },
              justifyContent: 'space-between',
            }}
          >
            <TextField
              placeholder="Search by title, author, or #..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              size="small"
              slotProps={{
                input: {
                  startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
                },
              }}
              sx={{ minWidth: 260 }}
            />

            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel id="pr-repo-select-label">Repository</InputLabel>
                <Select
                  labelId="pr-repo-select-label"
                  label="Repository"
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

              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                {(['ALL', 'OPEN', 'CLOSED', 'MERGED'] as const).map((filter) => (
                  <Chip
                    key={filter}
                    label={filter}
                    size="small"
                    clickable
                    color={statusFilter === filter ? 'primary' : 'default'}
                    onClick={() => setStatusFilter(filter)}
                    variant={statusFilter === filter ? 'filled' : 'outlined'}
                  />
                ))}
              </Box>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* PR Table */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : filteredPrs.length === 0 ? (
        <EmptyState
          icon={<MergeTypeIcon sx={{ fontSize: 56 }} />}
          title={searchTerm ? 'No Pull Requests Match Your Query' : 'No Pull Requests Found'}
          description={
            repositories.length === 0
              ? 'Track a repository first to sync pull requests from GitHub.'
              : 'Try selecting another repository or resetting your filters.'
          }
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 80 }}>#</TableCell>
                <TableCell>PR Title & Branches</TableCell>
                <TableCell>Repository</TableCell>
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
                  <TableCell>
                    <Chip
                      label={pr.repoName}
                      size="small"
                      variant="outlined"
                      sx={{ fontSize: '0.75rem', borderColor: 'rgba(255,255,255,0.1)' }}
                    />
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
                      onClick={() => handleOpenReview(pr)}
                    >
                      {pr.hasAiAnalysis ? 'View Review' : 'Run AI Review'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* AI PR Review Modal */}
      <PrAnalysisModal
        prId={selectedPr?.id || null}
        prTitle={selectedPr?.title}
        prNumber={selectedPr?.number}
        open={prModalOpen}
        onClose={() => setPrModalOpen(false)}
        onAnalyzed={() => fetchPrData()}
      />
    </Box>
  );
};

export default PullRequestsPage;
