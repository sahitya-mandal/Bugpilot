import React from 'react';
import { Chip } from '@mui/material';
import ShieldAlertIcon from '@mui/icons-material/GppBadOutlined';
import ShieldWarningIcon from '@mui/icons-material/WarningAmber';
import ShieldCheckIcon from '@mui/icons-material/VerifiedUserOutlined';
import type { RiskLevel } from '../types';

interface RiskChipProps {
  riskLevel?: RiskLevel | string | null;
  size?: 'small' | 'medium';
}

export const RiskChip: React.FC<RiskChipProps> = ({ riskLevel, size = 'small' }) => {
  if (!riskLevel) {
    return <Chip label="Not Reviewed" size={size} variant="outlined" sx={{ color: 'text.secondary', borderColor: 'divider' }} />;
  }

  const riskUpper = riskLevel.toUpperCase();

  switch (riskUpper) {
    case 'CRITICAL':
      return (
        <Chip
          icon={<ShieldAlertIcon fontSize="small" />}
          label="RISK: CRITICAL"
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
          icon={<ShieldAlertIcon fontSize="small" />}
          label="RISK: HIGH"
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
          icon={<ShieldWarningIcon fontSize="small" />}
          label="RISK: MEDIUM"
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
          icon={<ShieldCheckIcon fontSize="small" />}
          label="RISK: LOW"
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

export default RiskChip;
