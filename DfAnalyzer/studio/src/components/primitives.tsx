import { Box, useTheme } from '@mui/material';
import type { ReactNode } from 'react';
import { MONO } from '../theme/tokens';


export function StudioCard({ children, sx }: { children: ReactNode; sx?: object }) {
  const { studio } = useTheme();
  return (
    <Box
      sx={{
        bgcolor: studio.surface,
        border: `1px solid ${studio.border}`,
        borderRadius: '12px',
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        ...sx,
      }}
    >
      {children}
    </Box>
  );
}

export function Tile({
  label,
  value,
  suffix,
  footer,
}: {
  label: string;
  value: ReactNode;
  suffix?: string;
  footer?: ReactNode;
}) {
  const { studio } = useTheme();
  return (
    <Box
      sx={{
        bgcolor: studio.surface,
        border: `1px solid ${studio.border}`,
        borderRadius: '12px',
        px: 1.875,
        py: 1.625,
        justifyContent: 'flex-start',
        display: 'flex',
        flexDirection: 'column',
        gap: 0.625,
      }}
    >
      <Box
        sx={{ fontSize: 11.5, color: studio.muted, fontWeight: 500, letterSpacing: '.02em', lineHeight: 1.2 }}
      >
        {label}
      </Box>
      <Box
        sx={{
          fontSize: 24,
          fontWeight: 600,
          lineHeight: 1.15,
          letterSpacing: '-.02em',
          fontVariantNumeric: 'tabular-nums',
          display: 'flex',
          alignItems: 'baseline',
          gap: 0.75,
        }}
      >
        {value}
        {suffix && (
          <Box
            component="span"
            sx={{ fontSize: 12, color: studio.muted, fontWeight: 400, lineHeight: 1 }}
          >
            {suffix}
          </Box>
        )}
      </Box>
      {footer}
    </Box>
  );
}

export type ChipTone = 'neutral' | 'blue' | 'aqua' | 'warn' | 'good' | 'crit';

export function Chip({
  tone = 'neutral',
  icon,
  children,
}: {
  tone?: ChipTone;
  icon?: ReactNode;
  children: ReactNode;
}) {
  const { studio } = useTheme();

  const tones: Record<ChipTone, { fg: string; bd: string; bg: string }> = {
    neutral: { fg: studio.muted, bd: studio.border, bg: studio.elev },
    blue: { fg: studio.blue, bd: studio.blue, bg: studio.blueDim },
    aqua: { fg: studio.aqua, bd: studio.aqua, bg: studio.aquaDim },
    warn: { fg: studio.warn, bd: studio.warn, bg: 'transparent' },
    good: { fg: studio.good, bd: studio.good, bg: 'transparent' },
    crit: { fg: studio.crit, bd: studio.crit, bg: 'transparent' },
  };
  const { fg, bd, bg } = tones[tone];

  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.625,
        height: 22,
        px: 1,
        borderRadius: '6px',
        fontSize: 11,
        fontWeight: 500,
        whiteSpace: 'nowrap',
        color: fg,
        border: `1px solid ${bd}`,
        bgcolor: bg,
      }}
    >
      {icon}
      {children}
    </Box>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel: string;
}) {
  const { studio } = useTheme();
  return (
    <Box
      role="tablist"
      aria-label={ariaLabel}
      sx={{
        display: 'flex',
        bgcolor: studio.page,
        border: `1px solid ${studio.border}`,
        borderRadius: '9px',
        p: '2px',
        gap: '2px',
      }}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Box
            key={option.value}
            component="button"
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(option.value)}
            sx={{
              height: 28,
              px: 1.5,
              border: 0,
              borderRadius: '7px',
              fontSize: 12.5,
              fontWeight: 500,
              fontFamily: 'inherit',
              cursor: 'pointer',
              bgcolor: selected ? studio.elev : 'transparent',
              color: selected ? studio.ink : studio.muted,
              boxShadow: selected ? studio.shadow : 'none',
              '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
            }}
          >
            {option.label}
          </Box>
        );
      })}
    </Box>
  );
}

export function Mono({ children, sx }: { children: ReactNode; sx?: object }) {
  return (
    <Box component="span" sx={{ fontFamily: MONO, ...sx }}>
      {children}
    </Box>
  );
}
