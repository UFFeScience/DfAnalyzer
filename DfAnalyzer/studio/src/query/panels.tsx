import { Box, useTheme } from '@mui/material';
import type { ChangeEvent, ReactNode } from 'react';
import { MONO } from '../theme/tokens';


/** Painel lateral com as escolhas da consulta. */
export function SidePanel({ children }: { children: ReactNode }) {
  const { studio } = useTheme();
  return (
    <Box
      sx={{
        width: { xs: '100%', lg: 302 },
        flex: 'none',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        bgcolor: studio.surface,
        border: `1px solid ${studio.border}`,
        borderRadius: '12px',
      }}
    >
      {children}
    </Box>
  );
}

export function Section({ label, children }: { label: string; children: ReactNode }) {
  const { studio } = useTheme();
  return (
    <Box
      sx={{
        px: 1.875,
        py: 1.625,
        borderBottom: `1px solid ${studio.border}`,
        '&:last-of-type': { borderBottom: 0 },
      }}
    >
      <Box
        sx={{
          fontSize: 10.5,
          letterSpacing: '.06em',
          color: studio.muted,
          fontWeight: 600,
          mb: 1.125,
        }}
      >
        {label}
      </Box>
      {children}
    </Box>
  );
}

export function CheckItem({
  label,
  checked,
  onToggle,
  hint,
}: {
  label: string;
  checked: boolean;
  onToggle: () => void;
  hint?: string;
}) {
  const { studio } = useTheme();
  return (
    <Box
      component="button"
      type="button"
      onClick={onToggle}
      role="checkbox"
      aria-checked={checked}
      title={hint}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.125,
        width: '100%',
        py: 0.625,
        px: 0,
        border: 0,
        bgcolor: 'transparent',
        textAlign: 'left',
        cursor: 'pointer',
        fontFamily: MONO,
        fontSize: 11.5,
        color: checked ? studio.ink : studio.muted,
        '&:hover': { color: studio.ink },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      <Box
        sx={{
          width: 15,
          height: 15,
          flex: 'none',
          borderRadius: '4px',
          border: `1.5px solid ${checked ? studio.blue : studio.border}`,
          bgcolor: checked ? studio.blue : 'transparent',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {checked && (
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor"
            strokeWidth={3.2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="m4 12.5 5 5L20 6.5" />
          </svg>
        )}
      </Box>
      <Box sx={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {label}
      </Box>
    </Box>
  );
}

export function MiniPill({
  children,
  as,
  ...rest
}: {
  children?: ReactNode;
  as?: 'select' | 'input';
  value?: string;
  onChange?: (value: string) => void;
  ariaLabel?: string;
  grow?: number;
}) {
  const { studio } = useTheme();
  const base = {
    height: 28,
    minWidth: 0,
    border: `1px solid ${studio.border}`,
    borderRadius: '7px',
    bgcolor: studio.page,
    color: studio.ink,
    fontSize: 11.5,
    fontFamily: MONO,
    px: 1,
    display: 'flex',
    alignItems: 'center',
    gap: 0.625,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    flexGrow: rest.grow ?? 0,
  };

  if (as === 'select') {
    return (
      <Box
        component="select"
        value={rest.value}
        aria-label={rest.ariaLabel}
        onChange={(event: ChangeEvent<HTMLSelectElement>) => rest.onChange?.(event.target.value)}
        sx={{ ...base, '&:focus': { outline: 'none', borderColor: studio.blue } }}
      >
        {children}
      </Box>
    );
  }
  if (as === 'input') {
    return (
      <Box
        component="input"
        value={rest.value}
        aria-label={rest.ariaLabel}
        placeholder="valor"
        onChange={(event: ChangeEvent<HTMLInputElement>) => rest.onChange?.(event.target.value)}
        sx={{
          ...base,
          '&::placeholder': { color: studio.muted },
          '&:focus': { outline: 'none', borderColor: studio.blue },
        }}
      />
    );
  }
  return <Box sx={base}>{children}</Box>;
}

export function UnderlineTabs<T extends string>({
  value,
  onChange,
  options,
  ariaLabel,
}: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
  ariaLabel: string;
}) {
  const { studio } = useTheme();
  return (
    <Box role="tablist" aria-label={ariaLabel} sx={{ display: 'flex', gap: '2px' }}>
      {options.map((option) => {
        const on = option.value === value;
        return (
          <Box
            key={option.value}
            component="button"
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onChange(option.value)}
            sx={{
              height: 30,
              px: 1.5,
              border: 0,
              bgcolor: 'transparent',
              fontFamily: 'inherit',
              fontSize: 12,
              fontWeight: 500,
              cursor: 'pointer',
              color: on ? studio.ink : studio.muted,
              borderBottom: `2px solid ${on ? studio.blue : 'transparent'}`,
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

export function SqlBlock({ sql }: { sql: string }) {
  const { studio } = useTheme();
  const KEYWORDS = /\b(SELECT|FROM|WHERE|JOIN|ON|AND|OR|LIMIT|OFFSET|NOT|IN|BETWEEN|IS|NULL|LIKE|UNION|ORDER BY|GROUP BY)\b/g;

  const painted = sql.split('\n').map((line, index) => {
    if (line.trim().startsWith('--')) {
      return <Box key={index} component="span" sx={{ color: studio.muted }}>{line}{'\n'}</Box>;
    }
    const parts: ReactNode[] = [];
    let cursor = 0;
    // Literais primeiro, para não destacar palavra dentro de string.
    const pattern = new RegExp(`'[^']*'|\\b\\d+(?:\\.\\d+)?\\b|${KEYWORDS.source}`, 'g');
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(line)) !== null) {
      if (match.index > cursor) parts.push(line.slice(cursor, match.index));
      const token = match[0];
      const color = token.startsWith("'")
        ? studio.aqua
        : /^\d/.test(token)
          ? studio.orange
          : studio.blue;
      parts.push(
        <Box key={`${index}-${match.index}`} component="span"
          sx={{ color, fontWeight: color === studio.blue ? 500 : 400 }}>
          {token}
        </Box>,
      );
      cursor = match.index + token.length;
    }
    parts.push(line.slice(cursor), '\n');
    return <Box key={index} component="span">{parts}</Box>;
  });

  return (
    <Box
      component="pre"
      sx={{
        m: 0,
        px: 1.875,
        py: 1.625,
        bgcolor: studio.page,
        border: 0,
        fontFamily: MONO,
        fontSize: 12,
        lineHeight: 1.7,
        color: studio.muted,
        overflowX: 'auto',
        whiteSpace: 'pre',
      }}
    >
      {painted}
    </Box>
  );
}
