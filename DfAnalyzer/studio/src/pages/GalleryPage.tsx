import { Alert, AlertTitle, Box, Skeleton, Stack, useTheme } from '@mui/material';
import { useMemo, useState } from 'react';
import {
  useDatasetSnapshot,
  useExecutions,
  useGraph,
  useVersions,
  type AttributeInfo,
  type ExecutionSummary,
  type GraphNode,
  type QueryRequest,
  type QueryResult,
  type VersionSummary,
} from '../api/catalog';
import { formatCount } from '../analytics/format';
import { StudioCard, Chip } from '../components/primitives';
import { downloadCsv, toCsv } from '../query/csv';
import { ExportIcon, FileIcon } from '../layout/icons';
import { SectionSwitch, type DataflowSection } from '../layout/SectionSwitch';
import { MONO, type StudioPalette } from '../theme/tokens';

/** Linhas lidas por card. */
const CARD_SAMPLE_SIZE = 50;
/** Linhas lidas com o dataset aberto, também usadas no CSV. */
const DETAIL_SAMPLE_SIZE = 200;

export function GalleryPage({
  dataflowId,
  dataflowTag,
  onBack,
  section,
  onNavigateSection,
}: {
  dataflowId: number;
  dataflowTag: string;
  onBack: () => void;
  section: DataflowSection;
  onNavigateSection: (section: DataflowSection) => void;
}) {
  const { studio } = useTheme();

  const versions = useVersions(dataflowId);
  const [versionOverride, setVersionOverride] = useState<number | null>(null);
  const effectiveVersion = versionOverride ?? versions.data?.[0]?.version ?? null;

  const executions = useExecutions(dataflowId, effectiveVersion);
  const [execOverride, setExecOverride] = useState<number | null>(null);
  const effectiveExecId = execOverride ?? executions.data?.[0]?.id ?? null;
  const execution = executions.data?.find((e) => e.id === effectiveExecId) ?? null;

  const graphDatasets = useGraph(dataflowId, 'dataset');
  const graphTransformations = useGraph(dataflowId, 'transformation');

  // Produtora é a origem da aresta com a tag do dataset na visão de transformações.
  const producerByTag = useMemo(() => {
    const graph = graphTransformations.data;
    if (!graph) return new Map<string, string>();
    const nodesById = new Map(graph.nodes.map((n) => [n.id, n]));
    const map = new Map<string, string>();
    for (const edge of graph.edges) {
      const source = nodesById.get(edge.source);
      if (source?.kind === 'TRANSFORMATION' && !map.has(edge.label)) {
        map.set(edge.label, source.tag);
      }
    }
    return map;
  }, [graphTransformations.data]);

  const datasets = useMemo(
    () => (graphDatasets.data?.nodes ?? []).filter((n) => n.kind === 'DATASET'),
    [graphDatasets.data],
  );

  const [openTag, setOpenTag] = useState<string | null>(null);
  const openNode = openTag ? datasets.find((d) => d.tag === openTag) ?? null : null;

  const loading = versions.isPending || graphDatasets.isPending || graphTransformations.isPending;
  const errored = versions.isError || graphDatasets.isError || graphTransformations.isError;
  const noVersions = !loading && !errored && (versions.data?.length ?? 0) === 0;
  const noExecutions = !loading && !errored && !noVersions && !executions.isPending
    && (executions.data?.length ?? 0) === 0;
  const noDatasets = !loading && !errored && datasets.length === 0;

  return (
    <>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={1.75}
        alignItems={{ xs: 'stretch', md: 'flex-end' }}
      >
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Box
              component="button"
              type="button"
              onClick={openTag ? () => setOpenTag(null) : onBack}
              sx={{
                border: 0, bgcolor: 'transparent', p: 0, color: studio.muted,
                cursor: 'pointer', fontSize: 13, fontFamily: 'inherit',
                '&:hover': { color: studio.ink },
              }}
            >
              {openTag ? 'Galeria' : 'Dataflows'}
            </Box>
            <Box sx={{ color: studio.muted, fontSize: 13 }}>/</Box>
            {!openTag && <Box sx={{ fontFamily: MONO, fontSize: 13, fontWeight: 600 }}>{dataflowTag}</Box>}
            {openTag && (
              <Box sx={{ fontFamily: MONO, fontSize: 13, fontWeight: 600 }}>{openTag}</Box>
            )}
          </Stack>
          <Box component="h1" sx={{ m: 0, mt: 0.5, fontSize: 21, fontWeight: 600, letterSpacing: '-.01em' }}>
            {openTag ? openTag : 'Galeria de dados'}
          </Box>
          <Box sx={{ mt: 0.5, fontSize: 12.5, color: studio.muted }}>
            {execution
              ? `${formatCount(datasets.length)} datasets · execução #${execution.id} · ${execution.tag}`
              : 'Sem execuções registradas'}
          </Box>
        </Box>

        <Box sx={{ flex: 'none' }}>
          <SectionSwitch section={section} onNavigate={onNavigateSection} />
        </Box>
      </Stack>

      {errored && (
        <Alert severity="error">
          <AlertTitle>Não foi possível carregar a galeria</AlertTitle>
          {versions.error instanceof Error ? versions.error.message
            : graphDatasets.error instanceof Error ? graphDatasets.error.message
              : 'Erro desconhecido.'}
        </Alert>
      )}

      {loading && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
          {[0, 1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={150} />)}
        </Box>
      )}

      {noVersions && (
        <StudioCard sx={{ p: 6, alignItems: 'center', textAlign: 'center' }}>
          <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>Nenhuma versão salva</Box>
          <Box sx={{ fontSize: 12.5, color: studio.muted, maxWidth: 420 }}>
            A proveniência prospectiva deste dataflow ainda não foi salva — a
            galeria depende de uma execução para ter dados a mostrar.
          </Box>
        </StudioCard>
      )}

      {noExecutions && (
        <StudioCard sx={{ p: 6, alignItems: 'center', textAlign: 'center' }}>
          <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>Nenhuma execução ainda</Box>
          <Box sx={{ fontSize: 12.5, color: studio.muted, maxWidth: 420 }}>
            A definição está salva, mas nenhuma task foi ingerida nesta versão.
          </Box>
        </StudioCard>
      )}

      {noDatasets && !noVersions && (
        <StudioCard sx={{ p: 6, alignItems: 'center', textAlign: 'center' }}>
          <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>Nenhum dataset declarado</Box>
          <Box sx={{ fontSize: 12.5, color: studio.muted, maxWidth: 420 }}>
            Este dataflow não declara datasets na proveniência prospectiva.
          </Box>
        </StudioCard>
      )}

      {!loading && !errored && !noVersions && !noExecutions && !noDatasets && execution && (
        <>
          {!openTag && (
            <>
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <PickerSelect
                  label="Versão"
                  value={effectiveVersion}
                  onChange={setVersionOverride}
                  options={(versions.data ?? []).map((v: VersionSummary) => ({
                    value: v.version,
                    label: `versão ${v.version} · ${formatCount(v.executions)} execuções`,
                  }))}
                />
                <PickerSelect
                  label="Execução"
                  value={effectiveExecId}
                  onChange={setExecOverride}
                  options={(executions.data ?? []).map((e: ExecutionSummary) => ({
                    value: e.id,
                    label: `#${e.id} · ${e.tag}`,
                  }))}
                />
              </Stack>

              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' }, gap: 1.5 }}>
                {datasets.map((node) => (
                  <GalleryCard
                    key={node.id}
                    node={node}
                    producer={producerByTag.get(node.tag) ?? null}
                    dataflowId={dataflowId}
                    version={effectiveVersion!}
                    execution={execution}
                    onOpen={() => setOpenTag(node.tag)}
                  />
                ))}
              </Box>
            </>
          )}

          {openTag && openNode && (
            <GalleryDetail
              node={openNode}
              producer={producerByTag.get(openNode.tag) ?? null}
              dataflowId={dataflowId}
              version={effectiveVersion!}
              execution={execution}
            />
          )}
        </>
      )}
    </>
  );
}

function buildSnapshotRequest(
  tag: string,
  attributes: AttributeInfo[],
  version: number,
  execution: number,
  size: number,
): QueryRequest | null {
  if (attributes.length === 0) return null;
  return {
    version,
    mapping: 'LOGICAL',
    sources: [tag],
    targets: [],
    projections: attributes.map((a) => `${tag}.${a.name}`),
    selections: [],
    execution,
    page: 0,
    size,
  };
}

function roleColor(studio: StudioPalette, role: GraphNode['role']): string {
  return role === 'SOURCE' ? studio.aqua : role === 'SINK' ? studio.orange : studio.muted;
}

function GalleryCard({
  node,
  producer,
  dataflowId,
  version,
  execution,
  onOpen,
}: {
  node: GraphNode;
  producer: string | null;
  dataflowId: number;
  version: number;
  execution: ExecutionSummary;
  onOpen: () => void;
}) {
  const { studio } = useTheme();
  const attributes = node.attributes ?? [];
  const request = useMemo(
    () => buildSnapshotRequest(node.tag, attributes, version, execution.id, CARD_SAMPLE_SIZE),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [node.tag, version, execution.id],
  );
  const snapshot = useDatasetSnapshot(dataflowId, request);

  return (
    <Box
      component="button"
      type="button"
      onClick={onOpen}
      sx={{
        textAlign: 'left', cursor: 'pointer', p: 1.75, display: 'flex', flexDirection: 'column',
        gap: 1.25, borderRadius: '12px', border: `1px solid ${studio.border}`, bgcolor: studio.surface,
        fontFamily: 'inherit', minHeight: 150,
        '&:hover': { borderColor: studio.blue },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      <Stack direction="row" alignItems="center" spacing={0.875}>
        <Box sx={{ width: 7, height: 7, borderRadius: '50%', flex: 'none', bgcolor: roleColor(studio, node.role) }} />
        <Box
          sx={{
            fontFamily: MONO, fontSize: 12.5, fontWeight: 600, color: studio.ink,
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0,
          }}
          title={node.tag}
        >
          {node.tag}
        </Box>
      </Stack>

      {producer && (
        <Box sx={{ alignSelf: 'flex-start' }}>
          <Chip tone="blue">{producer}</Chip>
        </Box>
      )}

      <Box sx={{ fontSize: 11, color: studio.muted }}>
        {snapshot.data
          ? `${formatCount(snapshot.data.rows.length)}${snapshot.data.hasMore ? '+' : ''} linha${snapshot.data.rows.length === 1 && !snapshot.data.hasMore ? '' : 's'}`
          : '—'}
        {' · '}{attributes.length} atributo{attributes.length === 1 ? '' : 's'}
      </Box>

      {snapshot.isPending && attributes.length > 0 && <Skeleton variant="rounded" height={28} />}
      {snapshot.isError && (
        <Box sx={{ fontSize: 11, color: studio.crit }}>Não foi possível carregar a amostra.</Box>
      )}
      {snapshot.data && <CardPreview attributes={attributes} result={snapshot.data} />}

      <Box sx={{ mt: 'auto', display: 'flex', alignItems: 'center', gap: 0.5, fontSize: 11.5, color: studio.muted, fontWeight: 500 }}>
        Abrir →
      </Box>
    </Box>
  );
}

type Preview =
  | { kind: 'file'; count: number }
  | { kind: 'numeric'; label: string; value: string; pct: number }
  | { kind: 'text'; chips: string[] }
  | { kind: 'empty' };

function computePreview(attributes: AttributeInfo[], result: QueryResult): Preview {
  if (result.rows.length === 0) return { kind: 'empty' };

  const hasFile = attributes.some((a) => a.type === 'FILE');
  if (hasFile) {
    return { kind: 'file', count: attributes.filter((a) => a.type === 'FILE').length };
  }

  const numericIndex = result.columns.findIndex((c) => c.type === 'NUMERIC');
  if (numericIndex >= 0) {
    const values = result.rows
      .map((r) => r[numericIndex])
      .filter((v): v is string => v != null)
      .map(Number)
      .filter((n) => Number.isFinite(n));
    if (values.length > 0) {
      const max = Math.max(...values);
      const avg = values.reduce((a, b) => a + b, 0) / values.length;
      const label = result.columns[numericIndex].name.split('.').slice(1).join('.');
      const pct = max > 0 ? Math.min(100, Math.round((avg / max) * 100)) : 0;
      return { kind: 'numeric', label, value: formatNumber(avg), pct };
    }
  }

  const textIndex = result.columns.findIndex((c) => c.type === 'TEXT');
  if (textIndex >= 0) {
    const values = result.rows.map((r) => r[textIndex]).filter((v): v is string => v != null);
    if (values.length > 0) {
      const counts = new Map<string, number>();
      for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1);
      const top = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3);
      const chips = top.map(([v, c]) => (c > 1 ? `${v} ×${c}` : v));
      return { kind: 'text', chips };
    }
  }

  return { kind: 'empty' };
}

function CardPreview({ attributes, result }: { attributes: AttributeInfo[]; result: QueryResult }) {
  const { studio } = useTheme();
  const preview = computePreview(attributes, result);

  if (preview.kind === 'file') {
    return (
      <Stack direction="row" spacing={0.875} alignItems="center" sx={{ p: 1, borderRadius: '8px', border: `1px solid ${studio.border}`, bgcolor: studio.page }}>
        <Box sx={{ color: studio.muted, display: 'flex', flex: 'none' }}><FileIcon size={16} /></Box>
        <Box sx={{ fontSize: 11, color: studio.muted }}>
          {preview.count} atributo{preview.count === 1 ? '' : 's'} de arquivo
        </Box>
      </Stack>
    );
  }

  if (preview.kind === 'numeric') {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        <Stack direction="row" justifyContent="space-between" sx={{ fontSize: 10.5, color: studio.muted }}>
          <Box sx={{ fontFamily: MONO }}>{preview.label} · média</Box>
          <Box sx={{ fontVariantNumeric: 'tabular-nums' }}>{preview.value}</Box>
        </Stack>
        <Box sx={{ height: 6, borderRadius: '3px', bgcolor: studio.border, overflow: 'hidden' }}>
          <Box sx={{ height: '100%', width: `${preview.pct}%`, borderRadius: '3px', bgcolor: studio.blue }} />
        </Box>
      </Box>
    );
  }

  if (preview.kind === 'text') {
    return (
      <Stack direction="row" flexWrap="wrap" useFlexGap sx={{ gap: 0.625 }}>
        {preview.chips.map((c) => <Chip key={c}>{c}</Chip>)}
      </Stack>
    );
  }

  return <Box sx={{ fontSize: 11, color: studio.muted }}>sem linhas nesta execução</Box>;
}

function GalleryDetail({
  node,
  producer,
  dataflowId,
  version,
  execution,
}: {
  node: GraphNode;
  producer: string | null;
  dataflowId: number;
  version: number;
  execution: ExecutionSummary;
}) {
  const { studio } = useTheme();
  const attributes = node.attributes ?? [];
  const request = useMemo(
    () => buildSnapshotRequest(node.tag, attributes, version, execution.id, DETAIL_SAMPLE_SIZE),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [node.tag, version, execution.id],
  );
  const snapshot = useDatasetSnapshot(dataflowId, request);
  const fileAttrs = attributes.filter((a) => a.type === 'FILE');

  return (
    <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.5} sx={{ minHeight: 0, flexGrow: 1 }}>
      <StudioCard sx={{ width: { xs: '100%', lg: 280 }, flex: 'none', p: 1.875, gap: 1.5, overflow: 'auto' }}>
        <Box>
          <Box sx={{ fontSize: 13, fontWeight: 600 }}>Atributos</Box>
          {producer && (
            <Box sx={{ mt: 0.75 }}><Chip tone="blue">{producer}</Chip></Box>
          )}
          <Stack spacing={0} sx={{ mt: 1 }}>
            {attributes.length === 0 && (
              <Box sx={{ fontSize: 12, color: studio.muted }}>Sem atributos declarados.</Box>
            )}
            {attributes.map((a) => (
              <Stack
                key={a.name} direction="row" alignItems="center" spacing={1}
                sx={{ py: 0.75, borderBottom: `1px solid ${studio.border}` }}
              >
                <Box sx={{ fontFamily: MONO, fontSize: 11.5, flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }} title={a.name}>
                  {a.name}
                </Box>
                <Chip tone={a.type === 'FILE' ? 'aqua' : 'neutral'}>{a.type ?? '—'}</Chip>
              </Stack>
            ))}
          </Stack>
        </Box>

        {fileAttrs.length > 0 && snapshot.data && (
          <Box sx={{ borderTop: `1px solid ${studio.border}`, pt: 1.25 }}>
            <Box sx={{ fontSize: 12, fontWeight: 600, mb: 0.875 }}>Arquivo</Box>
            {fileAttrs.map((a) => {
              const colIndex = snapshot.data!.columns.findIndex((c) => c.name === `${node.tag}.${a.name}`);
              const value = colIndex >= 0 ? snapshot.data!.rows[0]?.[colIndex] ?? null : null;
              return (
                <Stack key={a.name} direction="row" spacing={0.875} alignItems="center" sx={{ p: 1, borderRadius: '8px', border: `1px solid ${studio.border}`, bgcolor: studio.page, mb: 0.75 }}>
                  <Box sx={{ color: studio.muted, display: 'flex', flex: 'none' }}><FileIcon size={16} /></Box>
                  <Box sx={{ fontFamily: MONO, fontSize: 10.5, color: studio.muted, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={value ?? undefined}>
                    {value ?? '—'}
                  </Box>
                </Stack>
              );
            })}
            <Box sx={{ fontSize: 10.5, color: studio.muted, lineHeight: 1.5 }}>
              Pré-visualização de conteúdo não disponível — o esquema guarda a
              referência ao arquivo, não o arquivo em si.
            </Box>
          </Box>
        )}
      </StudioCard>

      <StudioCard sx={{ flexGrow: 1, p: 1.875, minWidth: 0 }}>
        <Stack direction="row" alignItems="baseline" spacing={1} flexWrap="wrap">
          <Box sx={{ fontSize: 13, fontWeight: 600 }}>Amostra</Box>
          <Box sx={{ fontSize: 11.5, color: studio.muted }}>
            {snapshot.data
              ? `${formatCount(snapshot.data.rows.length)}${snapshot.data.hasMore ? '+' : ''} linhas nesta execução`
              : 'carregando…'}
          </Box>
          {snapshot.data && snapshot.data.rows.length > 0 && (
            <Box
              component="button"
              type="button"
              onClick={() => downloadCsv(`${node.tag}_exec${execution.id}.csv`, toCsv(snapshot.data!))}
              sx={{
                ml: 'auto', height: 29, borderRadius: '8px', border: `1px solid ${studio.border}`,
                bgcolor: studio.elev, color: studio.ink, fontSize: 12, fontWeight: 500, fontFamily: 'inherit',
                display: 'inline-flex', alignItems: 'center', gap: 0.625, px: 1.25, cursor: 'pointer',
              }}
            >
              <ExportIcon size={13} /> CSV da amostra
            </Box>
          )}
        </Stack>

        <Box sx={{ mt: 1, overflow: 'auto', minHeight: 0, flexGrow: 1 }}>
          {snapshot.isPending && <Skeleton variant="rounded" height={200} />}
          {snapshot.isError && (
            <Alert severity="error">
              {snapshot.error instanceof Error ? snapshot.error.message : 'Não foi possível carregar a amostra.'}
            </Alert>
          )}
          {snapshot.data && snapshot.data.rows.length === 0 && (
            <Box sx={{ fontSize: 12.5, color: studio.muted }}>Nenhuma linha nesta execução.</Box>
          )}
          {snapshot.data && snapshot.data.rows.length > 0 && (
            <Box component="table" sx={{ width: '100%', minWidth: 420, borderCollapse: 'collapse', fontSize: 12 }}>
              <Box component="thead">
                <Box component="tr">
                  {snapshot.data.columns.map((c) => (
                    <Box component="th" key={c.name} sx={{ textAlign: 'left', fontWeight: 500, color: studio.muted, fontSize: 11, letterSpacing: '.02em', pb: 0.75, pr: 1.5, borderBottom: `1px solid ${studio.border}`, whiteSpace: 'nowrap' }}>
                      {c.name.split('.').slice(1).join('.') || c.name}
                    </Box>
                  ))}
                </Box>
              </Box>
              <Box component="tbody">
                {snapshot.data.rows.map((row, i) => (
                  <Box component="tr" key={i}>
                    {row.map((cell, j) => (
                      <Box component="td" key={j} sx={{ py: 0.75, pr: 1.5, borderBottom: `1px solid ${studio.border}`, color: studio.muted, fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                        {cell ?? '—'}
                      </Box>
                    ))}
                  </Box>
                ))}
              </Box>
            </Box>
          )}
        </Box>
      </StudioCard>
    </Stack>
  );
}

function formatNumber(n: number): string {
  return Number.isInteger(n) ? n.toLocaleString('pt-BR') : n.toFixed(2).replace('.', ',');
}

function PickerSelect<T extends number>({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: T | null;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
}) {
  const { studio } = useTheme();
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      <Box sx={{ fontSize: 11.5, color: studio.muted, flex: 'none' }}>{label}</Box>
      <Box
        component="select"
        value={value ?? ''}
        onChange={(e: React.ChangeEvent<HTMLSelectElement>) => onChange(Number(e.target.value) as T)}
        disabled={options.length === 0}
        sx={{
          height: 34, borderRadius: '9px', border: `1px solid ${studio.border}`,
          bgcolor: studio.elev, color: studio.ink, fontSize: 12.5, fontFamily: 'inherit',
          px: 1, maxWidth: 260,
        }}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </Box>
    </Stack>
  );
}
