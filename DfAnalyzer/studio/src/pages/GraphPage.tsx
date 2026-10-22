import { Alert, AlertTitle, Box, Menu, MenuItem, Skeleton, Stack, Tooltip, useMediaQuery, useTheme } from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  useExecutionTimeline,
  useExecutions,
  useGraph,
  useLineage,
  useVersions,
  provJsonUrl,
  type GraphResponse,
  type GraphView,
} from '../api/catalog';
import { Chip, Segmented, StudioCard } from '../components/primitives';
import { downloadPng, downloadSvg, graphToSvg } from '../graph/exportImage';
import { ProvenanceGraph } from '../graph/ProvenanceGraph';
import { NodeInspector } from '../graph/NodeInspector';
import { NodeList } from '../graph/NodeList';
import { ExportIcon } from '../layout/icons';
import { SectionSwitch, type DataflowSection } from '../layout/SectionSwitch';
import { MONO } from '../theme/tokens';

type Detail = 'grouped' | 'full';

/** Acima disto, o grafo abre agrupado. */
const AUTO_GROUP_THRESHOLD = 60;

export function GraphPage({
  dataflowId,
  dataflowTag,
  view,
  selectedNodeId,
  onViewChange,
  onSelectNode,
  onBack,
  section,
  onNavigateSection,
}: {
  dataflowId: number;
  dataflowTag: string;
  view: GraphView;
  selectedNodeId: string | null;
  onViewChange: (view: GraphView) => void;
  onSelectNode: (nodeId: string | null) => void;
  onBack: () => void;
  section: DataflowSection;
  onNavigateSection: (section: DataflowSection) => void;
}) {
  const theme = useTheme();
  const { studio } = theme;
  const compact = useMediaQuery(theme.breakpoints.down('sm'));
  const [compactLayout, setCompactLayout] = useState<'list' | 'canvas'>('list');
  const asList = compact && compactLayout === 'list';

  const graph = useGraph(dataflowId, view);

  const [detail, setDetail] = useState<Detail | null>(null);
  const [expandedGroups, setExpandedGroups] = useState<ReadonlySet<string>>(new Set());
  const [collapseStats, setCollapseStats] = useState({ visible: 0, collapsed: 0 });

  const autoDetail: Detail =
    (graph.data?.nodes.length ?? 0) > AUTO_GROUP_THRESHOLD ? 'grouped' : 'full';
  const effectiveDetail = detail ?? autoDetail;

  useEffect(() => {
    setExpandedGroups(new Set());
  }, [dataflowId, view]);

  const toggleGroup = useCallback((groupId: string) => {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (!next.delete(groupId)) {
        next.add(groupId);
      }
      return next;
    });
  }, []);
  const lineage = useLineage(dataflowId, view, selectedNodeId);

  const versions = useVersions(dataflowId);
  const mapping = useMemo(() => {
    const shown = versions.data?.find((v) => v.version === graph.data?.version);
    return shown ? { origin: shown.origin, generator: shown.generator, version: shown.version } : undefined;
  }, [versions.data, graph.data]);

  const executions = useExecutions(dataflowId, graph.data?.version ?? null, { live: true });
  const latestExecution = executions.data?.[0] ?? null;
  // Sem escolha, mostra a execução mais recente.
  const [pickedExecution, setPickedExecution] = useState<number | null>(null);
  useEffect(() => {
    setPickedExecution(null);
  }, [dataflowId]);
  const shownExecution =
    executions.data?.find((e) => e.id === pickedExecution) ?? latestExecution;
  const timeline = useExecutionTimeline(dataflowId, shownExecution?.id ?? null, { live: true });
  const [layoutNonce, setLayoutNonce] = useState(0);
  const [traceLineage, setTraceLineage] = useState(true);
  const runningTags = useMemo(
    () =>
      new Set(
        (timeline.data?.anomalies ?? [])
          .filter((a) => a.status === 'RUNNING')
          .map((a) => a.transformationTag),
      ),
    [timeline.data],
  );

  const selectedStats = useMemo(() => {
    if (!shownExecution || !selectedNodeId?.startsWith('t:')) return undefined;
    const tag = selectedNodeId.slice(2).toLowerCase();
    const row = (timeline.data?.rows ?? []).find((r) => r.transformationTag.toLowerCase() === tag) ?? null;
    // Enquanto carrega, não se afirma "nenhuma task".
    return timeline.data ? { executionId: shownExecution.id, row } : undefined;
  }, [shownExecution, selectedNodeId, timeline.data]);

  const selectedNode = useMemo(
    () => graph.data?.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [graph.data, selectedNodeId],
  );

  const counts = useMemo(() => {
    if (!graph.data) return null;
    const transformations = graph.data.nodes.filter((n) => n.kind === 'TRANSFORMATION').length;
    const datasets = graph.data.nodes.length - transformations;
    return {
      transformations,
      datasets,
      edges: graph.data.edges.length,
      total: graph.data.nodes.length,
    };
  }, [graph.data]);

  return (
    <>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={1.75}
        alignItems={{ xs: 'stretch', md: 'flex-end' }}
      >
        <Box sx={{ flex: { xs: '0 0 auto', md: '0 0 280px' }, minWidth: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Box
              component="button"
              type="button"
              onClick={onBack}
              sx={{
                border: 0,
                bgcolor: 'transparent',
                p: 0,
                color: studio.muted,
                cursor: 'pointer',
                fontSize: 13,
                fontFamily: 'inherit',
                '&:hover': { color: studio.ink },
              }}
            >
              Dataflows
            </Box>
            <Box sx={{ color: studio.muted, fontSize: 13 }}>/</Box>
            <Box sx={{ fontFamily: MONO, fontSize: 13, fontWeight: 600 }}>{dataflowTag}</Box>
          </Stack>
          <Box component="h1" sx={{ m: 0, mt: 0.5, fontSize: 21, fontWeight: 600, letterSpacing: '-.01em' }}>
            Grafo de proveniência
          </Box>
          <Box sx={{ mt: 0.5, fontSize: 12.5, color: studio.muted }}>
            {view === 'transformation'
              ? 'Transformação como nó (PROV Activity), dataset como aresta'
              : 'Dataset como nó (PROV Entity), transformação como aresta'}
            {graph.data && ` · versão ${graph.data.version}`}
          </Box>
        </Box>

        <Stack
          direction="row"
          spacing={1}
          useFlexGap
          alignItems="center"
          sx={{ flex: '1 1 0', flexWrap: 'wrap', rowGap: 1, minWidth: 0, justifyContent: { md: 'flex-end' } }}
        >
          <SectionSwitch section={section} onNavigate={onNavigateSection} />
          {counts && counts.total > AUTO_GROUP_THRESHOLD && (
            <Tooltip
              title={
                effectiveDetail === 'grouped'
                  ? `${collapseStats.collapsed} nós dobrados em grupos — clique num grupo para abri-lo`
                  : 'Todos os nós visíveis'
              }
            >
              <Box>
                <Segmented
                  ariaLabel="Nível de detalhe"
                  value={effectiveDetail}
                  onChange={setDetail}
                  options={[
                    { value: 'grouped', label: 'Agrupado' },
                    { value: 'full', label: 'Completo' },
                  ]}
                />
              </Box>
            </Tooltip>
          )}
          {counts && (
            <Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', rowGap: 0.75 }}>
              {counts.transformations > 0 && (
                <Chip tone="blue">{counts.transformations} transformações</Chip>
              )}
              {counts.datasets > 0 && <Chip>{counts.datasets} datasets</Chip>}
              <Chip>{counts.edges} arestas</Chip>
              {latestExecution && latestExecution.runningTasks > 0 && (
                <Chip tone="warn">
                  execução #{latestExecution.id} em andamento · {latestExecution.runningTasks} task
                  {latestExecution.runningTasks === 1 ? '' : 's'}
                </Chip>
              )}
              {effectiveDetail === 'grouped' && collapseStats.collapsed > 0 && (
                <Chip tone="aqua">
                  {collapseStats.visible} visíveis
                </Chip>
              )}
            </Stack>
          )}
          <Segmented
            ariaLabel="Visão do grafo"
            value={view}
            onChange={onViewChange}
            options={[
              { value: 'transformation', label: 'Transformações' },
              { value: 'dataset', label: 'Datasets' },
            ]}
          />
          {(executions.data?.length ?? 0) > 0 && shownExecution && (
            <Box
              component="select"
              aria-label="Execução mostrada no grafo"
              title="Execução cujas contagens de tasks aparecem nos nós"
              value={shownExecution.id}
              onChange={(event: React.ChangeEvent<HTMLSelectElement>) => setPickedExecution(Number(event.target.value))}
              sx={{
                height: 29,
                maxWidth: 230,
                px: 1,
                borderRadius: '8px',
                border: `1px solid ${studio.border}`,
                bgcolor: studio.elev,
                color: studio.ink,
                fontSize: 12,
                fontFamily: 'inherit',
              }}
            >
              {(executions.data ?? []).map((e) => (
                <option key={e.id} value={e.id}>
                  Execução #{e.id}
                  {e.startTime ? ` · ${e.startTime.slice(0, 16)}` : ''}
                </option>
              ))}
            </Box>
          )}
          {!asList && graph.data && graph.data.nodes.length > 0 && (
            <Tooltip
              title={
                traceLineage
                  ? 'Ao escolher um nó, acende o que está a montante e a jusante dele. Clique para desligar.'
                  : 'Realce da linhagem desligado. Clique para ligar.'
              }
            >
              <Box
                component="button"
                type="button"
                aria-pressed={traceLineage}
                onClick={() => setTraceLineage((on) => !on)}
                sx={{
                  height: 29,
                  px: 1.25,
                  borderRadius: '8px',
                  border: `1px solid ${traceLineage ? studio.blue : studio.border}`,
                  bgcolor: traceLineage ? studio.blueDim : studio.elev,
                  color: traceLineage ? studio.blue : studio.ink,
                  fontSize: 12,
                  fontWeight: 500,
                  fontFamily: 'inherit',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
                }}
              >
                Rastrear linhagem
              </Box>
            </Tooltip>
          )}
          {!asList && graph.data && graph.data.nodes.length > 0 && (
            <Tooltip title="Devolve os nós arrastados à posição calculada e reenquadra o grafo">
              <Box
                component="button"
                type="button"
                onClick={() => setLayoutNonce((n) => n + 1)}
                sx={{
                  height: 29,
                  px: 1.25,
                  borderRadius: '8px',
                  border: `1px solid ${studio.border}`,
                  bgcolor: studio.elev,
                  color: studio.ink,
                  fontSize: 12,
                  fontWeight: 500,
                  fontFamily: 'inherit',
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  '&:hover': { borderColor: studio.muted },
                  '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
                }}
              >
                Auto-layout
              </Box>
            </Tooltip>
          )}
          {compact && (
            <Segmented
              ariaLabel="Como mostrar os nós"
              value={compactLayout}
              onChange={setCompactLayout}
              options={[
                { value: 'list', label: 'Lista' },
                { value: 'canvas', label: 'Desenho' },
              ]}
            />
          )}
          {graph.data && graph.data.nodes.length > 0 && (
            <ExportMenu graph={graph.data} dataflowId={dataflowId} dataflowTag={dataflowTag} view={view} />
          )}
        </Stack>
      </Stack>

      <StudioCard sx={{ flexGrow: 1, overflow: 'hidden' }}>
        {graph.isError && (
          <Box sx={{ p: 2 }}>
            <Alert severity="error">
              <AlertTitle>Não foi possível carregar o grafo</AlertTitle>
              {graph.error instanceof Error ? graph.error.message : 'Erro desconhecido.'}
            </Alert>
          </Box>
        )}

        {graph.isPending && (
          <Box sx={{ p: 2, flexGrow: 1 }}>
            <Skeleton variant="rounded" height="100%" sx={{ minHeight: 320 }} />
          </Box>
        )}

        {!graph.isPending && !graph.isError && graph.data.nodes.length === 0 && (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>Grafo vazio</Box>
            <Box sx={{ fontSize: 12.5, color: studio.muted }}>
              Esta versão do dataflow não declarou transformações.
            </Box>
          </Box>
        )}

        {!graph.isPending && !graph.isError && graph.data.nodes.length > 0 && (
          <Box
            sx={{
              flexGrow: 1,
              minHeight: 0,
              display: 'flex',
              flexDirection: { xs: 'column', lg: 'row' },
            }}
          >
            {asList ? (
              <NodeList
                nodes={graph.data.nodes}
                timeline={timeline.data?.rows ?? []}
                runningTags={runningTags}
                selectedNodeId={selectedNodeId}
                onSelectNode={onSelectNode}
              />
            ) : (
            <ProvenanceGraph
              graph={graph.data}
              lineage={selectedNodeId && traceLineage ? (lineage.data ?? null) : null}
              selectedNodeId={selectedNodeId}
              onSelectNode={onSelectNode}
              grouped={effectiveDetail === 'grouped'}
              expandedGroups={expandedGroups}
              onToggleGroup={toggleGroup}
              onCollapseStats={setCollapseStats}
              runningTags={runningTags}
              layoutNonce={layoutNonce}
            />
            )}
            {selectedNode && !asList && (
              <NodeInspector
                node={selectedNode}
                lineage={lineage.data ?? null}
                dataflowId={dataflowId}
                mapping={mapping}
                stats={selectedStats}
                onQuery={() => onNavigateSection('query')}
                onOpenData={() => onNavigateSection('gallery')}
                onClose={() => onSelectNode(null)}
                onFocusNode={onSelectNode}
              />
            )}
            {selectedNode && asList && (
              <Box
                role="dialog"
                aria-label={`Detalhes de ${selectedNode.tag}`}
                sx={{
                  position: 'fixed',
                  left: 0,
                  right: 0,
                  // Acima da barra de navegação inferior.
                  bottom: 'calc(58px + env(safe-area-inset-bottom, 0px))',
                  zIndex: theme.zIndex.drawer,
                  maxHeight: '62dvh',
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'hidden',
                  borderRadius: '18px 18px 0 0',
                  borderTop: `1px solid ${studio.border}`,
                  bgcolor: studio.surface,
                  boxShadow: studio.shadow,
                }}
              >
                <Box sx={{ overflowY: 'auto', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
                  <NodeInspector
                    node={selectedNode}
                    lineage={lineage.data ?? null}
                    dataflowId={dataflowId}
                    mapping={mapping}
                    stats={selectedStats}
                    onQuery={() => onNavigateSection('query')}
                    onOpenData={() => onNavigateSection('gallery')}
                    onClose={() => onSelectNode(null)}
                    onFocusNode={onSelectNode}
                  />
                </Box>
              </Box>
            )}
          </Box>
        )}
      </StudioCard>
    </>
  );
}

function ExportMenu({
  graph,
  dataflowId,
  dataflowTag,
  view,
}: {
  graph: GraphResponse;
  dataflowId: number;
  dataflowTag: string;
  view: GraphView;
}) {
  const { studio } = useTheme();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [error, setError] = useState<string | null>(null);

  const svg = () =>
    graphToSvg(
      graph,
      `${dataflowTag} · versão ${graph.version} · visão de ${view === 'transformation' ? 'transformações' : 'datasets'}`,
    );
  const filename = `${dataflowTag}-v${graph.version}-${view}`;
  const close = () => setAnchor(null);

  return (
    <>
      <Tooltip title={error ?? 'Exportar o grafo inteiro desta visão'}>
        <Box
          component="button"
          type="button"
          aria-haspopup="menu"
          aria-expanded={anchor !== null}
          onClick={(event: React.MouseEvent<HTMLElement>) => setAnchor(event.currentTarget)}
          sx={{
            height: 29,
            px: 1.25,
            borderRadius: '8px',
            border: `1px solid ${error ? studio.crit : studio.border}`,
            bgcolor: studio.elev,
            color: studio.ink,
            fontSize: 12,
            fontWeight: 500,
            fontFamily: 'inherit',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 0.75,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
            '&:hover': { borderColor: studio.muted },
            '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
          }}
        >
          <ExportIcon size={14} />
          Exportar
        </Box>
      </Tooltip>
      <Menu anchorEl={anchor} open={anchor !== null} onClose={close}>
        <MenuItem
          dense
          onClick={() => {
            setError(null);
            downloadSvg(`${filename}.svg`, svg());
            close();
          }}
        >
          Imagem SVG (vetorial)
        </MenuItem>
        <MenuItem
          dense
          onClick={() => {
            setError(null);
            downloadPng(`${filename}.png`, svg()).catch((cause: unknown) =>
              setError(cause instanceof Error ? cause.message : 'Não foi possível gerar o PNG.'),
            );
            close();
          }}
        >
          Imagem PNG
        </MenuItem>
        <MenuItem
          dense
          onClick={() => {
            window.open(provJsonUrl(dataflowId), '_blank');
            close();
          }}
        >
          PROV-JSON (W3C)
        </MenuItem>
      </Menu>
    </>
  );
}
