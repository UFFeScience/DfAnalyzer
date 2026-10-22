import { Box, Stack, useTheme } from '@mui/material';
import type { GraphNode, TimelineRow } from '../api/catalog';
import { Chip } from '../components/primitives';
import { ChevronIcon, DataflowsIcon, GraphIcon } from '../layout/icons';
import { MONO } from '../theme/tokens';

/** Os nós do grafo em lista vertical, para telas estreitas. */
export function NodeList({
  nodes,
  timeline,
  runningTags,
  selectedNodeId,
  onSelectNode,
}: {
  nodes: GraphNode[];
  /** Vazio quando não há execução. */
  timeline: TimelineRow[];
  runningTags: ReadonlySet<string>;
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
}) {
  const { studio } = useTheme();
  const byTag = new Map(timeline.map((row) => [row.transformationTag.toLowerCase(), row]));

  return (
    <Box
      component="ul"
      aria-label="Nós do grafo"
      sx={{ listStyle: 'none', m: 0, p: 1.25, display: 'flex', flexDirection: 'column', gap: 1, flexGrow: 1 }}
    >
      {nodes.map((node) => {
        const isTransformation = node.kind === 'TRANSFORMATION';
        const row = isTransformation ? byTag.get(node.tag.toLowerCase()) : undefined;
        const selected = node.id === selectedNodeId;
        return (
          <Box component="li" key={node.id}>
            <Box
              component="button"
              type="button"
              aria-pressed={selected}
              onClick={() => onSelectNode(selected ? null : node.id)}
              sx={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: 1.25,
                px: 1.5,
                py: 1.25,
                textAlign: 'left',
                fontFamily: 'inherit',
                color: studio.ink,
                bgcolor: selected ? studio.blueDim : studio.elev,
                border: `1px solid ${selected ? studio.blue : studio.border}`,
                borderRadius: '11px',
                cursor: 'pointer',
                '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
              }}
            >
              <Box
                sx={{
                  width: 30,
                  height: 30,
                  flex: 'none',
                  borderRadius: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: isTransformation ? studio.blue : studio.aqua,
                  bgcolor: isTransformation ? studio.blueDim : studio.aquaDim,
                }}
              >
                {isTransformation ? <GraphIcon size={16} /> : <DataflowsIcon size={16} />}
              </Box>
              <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                <Box
                  sx={{ fontFamily: MONO, fontSize: 13, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                >
                  {node.tag}
                </Box>
                <Box sx={{ mt: 0.25, fontSize: 11.5, color: studio.muted }}>{nodeSummary(node, row)}</Box>
              </Box>
              <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flex: 'none', color: studio.muted }}>
                {isTransformation && runningTags.has(node.tag) && <Chip tone="warn">em execução</Chip>}
                {row && row.errorTaskCount > 0 && <Chip tone="crit">{row.errorTaskCount} com erro</Chip>}
                <ChevronIcon size={15} />
              </Stack>
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}

export function nodeSummary(node: GraphNode, row: TimelineRow | undefined): string {
  if (node.kind === 'TRANSFORMATION') {
    if (!row) return 'Transformação · sem execução registrada';
    const tasks = `${row.taskCount} task${row.taskCount === 1 ? '' : 's'}`;
    return `${tasks} · ${formatSeconds(row.durationSeconds)}`;
  }
  const attributes = node.attributes?.length ?? 0;
  const role =
    node.role === 'SOURCE' ? 'entrada' : node.role === 'SINK' ? 'saída' : node.role === 'INTERMEDIATE' ? 'intermediário' : 'dataset';
  return `${role} · ${attributes} atributo${attributes === 1 ? '' : 's'}`;
}

function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${String(Math.round(seconds % 60)).padStart(2, '0')}s`;
}
