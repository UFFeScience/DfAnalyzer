import { Box, Stack, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import type { ComponentType, ReactNode, SVGProps } from 'react';
import { useColorMode } from '../theme/ColorModeContext';
import { MONO } from '../theme/tokens';
import {
  AnalyticsIcon,
  DataflowsIcon,
  GalleryIcon,
  GraphIcon,
  LogoMark,
  MoonIcon,
  QueryIcon,
  SearchIcon,
  SunIcon,
} from './icons';

type NavIcon = ComponentType<SVGProps<SVGSVGElement> & { size?: number }>;

interface NavEntry {
  key: string;
  label: string;
  /** Rótulo da barra inferior em tela estreita. */
  short: string;
  icon: NavIcon;
}

const NAV: NavEntry[] = [
  { key: 'dataflows', label: 'Dataflows', short: 'Fluxos', icon: DataflowsIcon },
  { key: 'graph', label: 'Grafo', short: 'Grafo', icon: GraphIcon },
  { key: 'query', label: 'Consulta', short: 'Consulta', icon: QueryIcon },
  { key: 'analytics', label: 'Analytics', short: 'Analytics', icon: AnalyticsIcon },
  { key: 'gallery', label: 'Galeria', short: 'Galeria', icon: GalleryIcon },
];

export function AppShell({
  title,
  section,
  hasSelection,
  onNavigate,
  onOpenSearch,
  children,
}: {
  title: string;
  section: string;
  hasSelection: boolean;
  onNavigate: (section: string) => void;
  onOpenSearch?: () => void;
  children: ReactNode;
}) {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('sm'));
  const { studio } = theme;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: compact ? 'column' : 'row',
        height: '100dvh',
        overflow: 'hidden',
        bgcolor: studio.page,
        color: studio.ink,
      }}
    >
      {!compact && <Rail compact={false} section={section} hasSelection={hasSelection} onNavigate={onNavigate} />}

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: 0 }}>
        <TopBar title={title} compact={compact} onOpenSearch={onOpenSearch} />
        <Box
          component="main"
          sx={{
            flexGrow: 1,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            px: { xs: 2, sm: 2.75 },
            py: { xs: 2, sm: 2.5 },
            overflowY: 'auto',
            '& > *': { flexShrink: 0 },
          }}
        >
          {children}
        </Box>
      </Box>

      {compact && <Rail compact section={section} hasSelection={hasSelection} onNavigate={onNavigate} />}
    </Box>
  );
}

function Rail({
  compact,
  section,
  hasSelection,
  onNavigate,
}: {
  compact: boolean;
  section: string;
  hasSelection: boolean;
  onNavigate: (section: string) => void;
}) {
  const { studio } = useTheme();

  return (
    <Box
      component="nav"
      aria-label="Seções"
      sx={{
        flex: 'none',
        bgcolor: studio.surface,
        display: 'flex',
        ...(compact
          ? {
              flexDirection: 'row',
              alignItems: 'stretch',
              px: 0.5,
              height: 58,
              borderTop: `1px solid ${studio.border}`,
              pb: 'env(safe-area-inset-bottom, 0px)',
            }
          : {
              width: 64,
              flexDirection: 'column',
              alignItems: 'center',
              gap: 0.5,
              py: 1.75,
              borderRight: `1px solid ${studio.border}`,
            }),
      }}
    >
      {!compact && <Box
        sx={{
          width: 34,
          height: 34,
          flex: 'none',
          borderRadius: '9px',
          bgcolor: studio.blue,
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          mb: compact ? 0 : 1.75,
          mr: compact ? 0.5 : 0,
        }}
      >
        <LogoMark size={19} />
      </Box>}

      {NAV.map((entry) => (
        <RailButton
          key={entry.key}
          compact={compact}
          entry={entry}
          active={entry.key === section}
          hasSelection={hasSelection}
          onNavigate={onNavigate}
        />
      ))}
    </Box>
  );
}

function RailButton({
  entry,
  compact,
  active,
  hasSelection,
  onNavigate,
}: {
  entry: NavEntry;
  compact: boolean;
  active: boolean;
  hasSelection: boolean;
  onNavigate: (section: string) => void;
}) {
  const { studio } = useTheme();
  const Glyph = entry.icon;
  // Grafo, consulta, analytics e galeria só fazem sentido com um dataflow escolhido.
  const requiresSelection =
    entry.key === 'graph' || entry.key === 'query' || entry.key === 'analytics' || entry.key === 'gallery';
  const disabled =
    (requiresSelection && !hasSelection) || active;

  return (
    <Tooltip
      title={
        active
          ? entry.label
          : requiresSelection && !hasSelection
            ? `${entry.label} · escolha um dataflow na lista`
            : entry.label
      }
      placement="right"
    >
      <Box
        component="button"
        type="button"
        disabled={disabled}
        onClick={() => onNavigate(entry.key)}
        aria-label={entry.label}
        aria-current={active ? 'page' : undefined}
        sx={{
          ...(compact
            ? { flex: '1 1 0', minWidth: 0, height: '100%', flexDirection: 'column', gap: 0.375, borderRadius: 0 }
            : { width: 44, height: 44, flex: 'none', borderRadius: '10px' }),
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          border: '1px solid transparent',
          fontFamily: 'inherit',
          fontSize: 10,
          fontWeight: 500,
          bgcolor: active && !compact ? studio.blueDim : 'transparent',
          color: active ? studio.blue : studio.muted,
          borderColor: active && !compact ? studio.blue : 'transparent',
          cursor: disabled ? 'default' : 'pointer',
          opacity: disabled && !active ? 0.45 : 1,
          transition: 'background-color .12s, color .12s',
          '&:hover:not(:disabled)': { color: studio.ink },
          '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
        }}
      >
        <Glyph size={20} />
        {compact && (
          <Box component="span" sx={{ maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {entry.short}
          </Box>
        )}
      </Box>
    </Tooltip>
  );
}

function TopBar({
  title,
  compact,
  onOpenSearch,
}: {
  title: string;
  compact: boolean;
  onOpenSearch?: () => void;
}) {
  const { studio } = useTheme();
  const { mode, toggle } = useColorMode();

  return (
    <Box
      component="header"
      sx={{
        height: 56,
        flex: 'none',
        display: 'flex',
        alignItems: 'center',
        gap: 1.75,
        px: { xs: 1.5, sm: 2.25 },
        bgcolor: studio.surface,
        borderBottom: `1px solid ${studio.border}`,
      }}
    >
      <Typography sx={{ fontSize: 13, fontWeight: 600 }}>{title}</Typography>

      {!compact && (
        <Box
          component="button"
          type="button"
          onClick={onOpenSearch}
          sx={{
            ml: 'auto',
            width: 450,
            height: 34,
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            px: 1.25,
            borderRadius: '9px',
            border: `1px solid ${studio.border}`,
            bgcolor: studio.page,
            color: studio.muted,
            fontSize: 13,
            fontFamily: 'inherit',
            cursor: 'pointer',
            '&:hover': { borderColor: studio.blue },
            '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
          }}
        >
          <SearchIcon size={15} />
          <Box component="span">Buscar dataflow, transformação, dataset, atributo</Box>
          <Box
            component="span"
            sx={{
              ml: 'auto',
              fontSize: 11,
              border: `1px solid ${studio.border}`,
              borderRadius: '5px',
              px: 0.6,
              fontFamily: MONO,
            }}
          >
            Ctrl + K
          </Box>
        </Box>
      )}

      <Stack direction="row" spacing={1} alignItems="center" sx={{ ml: compact ? 'auto' : 0 }}>
        <Box
          component="button"
          type="button"
          onClick={toggle}
          aria-label={mode === 'dark' ? 'Mudar para tema claro' : 'Mudar para tema escuro'}
          sx={{
            width: 34,
            height: 34,
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '1px solid transparent',
            bgcolor: 'transparent',
            color: studio.muted,
            cursor: 'pointer',
            '&:hover': { color: studio.ink },
            '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
          }}
        >
          {mode === 'dark' ? <SunIcon size={15} /> : <MoonIcon size={15} />}
        </Box>
      </Stack>
    </Box>
  );
}
