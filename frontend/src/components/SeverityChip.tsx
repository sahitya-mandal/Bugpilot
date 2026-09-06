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
    return <Chip label="Not Analyzed" size={size} variant="outlined" sx={{ color: 'text.secondary', borderColor: 'divider', borderRadius: 1 }} />;
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
            backgroundColor: 'rgba(248, 81, 73, 0.15)',
            color: '#ff7b72',
            border: '1px solid rgba(248, 81, 73, 0.4)',
            fontWeight: 600,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#ff7b72' },
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
            backgroundColor: 'rgba(219, 109, 40, 0.15)',
            color: '#f0883e',
            border: '1px solid rgba(219, 109, 40, 0.4)',
            fontWeight: 600,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#f0883e' },
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
            backgroundColor: 'rgba(210, 153, 34, 0.15)',
            color: '#e3b341',
            border: '1px solid rgba(210, 153, 34, 0.4)',
            fontWeight: 600,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#e3b341' },
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
            backgroundColor: 'rgba(46, 160, 67, 0.15)',
            color: '#3fb950',
            border: '1px solid rgba(46, 160, 67, 0.4)',
            fontWeight: 600,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#3fb950' },
          }}
        />
      );
  }
};

export default SeverityChip;
