import { createTheme, type Theme } from '@mui/material/styles';
import { MONO, SANS, darkPalette, lightPalette, type StudioPalette } from './tokens';

export type ColorMode = 'light' | 'dark';

declare module '@mui/material/styles' {
  interface Theme {
    studio: StudioPalette;
  }
  interface ThemeOptions {
    studio?: StudioPalette;
  }
}

export function buildTheme(mode: ColorMode): Theme {
  const studio = mode === 'dark' ? darkPalette : lightPalette;

  return createTheme({
    studio,
    palette: {
      mode,
      background: { default: studio.page, paper: studio.surface },
      text: { primary: studio.ink, secondary: studio.muted },
      primary: { main: studio.blue },
      secondary: { main: studio.aqua },
      success: { main: studio.good },
      warning: { main: studio.warn },
      error: { main: studio.crit },
      divider: studio.border,
    },
    shape: { borderRadius: 6 },
    typography: {
      fontFamily: SANS,
      h1: { fontSize: '1.5rem', fontWeight: 600, letterSpacing: '-0.01em' },
      h2: { fontSize: '1.125rem', fontWeight: 600, letterSpacing: '-0.01em' },
      h3: { fontSize: '0.9375rem', fontWeight: 600 },
      body2: { fontSize: '0.8125rem' },
      caption: { fontSize: '0.75rem', color: studio.muted },
      button: { textTransform: 'none', fontWeight: 500 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { backgroundColor: studio.page, color: studio.ink },
          code: { fontFamily: MONO },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: { backgroundImage: 'none', border: `1px solid ${studio.border}` },
        },
      },
      MuiAppBar: {
        defaultProps: { elevation: 0, color: 'transparent' },
        styleOverrides: {
          root: {
            backgroundColor: studio.surface,
            borderBottom: `1px solid ${studio.border}`,
          },
        },
      },
      MuiButton: { defaultProps: { disableElevation: true } },
      MuiTableCell: {
        styleOverrides: {
          root: { borderColor: studio.border },
          head: { fontWeight: 600, color: studio.muted, fontSize: '0.75rem' },
        },
      },
    },
  });
}
