import { Box, useTheme } from '@mui/material';
import type { components } from '../api/schema';
import { Chip, type ChipTone } from './primitives';

export type MappingOrigin = components['schemas']['MappingOrigin'];

/** Quem escreveu a definição prospectiva. */
const ORIGINS: Record<NonNullable<MappingOrigin>, { label: string; tone: ChipTone; meaning: string }> = {
  manual: {
    label: 'Instrumentação manual',
    tone: 'neutral',
    meaning: 'O script declara que a instrumentação foi escrita à mão.',
  },
  generated: {
    label: 'Gerado por ferramenta',
    tone: 'warn',
    meaning: 'Mapeamento gerado por uma ferramenta e usado sem nenhuma alteração.',
  },
  curated: {
    label: 'Curado',
    tone: 'aqua',
    meaning: 'Mapeamento gerado por uma ferramenta e revisado por uma pessoa.',
  },
};

export function originMeaning(origin: MappingOrigin): string {
  return origin
    ? ORIGINS[origin].meaning
    : 'A definição não declara quem a escreveu (versões anteriores a este registro, ou scripts que não informam).';
}

export function OriginChip({ origin, generator }: { origin: MappingOrigin; generator?: string | null }) {
  const { studio } = useTheme();
  const title = [originMeaning(origin), generator ? `Gerador: ${generator}` : null].filter(Boolean).join(' ');

  if (!origin) {
    return (
      <Box component="span" title={title} sx={{ fontSize: 11.5, color: studio.muted }}>
        não informada
      </Box>
    );
  }
  return (
    <Box component="span" title={title} sx={{ display: 'inline-flex' }}>
      <Chip tone={ORIGINS[origin].tone}>{ORIGINS[origin].label}</Chip>
    </Box>
  );
}
