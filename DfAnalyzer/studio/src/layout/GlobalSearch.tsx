import { Box, Modal, Stack, useTheme } from '@mui/material';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataflowSummaries, useGraph, type GraphNode } from '../api/catalog';
import { MONO } from '../theme/tokens';
import { AnalyticsIcon, DataflowsIcon, GraphIcon, SearchIcon } from './icons';

/** Dataflows lidos pela busca, sem paginação. */
const SEARCH_PAGE_SIZE = 200;
const MAX_PER_GROUP = 8;

interface SearchResult {
  id: string;
  kind: 'dataflow' | 'transformation' | 'dataset' | 'attribute';
  label: string;
  hint?: string;
  action: () => void;
}

/** Busca global (Ctrl/Cmd+K). */
export function GlobalSearch({
  onClose,
  currentDataflow,
  onOpenDataflow,
  onSelectNode,
}: {
  onClose: () => void;
  currentDataflow: { id: number; tag: string } | null;
  onOpenDataflow: (id: number, tag: string) => void;
  onSelectNode: (nodeId: string) => void;
}) {
  const { studio } = useTheme();
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const dataflows = useDataflowSummaries(0, SEARCH_PAGE_SIZE);
  const graphTransformations = useGraph(currentDataflow?.id ?? null, 'transformation');
  const graphDatasets = useGraph(currentDataflow?.id ?? null, 'dataset');

  const results = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (needle.length === 0) return [];

    const out: SearchResult[] = [];

    for (const df of dataflows.data?.items ?? []) {
      if (out.filter((r) => r.kind === 'dataflow').length >= MAX_PER_GROUP) break;
      if (df.tag.toLowerCase().includes(needle) || String(df.id) === needle) {
        out.push({
          id: `df:${df.id}`,
          kind: 'dataflow',
          label: df.tag,
          hint: `dataflow · ${df.transformations} transformações · ${df.datasets} datasets`,
          action: () => onOpenDataflow(df.id, df.tag),
        });
      }
    }

    if (currentDataflow) {
      const transformationNodes = (graphTransformations.data?.nodes ?? []).filter(
        (n) => n.kind === 'TRANSFORMATION',
      );
      for (const node of transformationNodes) {
        if (out.filter((r) => r.kind === 'transformation').length >= MAX_PER_GROUP) break;
        if (node.tag.toLowerCase().includes(needle)) {
          out.push({
            id: node.id,
            kind: 'transformation',
            label: node.tag,
            hint: 'transformação · neste dataflow',
            action: () => onSelectNode(node.id),
          });
        }
      }

      const datasetNodes = (graphDatasets.data?.nodes ?? []).filter((n) => n.kind === 'DATASET');
      for (const node of datasetNodes) {
        if (out.filter((r) => r.kind === 'dataset').length >= MAX_PER_GROUP) break;
        if (node.tag.toLowerCase().includes(needle)) {
          out.push({
            id: node.id,
            kind: 'dataset',
            label: node.tag,
            hint: 'dataset · neste dataflow',
            action: () => onSelectNode(node.id),
          });
        }
      }

      pushAttributeMatches(datasetNodes, needle, out, onSelectNode);
    }

    return out;
  }, [query, dataflows.data, graphTransformations.data, graphDatasets.data, currentDataflow, onOpenDataflow, onSelectNode]);

  const grouped = groupBy(results, (r) => r.kind);
  const noQuery = query.trim().length === 0;
  const noResults = !noQuery && results.length === 0
    && !dataflows.isPending && !graphTransformations.isPending && !graphDatasets.isPending;

  return (
    <Modal
      open
      onClose={onClose}
      disableAutoFocus
      sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'center', pt: '12vh' }}
    >
      <Box
        sx={{
          width: 600, maxWidth: '92vw', maxHeight: '70vh', display: 'flex', flexDirection: 'column',
          bgcolor: studio.surface, border: `1px solid ${studio.border}`, borderRadius: '14px',
          boxShadow: studio.shadow, overflow: 'hidden',
        }}
      >
        <Stack direction="row" alignItems="center" spacing={1.25} sx={{ px: 1.75, height: 52, borderBottom: `1px solid ${studio.border}`, flex: 'none' }}>
          <Box sx={{ color: studio.muted, display: 'flex' }}><SearchIcon size={16} /></Box>
          <Box
            component="input"
            ref={inputRef}
            value={query}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setQuery(e.target.value)}
            onKeyDown={(e: React.KeyboardEvent<HTMLInputElement>) => {
              if (e.key === 'Escape') onClose();
              if (e.key === 'Enter' && results[0]) {
                results[0].action();
                onClose();
              }
            }}
            placeholder={
              currentDataflow
                ? `Buscar dataflow, transformação, dataset ou atributo em ${currentDataflow.tag}`
                : 'Buscar dataflow'
            }
            aria-label="Busca global"
            sx={{
              flexGrow: 1, border: 0, outline: 'none', bgcolor: 'transparent', color: studio.ink,
              fontSize: 14, fontFamily: 'inherit', '&::placeholder': { color: studio.muted },
            }}
          />
          <Box sx={{ fontSize: 11, border: `1px solid ${studio.border}`, borderRadius: '5px', px: 0.6, color: studio.muted }}>
            Esc
          </Box>
        </Stack>

        <Box sx={{ overflowY: 'auto', p: 1 }}>
          {noQuery && (
            <Box sx={{ px: 1.25, py: 2, fontSize: 12.5, color: studio.muted }}>
              Digite para buscar {currentDataflow ? 'neste dataflow ou em outro' : 'um dataflow'}.
              Busca por valores de dados não está disponível — não existe uma
              varredura textual sobre o conteúdo dos datasets hoje.
            </Box>
          )}

          {noResults && (
            <Box sx={{ px: 1.25, py: 2, fontSize: 12.5, color: studio.muted }}>
              Nada encontrado para "{query}".
            </Box>
          )}

          {(['dataflow', 'transformation', 'dataset', 'attribute'] as const).map((kind) =>
            grouped[kind]?.length ? (
              <ResultGroup key={kind} kind={kind} items={grouped[kind]!} onPick={(r) => { r.action(); onClose(); }} />
            ) : null,
          )}
        </Box>
      </Box>
    </Modal>
  );
}

function pushAttributeMatches(
  datasetNodes: GraphNode[],
  needle: string,
  out: SearchResult[],
  onSelectNode: (nodeId: string) => void,
) {
  for (const node of datasetNodes) {
    if (out.filter((r) => r.kind === 'attribute').length >= MAX_PER_GROUP) return;
    for (const attribute of node.attributes ?? []) {
      if (out.filter((r) => r.kind === 'attribute').length >= MAX_PER_GROUP) return;
      if (attribute.name.toLowerCase().includes(needle)) {
        out.push({
          id: `${node.id}#${attribute.name}`,
          kind: 'attribute',
          label: attribute.name,
          hint: `atributo · ${node.tag}`,
          action: () => onSelectNode(node.id),
        });
      }
    }
  }
}

function groupBy<T, K extends string>(items: T[], key: (item: T) => K): Partial<Record<K, T[]>> {
  const out: Partial<Record<K, T[]>> = {};
  for (const item of items) {
    const k = key(item);
    (out[k] ??= []).push(item);
  }
  return out;
}

const GROUP_LABEL: Record<SearchResult['kind'], string> = {
  dataflow: 'Dataflows',
  transformation: 'Transformações',
  dataset: 'Datasets',
  attribute: 'Atributos',
};

const GROUP_ICON: Record<SearchResult['kind'], typeof DataflowsIcon> = {
  dataflow: DataflowsIcon,
  transformation: GraphIcon,
  dataset: GraphIcon,
  attribute: AnalyticsIcon,
};

function ResultGroup({
  kind,
  items,
  onPick,
}: {
  kind: SearchResult['kind'];
  items: SearchResult[];
  onPick: (r: SearchResult) => void;
}) {
  const { studio } = useTheme();
  const Glyph = GROUP_ICON[kind];
  return (
    <Box sx={{ mb: 0.5 }}>
      <Box sx={{ px: 1.25, py: 0.625, fontSize: 10.5, fontWeight: 600, letterSpacing: '.04em', color: studio.muted }}>
        {GROUP_LABEL[kind].toUpperCase()}
      </Box>
      {items.map((r) => (
        <Box
          key={r.id}
          component="button"
          type="button"
          onClick={() => onPick(r)}
          sx={{
            width: '100%', textAlign: 'left', display: 'flex', alignItems: 'center', gap: 1,
            border: 0, bgcolor: 'transparent', borderRadius: '8px', px: 1.25, py: 0.875,
            cursor: 'pointer', fontFamily: 'inherit',
            '&:hover': { bgcolor: studio.elev },
          }}
        >
          <Box sx={{ color: studio.muted, display: 'flex', flex: 'none' }}><Glyph size={15} /></Box>
          <Box sx={{ minWidth: 0, flexGrow: 1 }}>
            <Box sx={{ fontFamily: MONO, fontSize: 12.5, color: studio.ink, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {r.label}
            </Box>
            {r.hint && <Box sx={{ fontSize: 10.5, color: studio.muted }}>{r.hint}</Box>}
          </Box>
        </Box>
      ))}
    </Box>
  );
}
