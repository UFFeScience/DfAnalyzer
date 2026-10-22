import { CssBaseline, ThemeProvider } from '@mui/material';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { buildTheme, type ColorMode } from './index';

interface ColorModeValue {
  mode: ColorMode;
  toggle: () => void;
}

const ColorModeContext = createContext<ColorModeValue | null>(null);

const STORAGE_KEY = 'dfa-studio.color-mode';

function readStoredMode(): ColorMode | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : null;
  } catch {
    // Janela privada ou dados bloqueados lançam exceção.
    return null;
  }
}

function preferredMode(): ColorMode {
  return readStoredMode()
    ?? (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
}

export function ColorModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ColorMode>(preferredMode);

  const toggle = useCallback(() => {
    setMode((current) => {
      const next: ColorMode = current === 'dark' ? 'light' : 'dark';
      try {
        window.localStorage.setItem(STORAGE_KEY, next);
      } catch {
      }
      return next;
    });
  }, []);

  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);
  const theme = useMemo(() => buildTheme(mode), [mode]);

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}

export function useColorMode(): ColorModeValue {
  const value = useContext(ColorModeContext);
  if (!value) {
    throw new Error('useColorMode precisa estar dentro de <ColorModeProvider>');
  }
  return value;
}
