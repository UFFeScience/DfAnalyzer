import { keyframes } from '@emotion/react';
import { Box, useTheme } from '@mui/material';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { memo } from 'react';
import type { CollapsibleNode } from './collapse';
import { isGroup } from './collapse';
import { MONO } from '../theme/tokens';
import { DataflowsIcon, GraphIcon } from '../layout/icons';

/** Nó do grafo de proveniência. */
export interface ProvenanceNodeData extends Record<string, unknown> {
  node: CollapsibleNode;
  selected: boolean;
  inLineage: boolean;
  dimmed: boolean;
  lineageSide: 'upstream' | 'downstream' | 'focus' | null;
  /** Há task em RUNNING nesta transformação. */
  running: boolean;
}

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .45; transform: scale(1.3); }
`;

function ProvenanceNodeImpl({ data }: NodeProps) {
  const { studio } = useTheme();
  const { node, selected, dimmed, lineageSide, running } = data as ProvenanceNodeData;
  const isTransformation = node.kind === 'TRANSFORMATION';
  const grouped = isGroup(node);

  // A cor vem do tipo do nó, não da posição na lista.
  const accent = isTransformation
    ? studio.blue
    : node.role === 'SOURCE'
      ? studio.aqua
      : node.role === 'SINK'
        ? studio.orange
        : studio.muted;

  const accentDim = isTransformation
    ? studio.blueDim
    : node.role === 'SOURCE'
      ? studio.aquaDim
      : node.role === 'SINK'
        ? studio.orangeDim
        : 'transparent';

  const attributeCount = node.attributes?.length ?? 0;

  const subtitle = grouped
    ? node.group.sampleTags.join(', ') +
      (node.group.memberIds.length > node.group.sampleTags.length ? '…' : '')
    : isTransformation
      ? 'transformação'
      : `${attributeCount} ${attributeCount === 1 ? 'atributo' : 'atributos'}`;

  return (
    <Box
      sx={{
        width: 190,
        height: 56,
        px: 1.25,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        borderRadius: isTransformation ? '10px' : '28px',
        border: grouped
          ? `1px dashed ${selected ? accent : studio.muted}`
          : `1px solid ${selected ? accent : studio.border}`,
        outline: selected ? `2px solid ${accent}` : 'none',
        outlineOffset: 1,
        bgcolor: selected ? accentDim : studio.surface,
        color: studio.ink,
        boxShadow: selected ? studio.shadow : 'none',
        opacity: dimmed ? 0.28 : 1,
        transition: 'opacity .15s, border-color .15s, background-color .15s',
        ...(grouped && {
          position: 'relative',
          '&::before': {
            content: '""',
            position: 'absolute',
            inset: 0,
            transform: 'translate(4px, 4px)',
            borderRadius: 'inherit',
            border: `1px dashed ${studio.border}`,
            zIndex: -1,
          },
        }),
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0, width: 1, height: 1 }} />

      <Box
        sx={{
          position: 'relative',
          width: 26,
          height: 26,
          flex: 'none',
          borderRadius: '7px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: accentDim === 'transparent' ? studio.elev : accentDim,
          color: accent,
        }}
      >
        {isTransformation ? <GraphIcon size={15} /> : <DataflowsIcon size={15} />}
        {running && (
          <Box
            title="task RUNNING agora, na execução mais recente"
            sx={{
              position: 'absolute',
              top: -3,
              right: -3,
              width: 8,
              height: 8,
              borderRadius: '50%',
              bgcolor: studio.warn,
              border: `1.5px solid ${studio.surface}`,
              animation: `${pulse} 1.4s ease-in-out infinite`,
            }}
          />
        )}
      </Box>

      <Box sx={{ minWidth: 0, flexGrow: 1 }}>
        <Box
          sx={{
            fontFamily: MONO,
            fontSize: 12,
            fontWeight: 500,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          title={node.tag}
        >
          {node.tag}
        </Box>
        <Box
          sx={{
            fontSize: 10.5,
            color: studio.muted,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
          title={grouped ? node.group.memberIds.join(', ') : undefined}
        >
          {subtitle}
        </Box>
      </Box>

      {lineageSide && lineageSide !== 'focus' && (
        <Box
          sx={{
            flex: 'none',
            fontSize: 9.5,
            fontWeight: 600,
            letterSpacing: '.04em',
            color: accent,
          }}
          title={lineageSide === 'upstream' ? 'está a montante' : 'está a jusante'}
        >
          {lineageSide === 'upstream' ? '↑' : '↓'}
        </Box>
      )}

      <Handle type="source" position={Position.Right} style={{ opacity: 0, width: 1, height: 1 }} />
    </Box>
  );
}

export const ProvenanceNode = memo(ProvenanceNodeImpl);
