import React, { useState, useEffect, useRef } from 'react';
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
  IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import GitHubIcon from '@mui/icons-material/GitHub';
import repositoryService from '../services/repositoryService';
import { parseGitHubUrl } from '../utils/githubUrl';
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
  const [repoUrl, setRepoUrl] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
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
    setRepoUrl('');
    setError(null);
    setStatusMessage(null);
    setLoading(false);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const parsed = parseGitHubUrl(repoUrl);
    if (!parsed) {
      setError('Please enter a valid GitHub repository URL (e.g. https://github.com/owner/repository).');
      return;
    }

    const { owner, name } = parsed;

    setLoading(true);
    setError(null);
    setStatusMessage('Initiating repository import...');

    try {
      const job = await repositoryService.importRepo({
        owner,
        name,
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
          // Keep polling on transient network hiccup
        }
      }, 2000);
    } catch (err: unknown) {
      setLoading(false);
      setStatusMessage(null);
      const maybeAxiosErr = err as { response?: { data?: { message?: string } }; message?: string };
      const backendMsg = maybeAxiosErr?.response?.data?.message;
      const message =
        backendMsg ||
        (maybeAxiosErr?.message
          ? maybeAxiosErr.message
          : 'Failed to import repository. Please verify the URL and backend connectivity.');
      setError(message);
    }
  };

  return (
    <Dialog open={open} onClose={loading ? undefined : handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ m: 0, px: 3, py: 2.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
          <GitHubIcon sx={{ fontSize: 22, color: 'text.primary' }} />
          <Typography variant="h6" component="span" sx={{ fontWeight: 600, fontSize: '1.1rem' }}>
            Import GitHub Repository
          </Typography>
        </Box>
        <IconButton onClick={handleClose} size="small" disabled={loading} sx={{ color: 'text.secondary' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <form onSubmit={handleSubmit}>
        <DialogContent sx={{ px: 3, pt: 1, pb: 3 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2.5 }} onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {statusMessage && (
            <Alert severity="info" sx={{ mb: 2.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                {loading && <CircularProgress size={16} color="inherit" />}
                <Typography variant="body2">{statusMessage}</Typography>
              </Box>
            </Alert>
          )}

          <TextField
            label="GitHub Repository URL"
            placeholder="https://github.com/owner/repository"
            helperText="Paste the URL of a GitHub repository you want to analyze."
            value={repoUrl}
            onChange={(e) => {
              setRepoUrl(e.target.value);
              if (error) setError(null);
            }}
            required
            fullWidth
            disabled={loading}
            autoFocus
            sx={{ mt: 1 }}
          />
        </DialogContent>

        <DialogActions sx={{ px: 3, py: 2, borderTop: '1px solid', borderColor: 'divider' }}>
          <Button onClick={handleClose} disabled={loading} color="inherit">
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            color="primary"
            disabled={loading || !repoUrl.trim()}
            startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <GitHubIcon fontSize="small" />}
          >
            {loading ? 'Importing Repository...' : 'Import Repository'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default ImportRepoDialog;
