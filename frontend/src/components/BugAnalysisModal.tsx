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
  Chip,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import BugReportIcon from '@mui/icons-material/BugReport';
import CodeIcon from '@mui/icons-material/Code';
import TimelineIcon from '@mui/icons-material/Timeline';
import DomainIcon from '@mui/icons-material/Domain';
import issueService from '../services/issueService';
import SeverityChip from './SeverityChip';
import type { BugAnalysis } from '../types';

interface BugAnalysisModalProps {
  issueId: number | null;
  issueTitle?: string;
  issueNumber?: number;
  open: boolean;
  onClose: () => void;
  onAnalyzed?: () => void;
}

export const BugAnalysisModal: React.FC<BugAnalysisModalProps> = ({
  issueId,
  issueTitle,
  issueNumber,
  open,
  onClose,
  onAnalyzed,
}) => {
  const [analysis, setAnalysis] = useState<BugAnalysis | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [analyzing, setAnalyzing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalysis = useCallback(async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await issueService.getAnalysis(id);
      setAnalysis(data);
    } catch {
      // Not yet analyzed or not found
      setAnalysis(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open && issueId) {
      fetchAnalysis(issueId);
    } else {
      setAnalysis(null);
      setError(null);
    }
  }, [open, issueId, fetchAnalysis]);

  const handleRunAnalysis = async () => {
    if (!issueId) return;
    setAnalyzing(true);
    setError(null);
    try {
      const res = await issueService.analyze(issueId);
      setAnalysis(res);
      if (onAnalyzed) onAnalyzed();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : 'Failed to run AI bug triage. Please check backend connection.';
      setError(message);
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle sx={{ m: 0, p: 2.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <AutoFixHighIcon color="primary" />
          <Typography variant="h6" component="span" sx={{ fontWeight: 600 }}>
            AI Bug Triage {issueNumber ? `(#${issueNumber})` : ''}
          </Typography>
        </Box>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>

      <Divider />

      <DialogContent sx={{ p: 3 }}>
        {issueTitle && (
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 2, color: 'text.primary' }}>
            {issueTitle}
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
              Checking existing AI analysis...
            </Typography>
          </Box>
        ) : analyzing ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 6, gap: 2 }}>
            <CircularProgress color="primary" />
            <Typography variant="h6">Analyzing Issue with Gemini AI...</Typography>
            <Typography variant="body2" color="text.secondary">
              Diagnosing root causes, evaluating severity, and preparing remediation recommendations.
            </Typography>
          </Box>
        ) : analysis ? (
          <Stack spacing={2.5}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="caption" color="text.secondary">
                  Severity:
                </Typography>
                <SeverityChip severity={analysis.severity} size="medium" />
                {analysis.analysisSource === 'GEMINI' && (
                  <Chip
                    label="Gemini AI"
                    size="small"
                    color="primary"
                    variant="outlined"
                    sx={{ fontWeight: 600, height: 24, fontSize: '0.75rem' }}
                  />
                )}
                {analysis.analysisSource === 'HEURISTIC' && (
                  <Chip
                    label="Heuristic Engine"
                    size="small"
                    variant="outlined"
                    sx={{ fontWeight: 600, height: 24, fontSize: '0.75rem', color: 'text.secondary', borderColor: 'divider' }}
                  />
                )}
              </Box>
              {analysis.analyzedAt && (
                <Typography variant="caption" color="text.secondary">
                  Analyzed on: {new Date(analysis.analyzedAt).toLocaleString()}
                </Typography>
              )}
            </Box>

            {/* Summary */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Typography variant="caption" color="primary" sx={{ fontWeight: 600, textTransform: 'uppercase' }}>
                Executive Summary
              </Typography>
              <Typography variant="body1" sx={{ mt: 0.5 }}>
                {analysis.summary}
              </Typography>
            </Paper>

            {/* Probable Root Cause */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                <BugReportIcon color="error" fontSize="small" />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  Probable Root Cause
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {analysis.probableRootCause}
              </Typography>
            </Paper>

            {/* Suggested Fix */}
            <Paper variant="outlined" sx={{ p: 2, bgcolor: 'background.default' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <CodeIcon color="secondary" fontSize="small" />
                <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  Suggested Fix & Implementation
                </Typography>
              </Box>
              <Box
                component="pre"
                sx={{
                  m: 0,
                  p: 1.5,
                  borderRadius: 1,
                  bgcolor: '#0a0d14',
                  border: '1px solid rgba(255,255,255,0.06)',
                  fontFamily: 'Consolas, Monaco, "Courier New", monospace',
                  fontSize: '0.85rem',
                  color: '#E2E8F0',
                  overflowX: 'auto',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {analysis.suggestedFix}
              </Box>
            </Paper>

            {/* Affected Area & Next Steps */}
            <Box sx={{ display: 'flex', gap: 2, flexDirection: { xs: 'column', sm: 'row' } }}>
              <Paper variant="outlined" sx={{ p: 2, flex: 1, bgcolor: 'background.default' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  <DomainIcon color="info" fontSize="small" />
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    Affected Architecture Area
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {analysis.affectedArea}
                </Typography>
              </Paper>

              <Paper variant="outlined" sx={{ p: 2, flex: 1, bgcolor: 'background.default' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  <TimelineIcon color="warning" fontSize="small" />
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    Recommended Next Steps
                  </Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {analysis.recommendedNextSteps}
                </Typography>
              </Paper>
            </Box>
          </Stack>
        ) : (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <AutoFixHighIcon sx={{ fontSize: 48, color: 'primary.main', mb: 2, opacity: 0.8 }} />
            <Typography variant="h6" sx={{ mb: 1 }}>
              No AI Triage Run Yet
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460, mx: 'auto', mb: 3 }}>
              BugPilot can inspect this issue, diagnose probable root causes, classify severity, and generate suggested code remediation.
            </Typography>
            <Button
              variant="contained"
              color="primary"
              startIcon={<AutoFixHighIcon />}
              onClick={handleRunAnalysis}
              size="large"
            >
              Run AI Bug Triage Now
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
            startIcon={<AutoFixHighIcon />}
            onClick={handleRunAnalysis}
            disabled={analyzing}
          >
            {analyzing ? 'Re-analyzing...' : 'Re-analyze with AI'}
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

export default BugAnalysisModal;
