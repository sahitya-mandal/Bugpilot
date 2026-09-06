import React from 'react';
import { Chip } from '@mui/material';
import AdjustIcon from '@mui/icons-material/Adjust';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import MergeTypeIcon from '@mui/icons-material/MergeType';
import type { IssueState, PullRequestState } from '../types';

interface StatusChipProps {
  status: IssueState | PullRequestState | string;
  size?: 'small' | 'medium';
}

export const StatusChip: React.FC<StatusChipProps> = ({ status, size = 'small' }) => {
  const s = status.toUpperCase();

  switch (s) {
    case 'OPEN':
      return (
        <Chip
          icon={<AdjustIcon fontSize="small" />}
          label="Open"
          size={size}
          sx={{
            backgroundColor: 'rgba(46, 160, 67, 0.15)',
            color: '#3fb950',
            border: '1px solid rgba(46, 160, 67, 0.4)',
            fontWeight: 500,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#3fb950' },
          }}
        />
      );
    case 'MERGED':
      return (
        <Chip
          icon={<MergeTypeIcon fontSize="small" />}
          label="Merged"
          size={size}
          sx={{
            backgroundColor: 'rgba(163, 113, 247, 0.15)',
            color: '#a371f7',
            border: '1px solid rgba(163, 113, 247, 0.4)',
            fontWeight: 500,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#a371f7' },
          }}
        />
      );
    case 'CLOSED':
    default:
      return (
        <Chip
          icon={<CheckCircleOutlineIcon fontSize="small" />}
          label="Closed"
          size={size}
          sx={{
            backgroundColor: 'rgba(139, 148, 158, 0.15)',
            color: '#8b949e',
            border: '1px solid rgba(139, 148, 158, 0.3)',
            fontWeight: 500,
            borderRadius: 1,
            '& .MuiChip-icon': { color: '#8b949e' },
          }}
        />
      );
  }
};

export default StatusChip;
