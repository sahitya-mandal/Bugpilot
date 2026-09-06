import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Tabs,
  Tab,
  IconButton,
  Divider,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import GitHubIcon from '@mui/icons-material/GitHub';
import AddBoxIcon from '@mui/icons-material/AddBox';
import repositoryService from '../services/repositoryService';
import type { Repository } from '../types';

interface ImportRepoDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: (repo: Repository) => void;
}

export const ImportRepoDialog: React.FC<ImportRepoDialogProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const [tab, setTab] = useState<number>(0);
  const [owner, setOwner] = useState<string>('');
  const [name, setName] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollTimerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);

  React.useEffect(() => {
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
      }
    };
  }, []);

  const handleClose = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    setOwner('');
    setName('');
    setError(null);
    setStatusMessage(null);
    setLoading(false);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!owner.trim() || !name.trim()) {
      setError('Owner and repository name are required.');
      return;
    }

    setLoading(true);
    setError(null);
    setStatusMessage(null);

    try {
      if (tab === 0) {
        // GitHub Import
        const job = await repositoryService.importRepo({
          owner: owner.trim(),
          name: name.trim(),
        });

        setStatusMessage('Import queued. Tracking background synchronization...');

        if (pollTimerRef.current) {
          clearInterval(pollTimerRef.current);
        }

        pollTimerRef.current = setInterval(async () => {
          try {
            const updatedJob = await repositoryService.getSyncJob(job.jobId);
            if (updatedJob.status === 'COMPLETED') {
              if (pollTimerRef.current) {
                clearInterval(pollTimerRef.current);
                pollTimerRef.current = null;
              }
              const repo = await repositoryService.getById(job.repositoryId);
              onSuccess(repo);
              handleClose();
            } else if (updatedJob.status === 'FAILED') {
              if (pollTimerRef.current) {
                clearInterval(pollTimerRef.current);
                pollTimerRef.current = null;
              }
              setLoading(false);
              const rateInfo = updatedJob.rateLimitReset
                ? ` Rate limit resets at ${new Date(updatedJob.rateLimitReset * 1000).toLocaleTimeString()}.`
                : '';
              setError((updatedJob.errorMessage || 'Failed to import repository.') + rateInfo);
              setStatusMessage(null);
            } else {
              setStatusMessage(`Import ${updatedJob.status}: ${updatedJob.currentStep}...`);
            }
          } catch {
            // Keep polling
          }
        }, 2000);

      } else {
        // Manual Create
        const repo = await repositoryService.create({
          owner: owner.trim(),
          name: name.trim(),
        });
        onSuccess(repo);
        handleClose();
      }
    } catch (err: any) {
      setLoading(false);
      setStatusMessage(null);
      const backendMsg = err?.response?.data?.message;
      const message =
        backendMsg ||
        (err instanceof Error
          ? err.message
          : 'Failed to import repository. Please verify the owner and name, and check backend logs.');
      setError(message);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6" component="span" sx={{ fontWeight: 600 }}>
          {tab === 0 ? 'Import from GitHub' : 'Track Custom Repository'}
        </Typography>
        <IconButton onClick={handleClose} size="small" disabled={loading}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <Tabs
        value={tab}
        onChange={(_, val) => setTab(val)}
        sx={{ px: 2.5, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab icon={<GitHubIcon fontSize="small" />} iconPosition="start" label="GitHub Import & Sync" />
        <Tab icon={<AddBoxIcon fontSize="small" />} iconPosition="start" label="Manual Registry" />
      </Tabs>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ p: 3 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2.5 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {statusMessage && (
            <Alert severity="info" sx={{ mb: 2.5 }}>
              {statusMessage}
            </Alert>
          )}

          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {tab === 0
              ? 'Enter the GitHub repository coordinates. BugPilot will automatically fetch repository metadata, open issues, pull requests, and commit velocity.'
              : 'Manually register a code repository into BugPilot intelligence system.'}
          </Typography>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <TextField
              label="Repository Owner / Organization"
              placeholder="e.g. octocat, facebook, google"
              value={owner}
              onChange={(e) => setOwner(e.target.value)}
              required
              fullWidth
              disabled={loading}
              autoFocus
            />
            <TextField
              label="Repository Name"
              placeholder="e.g. Spoon-Knife, react, guice"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              fullWidth
              disabled={loading}
            />
          </Box>
        </DialogContent>

        <Divider />

        <DialogActions sx={{ p: 2 }}>
          <Button onClick={handleClose} disabled={loading} color="inherit">
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={loading || !owner.trim() || !name.trim()}
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : <GitHubIcon />}
          >
            {loading
              ? tab === 0
                ? 'Importing & Syncing...'
                : 'Creating...'
              : tab === 0
              ? 'Import Repository'
              : 'Register'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default ImportRepoDialog;
