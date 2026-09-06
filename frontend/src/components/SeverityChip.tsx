import React from 'react';
import { Chip } from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import type { AnalysisSeverity } from '../types';

interface SeverityChipProps {
  severity?: AnalysisSeverity | string | null;
  size?: 'small' | 'medium';
}

export const SeverityChip: React.FC<SeverityChipProps> = ({ severity, size = 'small' }) => {
  if (!severity) {
    return <Chip label="Not Analyzed" size={size} variant="outlined" sx={{ color: 'text.secondary', borderColor: 'divider' }} />;
  }

  const sevUpper = severity.toUpperCase();

  switch (sevUpper) {
    case 'CRITICAL':
      return (
        <Chip
          icon={<ReportProblemIcon fontSize="small" />}
          label="CRITICAL"
          size={size}
          sx={{
            backgroundColor: 'rgba(239, 68, 68, 0.15)',
            color: '#EF4444',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            fontWeight: 700,
          }}
        />
      );
    case 'HIGH':
      return (
        <Chip
          icon={<ErrorOutlineIcon fontSize="small" />}
          label="HIGH"
          size={size}
          sx={{
            backgroundColor: 'rgba(249, 115, 22, 0.15)',
            color: '#F97316',
            border: '1px solid rgba(249, 115, 22, 0.4)',
            fontWeight: 700,
          }}
        />
      );
    case 'MEDIUM':
      return (
        <Chip
          icon={<WarningAmberIcon fontSize="small" />}
          label="MEDIUM"
          size={size}
          sx={{
            backgroundColor: 'rgba(245, 158, 11, 0.15)',
            color: '#F59E0B',
            border: '1px solid rgba(245, 158, 11, 0.4)',
            fontWeight: 600,
          }}
        />
      );
    case 'LOW':
    default:
      return (
        <Chip
          icon={<InfoOutlinedIcon fontSize="small" />}
          label="LOW"
          size={size}
          sx={{
            backgroundColor: 'rgba(16, 185, 129, 0.15)',
            color: '#10B981',
            border: '1px solid rgba(16, 185, 129, 0.4)',
            fontWeight: 600,
          }}
        />
      );
  }
};

export default SeverityChip;
