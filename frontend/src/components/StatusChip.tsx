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
            backgroundColor: 'rgba(16, 185, 129, 0.12)',
            color: '#10B981',
            border: '1px solid rgba(16, 185, 129, 0.3)',
            fontWeight: 600,
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
            backgroundColor: 'rgba(139, 92, 246, 0.15)',
            color: '#A78BFA',
            border: '1px solid rgba(139, 92, 246, 0.35)',
            fontWeight: 600,
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
            backgroundColor: 'rgba(156, 163, 175, 0.12)',
            color: '#9CA3AF',
            border: '1px solid rgba(156, 163, 175, 0.25)',
            fontWeight: 600,
          }}
        />
      );
  }
};

export default StatusChip;
