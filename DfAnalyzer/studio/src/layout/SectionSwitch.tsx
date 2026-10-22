import { useMediaQuery, useTheme } from '@mui/material';
import { Segmented } from '../components/primitives';

export type DataflowSection = 'graph' | 'query' | 'analytics' | 'gallery';

const OPTIONS: { value: DataflowSection; label: string }[] = [
  { value: 'graph', label: 'Grafo' },
  { value: 'query', label: 'Consulta' },
  { value: 'analytics', label: 'Analytics' },
  { value: 'gallery', label: 'Galeria' },
];

/** Alterna entre as telas de um dataflow escolhido. */
export function SectionSwitch({
  section,
  onNavigate,
}: {
  section: DataflowSection;
  onNavigate: (section: DataflowSection) => void;
}) {
  const theme = useTheme();
  // Em tela estreita a barra inferior já faz esta troca.
  const compact = useMediaQuery(theme.breakpoints.down('sm'));
  if (compact) return null;
  return (
    <Segmented ariaLabel="Trocar de tela" value={section} onChange={onNavigate} options={OPTIONS} />
  );
}
