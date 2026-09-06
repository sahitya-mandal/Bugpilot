import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#2f81f7', // Restrained technical blue (GitHub/Linear style)
      light: '#58a6ff',
      dark: '#1f6feb',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#7d8590', // Neutral graphite
      light: '#c9d1d9',
      dark: '#484f58',
      contrastText: '#ffffff',
    },
    background: {
      default: '#0d1117', // Deep matte dark
      paper: '#161b22',   // Secondary dark surface
    },
    text: {
      primary: '#f0f6fc',
      secondary: '#8b949e',
    },
    error: {
      main: '#f85149',
      light: '#ff7b72',
      dark: '#da3633',
    },
    warning: {
      main: '#d29922',
      light: '#e3b341',
      dark: '#9e6a03',
    },
    info: {
      main: '#58a6ff',
      light: '#79c0ff',
      dark: '#1f6feb',
    },
    success: {
      main: '#2ea043',
      light: '#3fb950',
      dark: '#238636',
    },
    divider: '#30363d',
  },
  typography: {
    fontFamily: [
      'Inter',
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      'sans-serif',
    ].join(','),
    h4: {
      fontWeight: 650,
      letterSpacing: '-0.02em',
      color: '#f0f6fc',
    },
    h5: {
      fontWeight: 600,
      letterSpacing: '-0.01em',
      color: '#f0f6fc',
    },
    h6: {
      fontWeight: 600,
      color: '#f0f6fc',
    },
    subtitle1: {
      fontSize: '0.925rem',
      fontWeight: 500,
      color: '#8b949e',
    },
    subtitle2: {
      fontSize: '0.8125rem',
      fontWeight: 500,
      color: '#8b949e',
    },
    body1: {
      fontSize: '0.875rem',
      color: '#f0f6fc',
    },
    body2: {
      fontSize: '0.8125rem',
      color: '#8b949e',
    },
    caption: {
      fontSize: '0.75rem',
      color: '#8b949e',
    },
    button: {
      textTransform: 'none',
      fontWeight: 500,
      fontSize: '0.8125rem',
    },
  },
  shape: {
    borderRadius: 6,
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: '#0d1117',
          color: '#f0f6fc',
          scrollbarColor: '#30363d #0d1117',
          '&::-webkit-scrollbar, & *::-webkit-scrollbar': {
            backgroundColor: '#0d1117',
            width: 7,
            height: 7,
          },
          '&::-webkit-scrollbar-thumb, & *::-webkit-scrollbar-thumb': {
            borderRadius: 4,
            backgroundColor: '#30363d',
          },
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 6,
          padding: '6px 14px',
          textTransform: 'none',
          fontWeight: 500,
          fontSize: '0.8125rem',
        },
        contained: {
          boxShadow: 'none',
          '&:hover': {
            boxShadow: 'none',
          },
          '&.MuiButton-containedPrimary': {
            backgroundColor: '#2f81f7',
            color: '#ffffff',
            '&:hover': {
              backgroundColor: '#388bfd',
              boxShadow: 'none',
            },
          },
        },
        outlined: {
          borderColor: '#30363d',
          color: '#f0f6fc',
          '&:hover': {
            borderColor: '#8b949e',
            backgroundColor: 'rgba(255, 255, 255, 0.04)',
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          backgroundColor: '#161b22',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundColor: '#161b22',
          border: '1px solid #30363d',
          borderRadius: 6,
          boxShadow: 'none',
          backgroundImage: 'none',
        },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          backgroundColor: '#161b22',
          border: '1px solid #30363d',
          borderRadius: 6,
          boxShadow: '0 16px 32px rgba(1, 4, 9, 0.85)',
          backgroundImage: 'none',
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 6,
          backgroundColor: '#0d1117',
          '& fieldset': {
            borderColor: '#30363d',
          },
          '&:hover fieldset': {
            borderColor: '#8b949e',
          },
          '&.Mui-focused fieldset': {
            borderColor: '#58a6ff',
            borderWidth: '1px',
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 500,
          borderRadius: 4,
          height: 24,
          fontSize: '0.75rem',
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: '1px solid #21262d',
          padding: '10px 14px',
          fontSize: '0.8125rem',
        },
        head: {
          color: '#8b949e',
          fontWeight: 600,
          backgroundColor: '#161b22',
          borderBottom: '1px solid #30363d',
          textTransform: 'uppercase',
          fontSize: '0.72rem',
          letterSpacing: '0.04em',
        },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          '&:hover': {
            backgroundColor: 'rgba(255, 255, 255, 0.02) !important',
          },
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        indicator: {
          backgroundColor: '#2f81f7',
          height: 2,
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 500,
          fontSize: '0.875rem',
          minHeight: 44,
          color: '#8b949e',
          '&.Mui-selected': {
            color: '#f0f6fc',
          },
        },
      },
    },
  },
});

export default theme;
