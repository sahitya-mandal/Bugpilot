import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  List,
  Typography,
  Divider,
  IconButton,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Avatar,
  Chip,
  Button,
  Menu,
  MenuItem,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardIcon from '@mui/icons-material/DashboardOutlined';
import FolderSpecialIcon from '@mui/icons-material/FolderSpecialOutlined';
import BugReportIcon from '@mui/icons-material/BugReportOutlined';
import MergeTypeIcon from '@mui/icons-material/MergeTypeOutlined';
import BarChartIcon from '@mui/icons-material/BarChartOutlined';
import HistoryIcon from '@mui/icons-material/HistoryOutlined';
import LogoutIcon from '@mui/icons-material/LogoutOutlined';
import AddIcon from '@mui/icons-material/Add';
import FlightTakeoffIcon from '@mui/icons-material/FlightTakeoff';
import CircleIcon from '@mui/icons-material/Circle';
import { useAuth } from '../context/AuthContext';
import ImportRepoDialog from '../components/ImportRepoDialog';
import type { Repository } from '../types';

const DRAWER_WIDTH = 260;

export const MainLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const [mobileOpen, setMobileOpen] = useState<boolean>(false);
  const [userMenuAnchor, setUserMenuAnchor] = useState<null | HTMLElement>(null);
  const [importDialogOpen, setImportDialogOpen] = useState<boolean>(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const navItems = [
    { text: 'Dashboard', path: '/dashboard', icon: <DashboardIcon /> },
    { text: 'Repositories', path: '/repositories', icon: <FolderSpecialIcon /> },
    { text: 'Issues & AI Triage', path: '/issues', icon: <BugReportIcon /> },
    { text: 'Pull Requests & Review', path: '/pull-requests', icon: <MergeTypeIcon /> },
    { text: 'Analytics', path: '/analytics', icon: <BarChartIcon /> },
    { text: 'Activity Timeline', path: '/activity', icon: <HistoryIcon /> },
  ];

  const handleLogout = () => {
    setUserMenuAnchor(null);
    logout();
    navigate('/login');
  };

  const handleRepoImported = (repo: Repository) => {
    navigate(`/repositories/${repo.id}`);
  };

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: 'background.paper' }}>
      {/* Brand Header */}
      <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Avatar
          sx={{
            bgcolor: '#21262d',
            color: 'text.primary',
            width: 36,
            height: 36,
            border: '1px solid #30363d',
          }}
        >
          <FlightTakeoffIcon fontSize="small" />
        </Avatar>
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1 }}>
              BugPilot
            </Typography>
            <Chip
              label="AI"
              size="small"
              sx={{
                height: 18,
                fontSize: '0.65rem',
                fontWeight: 600,
                bgcolor: 'rgba(56, 139, 253, 0.1)',
                color: 'primary.light',
                border: '1px solid rgba(56, 139, 253, 0.25)',
              }}
            />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.7rem' }}>
            Engineering Intelligence
          </Typography>
        </Box>
      </Box>

      <Divider />

      {/* Nav List */}
      <List sx={{ px: 1.5, py: 2, flex: 1 }}>
        {navItems.map((item) => {
          const isSelected =
            location.pathname === item.path ||
            (item.path !== '/dashboard' && location.pathname.startsWith(item.path));

          return (
            <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                onClick={() => {
                  navigate(item.path);
                  if (isMobile) setMobileOpen(false);
                }}
                selected={isSelected}
                sx={{
                  borderRadius: 1.5,
                  px: 2,
                  py: 1.1,
                  '&.Mui-selected': {
                    bgcolor: 'rgba(56, 139, 253, 0.1)',
                    color: 'primary.light',
                    fontWeight: 600,
                    '& .MuiListItemIcon-root': {
                      color: 'primary.light',
                    },
                    '&:hover': {
                      bgcolor: 'rgba(56, 139, 253, 0.15)',
                    },
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 36,
                    color: isSelected ? 'primary.light' : 'text.secondary',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Typography sx={{ fontSize: '0.875rem', fontWeight: isSelected ? 600 : 500 }}>
                      {item.text}
                    </Typography>
                  }
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      <Divider />

      {/* Quick Import Action */}
      <Box sx={{ p: 2 }}>
        <Button
          fullWidth
          variant="outlined"
          color="inherit"
          startIcon={<AddIcon />}
          onClick={() => {
            setImportDialogOpen(true);
            if (isMobile) setMobileOpen(false);
          }}
          sx={{ borderRadius: 1.5, borderColor: '#30363d', color: 'text.primary' }}
        >
          Import Repository
        </Button>
      </Box>
    </Box>
  );

  const getRoleChipColor = (role?: string) => {
    switch (role) {
      case 'ADMIN':
        return { bg: 'rgba(248, 81, 73, 0.15)', color: '#f85149', border: '1px solid rgba(248, 81, 73, 0.3)' };
      case 'TESTER':
        return { bg: 'rgba(210, 153, 34, 0.15)', color: '#d29922', border: '1px solid rgba(210, 153, 34, 0.3)' };
      case 'DEVELOPER':
      default:
        return { bg: 'rgba(56, 139, 253, 0.15)', color: '#58a6ff', border: '1px solid rgba(56, 139, 253, 0.3)' };
    }
  };

  const roleStyle = getRoleChipColor(user?.role);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* Top AppBar */}
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          bgcolor: 'background.paper',
          borderBottom: '1px solid #30363d',
          color: 'text.primary',
        }}
      >
        <Toolbar sx={{ justifyContent: 'space-between', px: { xs: 2, sm: 3 } }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <IconButton
              color="inherit"
              edge="start"
              onClick={handleDrawerToggle}
              sx={{ display: { md: 'none' }, mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CircleIcon sx={{ fontSize: 9, color: '#2ea043' }} />
              <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 500 }}>
                Spring Boot API Connected
              </Typography>
            </Box>
          </Box>

          {/* User Profile & Actions */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Box
              onClick={(e) => setUserMenuAnchor(e.currentTarget)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                cursor: 'pointer',
                p: 0.75,
                borderRadius: 2,
                '&:hover': { bgcolor: 'rgba(255, 255, 255, 0.04)' },
              }}
            >
              <Avatar
                sx={{
                  width: 34,
                  height: 34,
                  bgcolor: 'primary.dark',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                }}
              >
                {user?.email?.charAt(0).toUpperCase() || 'U'}
              </Avatar>
              <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'left' }}>
                <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
                  {user?.name || user?.email}
                </Typography>
                <Chip
                  label={user?.role || 'DEVELOPER'}
                  size="small"
                  sx={{
                    height: 18,
                    fontSize: '0.65rem',
                    fontWeight: 700,
                    bgcolor: roleStyle.bg,
                    color: roleStyle.color,
                    mt: 0.25,
                  }}
                />
              </Box>
            </Box>

            <Menu
              anchorEl={userMenuAnchor}
              open={Boolean(userMenuAnchor)}
              onClose={() => setUserMenuAnchor(null)}
              transformOrigin={{ horizontal: 'right', vertical: 'top' }}
              anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
              slotProps={{
                paper: {
                  sx: {
                    minWidth: 200,
                    mt: 1,
                    bgcolor: 'background.paper',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                  },
                },
              }}
            >
              <Box sx={{ px: 2, py: 1.5 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {user?.name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {user?.email}
                </Typography>
              </Box>
              <Divider />
              <MenuItem onClick={handleLogout} sx={{ color: 'error.light', py: 1.2 }}>
                <ListItemIcon sx={{ color: 'error.light', minWidth: 32 }}>
                  <LogoutIcon fontSize="small" />
                </ListItemIcon>
                <ListItemText primary="Sign Out" />
              </MenuItem>
            </Menu>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Sidebar Drawer */}
      <Box
        component="nav"
        sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}
      >
        {/* Mobile Drawer */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>

        {/* Desktop Drawer */}
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: DRAWER_WIDTH,
              borderRight: '1px solid rgba(255, 255, 255, 0.08)',
            },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3, md: 4 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: '64px',
          minHeight: 'calc(100vh - 64px)',
          overflowY: 'auto',
        }}
      >
        <Outlet />
      </Box>

      {/* Import Modal Available Everywhere */}
      <ImportRepoDialog
        open={importDialogOpen}
        onClose={() => setImportDialogOpen(false)}
        onSuccess={handleRepoImported}
      />
    </Box>
  );
};

export default MainLayout;
