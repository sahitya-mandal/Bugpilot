import React, { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  CircularProgress,
  Alert,
  Divider,
  Paper,
  Stack,
  IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import BugReportIcon from '@mui/icons-material/BugReport';
import CodeIcon from '@mui/icons-material/Code';
import RecommendIcon from '@mui/icons-material/Recommend';
import pullRequestService from '../services/pullRequestService';
import RiskChip from './RiskChip';
import type { PullRequestAnalysis } from '../types';

interface PrAnalysisModalProps {
  prId: number | null;
  prTitle?: string;
  prNumber?: number;
  open: boolean;
  onClose: () => void;
  onAnalyzed?: () => void;
}

export const PrAnalysisModal: React.FC<PrAnalysisModalProps> = ({
  prId,
  prTitle,
  prNumber,
  open,
  onClose,
  onAnalyzed,
}) => {
  const [analysis, setAnalysis] = useState<PullRequestAnalysis | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [analyzing, setAnalyzing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalysis = useCallback(async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await pullRequestService.getAnalysis(id);
      setAnalysis(data);
    } catch {
      setAnalysis(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open && prId) {
      fetchAnalysis(prId);
    } else {
      setAnalysis(null);
      setError(null);
    }
  }, [open, prId, fetchAnalysis]);

  const handleRunAnalysis = async () => {
    if (!prId) return;
    setAnalyzing(true);
    setError(null);
    try {
      const res = await pullRequestService.analyze(prId);
      setAnalysis(res);
      if (onAnalyzed) onAnalyzed();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : 'Failed to run AI PR review. Please check backend connection.';
      setError(message);
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <SmartToyIcon color="primary" />
          <Typography variant="h6" component="span" sx={{ fontWeight: 600 }}>
            AI Code Review {prNumber ? `(#${prNumber})` : ''}
          </Typography>
        </Box>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ p: 3 }}>
        {prTitle && (
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2, color: 'text.primary' }}>
            {prTitle}
          </Typography>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, gap: 2 }}>
            <CircularProgress />
            <Typography variant="body2" color="text.secondary">
              Checking existing AI review...
            </Typography>
          </Box>
        ) : analyzing ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, gap: 2 }}>
            <CircularProgress color="primary" />
            <Typography variant="h6">Analyzing Pull Request with Gemini AI...</Typography>
            <Typography variant="body2" color="text.secondary">
              Scanning diffs, evaluating edge cases, inspecting code quality, and assessing merge risk.
            </Typography>
          </Box>
        ) : analysis ? (
          <Stack spacing={2.5}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  Assessment:
                </Typography>
                <RiskChip riskLevel={analysis.riskLevel} size="medium" />
              </Box>
              {analysis.analyzedAt && (
                <Typography variant="caption" color="text.secondary">
                  Reviewed on: {new Date(analysis.analyzedAt).toLocaleString()}
                </Typography>
              )}
            </Box>

            {/* Summary */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Typography variant="caption" color="primary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                Review Summary
              </Typography>
              <Typography variant="body1" sx={{ mt: 0.5 }}>
                {analysis.summary}
              </Typography>
            </Paper>

            {/* Potential Bugs */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                <BugReportIcon color="error" fontSize="small" />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  Potential Bugs & Regressions
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {analysis.potentialBugs}
              </Typography>
            </Paper>

            {/* Code Quality Concerns */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                <CodeIcon color="warning" fontSize="small" />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  Code Quality Concerns
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {analysis.codeQualityConcerns}
              </Typography>
            </Paper>

            {/* Recommendations */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                <RecommendIcon color="success" fontSize="small" />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  Actionable Recommendations
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {analysis.recommendations}
              </Typography>
            </Paper>
          </Stack>
        ) : (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <SmartToyIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2, opacity: 0.8 }} />
            <Typography variant="h6" sx={{ mb: 1 }}>
              No AI Review Run Yet
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460, mx: 'auto', mb: 3 }}>
              BugPilot can review this pull request, discover bugs before merging, rate code risk, and suggest improvements.
            </Typography>
            <Button
              variant="contained"
              color="primary"
              startIcon={<SmartToyIcon />}
              onClick={handleRunAnalysis}
              size="large"
            >
              Run AI Code Review Now
            </Button>
          </Box>
        )}
      </DialogContent>

      <Divider />

      <DialogActions sx={{ p: 2, justifyContent: 'space-between' }}>
        {analysis && (
          <Button
            variant="outlined"
            color="primary"
            startIcon={<SmartToyIcon />}
            onClick={handleRunAnalysis}
            disabled={analyzing}
          >
            {analyzing ? 'Re-reviewing...' : 'Re-run AI Review'}
          </Button>
        )}
        <Box sx={{ ml: 'auto' }}>
          <Button onClick={onClose} color="inherit">
            Close
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  );
};

export default PrAnalysisModal;
