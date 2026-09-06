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
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import DoneAllIcon from '@mui/icons-material/DoneAll';

import repositoryService from '../services/repositoryService';
import StatusChip from '../components/StatusChip';
import SeverityChip from '../components/SeverityChip';
import BugAnalysisModal from '../components/BugAnalysisModal';
import EmptyState from '../components/EmptyState';
import type { Repository, Issue } from '../types';

interface IssueWithRepo extends Issue {
  repoName: string;
}

export const IssuesPage: React.FC = () => {
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [issues, setIssues] = useState<IssueWithRepo[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [selectedRepoId, setSelectedRepoId] = useState<number | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'OPEN' | 'CLOSED'>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  // Modal
  const [selectedIssue, setSelectedIssue] = useState<Issue | null>(null);
  const [bugModalOpen, setBugModalOpen] = useState<boolean>(false);

  const fetchIssuesData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const repos = await repositoryService.getAll();
      setRepositories(repos);

      const allIssues: IssueWithRepo[] = [];
      await Promise.all(
        repos.map(async (repo) => {
          try {
            const repoIssues = await repositoryService.getIssues(repo.id);
            repoIssues.forEach((iss) => {
              allIssues.push({
                ...iss,
                repoName: repo.fullName || `${repo.owner}/${repo.name}`,
              });
            });
          } catch {
            // Ignore error for individual repos
          }
        })
      );

      // Sort newest issue number first
      allIssues.sort((a, b) => b.number - a.number);
      setIssues(allIssues);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch issues.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchIssuesData();
  }, [fetchIssuesData]);

  const filteredIssues = issues.filter((iss) => {
    if (selectedRepoId !== 'ALL' && iss.repositoryId !== selectedRepoId) return false;
    if (statusFilter !== 'ALL' && iss.state !== statusFilter) return false;
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      return (
        iss.title.toLowerCase().includes(q) ||
        (iss.author && iss.author.toLowerCase().includes(q)) ||
        iss.number.toString().includes(q)
      );
    }
    return true;
  });

  const handleOpenTriage = (issue: Issue) => {
    setSelectedIssue(issue);
    setBugModalOpen(true);
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" sx={{ fontWeight: 800 }}>
          Issues & AI Bug Triage
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Unified bug triage center. Run Gemini AI root-cause analysis, severity classification, and fix suggestions on any issue.
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
                <InputLabel id="repo-select-label">Repository</InputLabel>
                <Select
                  labelId="repo-select-label"
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
                {(['ALL', 'OPEN', 'CLOSED'] as const).map((filter) => (
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

      {/* Issues Table */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : filteredIssues.length === 0 ? (
        <EmptyState
          icon={<BugReportIcon sx={{ fontSize: 56 }} />}
          title={searchTerm ? 'No Issues Match Your Query' : 'No Issues Found'}
          description={
            repositories.length === 0
              ? 'Track a repository first to sync issues from GitHub.'
              : 'Try selecting another repository or resetting your filters.'
          }
        />
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 80 }}>#</TableCell>
                <TableCell>Issue Title & Details</TableCell>
                <TableCell>Repository</TableCell>
                <TableCell align="center">Status</TableCell>
                <TableCell align="center">Author</TableCell>
                <TableCell align="center">AI Status</TableCell>
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
                      <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 460 }}>
                        {issue.body}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={issue.repoName}
                      size="small"
                      variant="outlined"
                      sx={{ fontSize: '0.75rem', borderColor: 'rgba(255,255,255,0.1)' }}
                    />
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
                      onClick={() => handleOpenTriage(issue)}
                    >
                      {issue.hasAiAnalysis ? 'View Triage' : 'AI Triage'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* AI Bug Analysis Modal */}
      <BugAnalysisModal
        issueId={selectedIssue?.id || null}
        issueTitle={selectedIssue?.title}
        issueNumber={selectedIssue?.number}
        open={bugModalOpen}
        onClose={() => setBugModalOpen(false)}
        onAnalyzed={() => fetchIssuesData()}
      />
    </Box>
  );
};

export default IssuesPage;
