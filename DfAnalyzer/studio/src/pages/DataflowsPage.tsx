import { Alert, AlertTitle, Box, Menu, MenuItem, Skeleton, Stack, useTheme } from '@mui/material';
import { useMemo, useState } from 'react';
import {
  provJsonUrl,
  useDataflowSummaries,
  useOverview,
  type DataflowStatus,
  type DataflowSummary,
} from '../api/catalog';
import { OriginChip } from '../components/OriginChip';
import { Chip, Mono, Segmented, StudioCard, Tile } from '../components/primitives';
import { CheckIcon, ChevronIcon, ExportIcon, RefreshIcon, SearchIcon } from '../layout/icons';
import { MONO } from '../theme/tokens';
import type { ChangeEvent, KeyboardEvent, MouseEvent, ReactNode } from 'react';

type StatusFilter = 'all' | 'generated' | 'running' | 'finished';

const PAGE_SIZE = 25;

export function DataflowsPage({
  onOpenDataflow,
}: {
  onOpenDataflow: (id: number, tag: string) => void;
}) {
  const overview = useOverview();
  const [page, setPage] = useState(0);
  const dataflows = useDataflowSummaries(page, PAGE_SIZE);
  const [filter, setFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');

  const rows = useMemo(() => {
    const items = dataflows.data?.items ?? [];
    const needle = filter.trim().toLowerCase();
    return items.filter((item) => {
      const matchesText =
        !needle || item.tag.toLowerCase().includes(needle) || String(item.id) === needle;
      const matchesStatus =
        statusFilter === 'all' ||
        (statusFilter === 'generated' && (item.origin === 'generated' || item.origin === 'curated')) ||
        (statusFilter === 'running' && item.status === 'RUNNING') ||
        (statusFilter === 'finished' && item.status === 'FINISHED');
      return matchesText && matchesStatus;
    });
  }, [dataflows.data, filter, statusFilter]);

  return (
    <>
      <PageHeader
        onRefresh={() => void Promise.all([dataflows.refetch(), overview.refetch()])}
        exportable={rows}
      />

      <Tiles />

      <StudioCard sx={{ flexGrow: 1, overflow: 'hidden' }}>
        <CardHeader
          filter={filter}
          onFilter={setFilter}
          statusFilter={statusFilter}
          onStatusFilter={setStatusFilter}
        />

        <Box sx={{ flexGrow: 1, overflow: 'auto', minHeight: 0 }}>
          {dataflows.isError && (
            <Box sx={{ p: 2 }}>
              <Alert
                severity="error"
                action={
                  <Box
                    component="button"
                    type="button"
                    onClick={() => void dataflows.refetch()}
                    sx={{ background: 'none', border: 0, color: 'inherit', cursor: 'pointer' }}
                  >
                    Tentar de novo
                  </Box>
                }
              >
                <AlertTitle>Não foi possível carregar os dataflows</AlertTitle>
                {dataflows.error instanceof Error ? dataflows.error.message : 'Erro desconhecido.'}
              </Alert>
            </Box>
          )}

          {dataflows.isPending && <LoadingRows />}

          {!dataflows.isPending && !dataflows.isError && rows.length === 0 && (
            <EmptyState hasAny={(dataflows.data?.total ?? 0) > 0} />
          )}

          {!dataflows.isPending && !dataflows.isError && rows.length > 0 && (
            <DataflowTable rows={rows} onOpen={onOpenDataflow} />
          )}
        </Box>

        <CardFooter
          shown={rows.length}
          total={dataflows.data?.total ?? 0}
          page={page}
          hasMore={dataflows.data?.hasMore ?? false}
          onPage={setPage}
        />
      </StudioCard>
    </>
  );
}

function PageHeader({
  onRefresh,
  exportable,
}: {
  onRefresh: () => void;
  exportable: DataflowSummary[];
}) {
  const { studio } = useTheme();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  return (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      spacing={1.75}
      alignItems={{ xs: 'stretch', md: 'flex-end' }}
    >
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Box component="h1" sx={{ m: 0, fontSize: 21, fontWeight: 600, letterSpacing: '-.01em' }}>
          Dataflows
        </Box>
        <Box sx={{ mt: 0.5, fontSize: 12.5, color: studio.muted }}>
          Proveniência capturada pelo DfAnalyzer · MonetDB <Mono>dataflow_analyzer</Mono>
        </Box>
      </Box>

      <Stack direction="row" spacing={1} sx={{ flex: 'none', flexWrap: 'wrap', rowGap: 1 }}>
        <GhostButton onClick={onRefresh} icon={<RefreshIcon size={14} />}>
          Atualizar
        </GhostButton>
        <GhostButton
          disabled={exportable.length === 0}
          title={
            exportable.length === 0
              ? 'Nenhum dataflow na lista para exportar'
              : 'Proveniência prospectiva de um dataflow no formato W3C PROV-JSON'
          }
          ariaHasPopup
          onClick={(event) => setAnchor(event.currentTarget)}
          icon={<ExportIcon size={14} />}
        >
          Exportar PROV-JSON
        </GhostButton>
        <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
          {exportable.map((item) => (
            <MenuItem
              key={item.id}
              dense
              component="a"
              href={provJsonUrl(item.id)}
              download={`${item.tag}.prov.json`}
              onClick={() => setAnchor(null)}
              sx={{ fontFamily: MONO, fontSize: 12.5 }}
            >
              {item.tag}
            </MenuItem>
          ))}
        </Menu>
      </Stack>
    </Stack>
  );
}

function GhostButton({
  children,
  icon,
  onClick,
  disabled,
  title,
  ariaHasPopup,
}: {
  children: ReactNode;
  icon?: ReactNode;
  onClick?: (event: MouseEvent<HTMLElement>) => void;
  disabled?: boolean;
  title?: string;
  ariaHasPopup?: boolean;
}) {
  const { studio } = useTheme();
  return (
    <Box
      component="button"
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      aria-haspopup={ariaHasPopup ? 'menu' : undefined}
      sx={{
        height: 34,
        px: 1.625,
        borderRadius: '9px',
        border: `1px solid ${studio.border}`,
        bgcolor: studio.elev,
        color: studio.ink,
        fontSize: 13,
        fontWeight: 500,
        fontFamily: 'inherit',
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.875,
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.45 : 1,
        whiteSpace: 'nowrap',
        '&:hover:not(:disabled)': { borderColor: studio.muted },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      {icon}
      {children}
    </Box>
  );
}

function Tiles() {
  const { data, isPending, isError } = useOverview();
  const queueHealthy = (data?.queuePending ?? 0) === 0;

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'repeat(2, minmax(0,1fr))', md: 'repeat(4, minmax(0,1fr))' },
        gap: 1.5,
      }}
    >
      <Tile label="Dataflows" value={metric(data?.dataflows, isPending, isError)} />
      <Tile
        label="Execuções · 7 dias"
        value={metric(data?.executionsLast7Days, isPending, isError)}
        footer={
          !isPending && !isError && data ? (
            <Box sx={{ mt: 0.25, fontSize: 11.5 }}>{data.executions.toLocaleString('pt-BR')} no total</Box>
          ) : undefined
        }
      />
      <Tile label="Tasks ingeridas" value={metric(data?.tasks, isPending, isError)} />
      <Tile
        label="Fila de ingestão"
        value={metric(data?.queuePending, isPending, isError)}
        suffix="pendentes"
        footer={
          !isPending && !isError ? (
            <Box sx={{ mt: 0.25 }}>
              <IngestionHealth
                queueHealthy={queueHealthy}
                errors={data?.ingestionErrors ?? 0}
                lastError={data?.lastIngestionError ?? null}
              />
            </Box>
          ) : undefined
        }
      />
    </Box>
  );
}

function IngestionHealth({
  queueHealthy,
  errors,
  lastError,
}: {
  queueHealthy: boolean;
  errors: number;
  lastError: string | null;
}) {
  if (errors > 0) {
    return (
      <Box title={lastError ?? undefined}>
        <Chip tone="crit">
          {errors} {errors === 1 ? 'falha' : 'falhas'} de ingestão
        </Chip>
      </Box>
    );
  }
  if (!queueHealthy) {
    return <Chip tone="warn">drenando</Chip>;
  }
  return (
    <Chip tone="good" icon={<CheckIcon size={14} />}>
      saudável
    </Chip>
  );
}

function metric(value: number | undefined, isPending: boolean, isError: boolean) {
  if (isPending) return <Skeleton width={48} height={28} />;
  if (isError || value === undefined) return '—';
  return value.toLocaleString('pt-BR');
}

function CardHeader({
  filter,
  onFilter,
  statusFilter,
  onStatusFilter,
}: {
  filter: string;
  onFilter: (value: string) => void;
  statusFilter: StatusFilter;
  onStatusFilter: (value: StatusFilter) => void;
}) {
  const { studio } = useTheme();
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={1.25}
      alignItems={{ xs: 'stretch', sm: 'center' }}
      sx={{ px: 2, py: 1.625, borderBottom: `1px solid ${studio.border}`, flex: 'none' }}
    >
      <Box
        component="h2"
        sx={{ m: 0, fontSize: 13, fontWeight: 600, letterSpacing: '.02em', flexGrow: 1 }}
      >
        Todos os dataflows
      </Box>

      <Box sx={{ flex: 'none' }}>
        <Segmented
          ariaLabel="Filtrar por status"
          value={statusFilter}
          onChange={onStatusFilter}
          options={[
            { value: 'all', label: 'Todos' },
            { value: 'generated', label: 'Gerados' },
            { value: 'running', label: 'Rodando' },
            { value: 'finished', label: 'Concluídos' },
          ]}
        />
      </Box>

      <Box
        sx={{
          width: { xs: '100%', sm: 200 },
          height: 29,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1.25,
          borderRadius: '9px',
          border: `1px solid ${studio.border}`,
          bgcolor: studio.page,
          color: studio.muted,
          '&:focus-within': { borderColor: studio.blue },
        }}
      >
        <SearchIcon size={15} />
        <Box
          component="input"
          value={filter}
          onChange={(event: ChangeEvent<HTMLInputElement>) => onFilter(event.target.value)}
          placeholder="Filtrar"
          aria-label="Filtrar dataflows por tag ou id"
          sx={{
            flexGrow: 1,
            minWidth: 0,
            border: 0,
            outline: 'none',
            bgcolor: 'transparent',
            color: studio.ink,
            fontFamily: 'inherit',
            fontSize: 13,
            '&::placeholder': { color: studio.muted },
          }}
        />
      </Box>
    </Stack>
  );
}

const COLUMNS = [
  { key: 'tag', label: 'DATAFLOW', width: 230 },
  { key: 'structure', label: 'ESTRUTURA', width: undefined },
  { key: 'versions', label: 'VERSÕES', width: 90 },
  { key: 'executions', label: 'EXECUÇÕES', width: 110 },
  { key: 'tasks', label: 'TASKS', width: 90 },
  { key: 'lastRun', label: 'ÚLTIMA', width: 150 },
  { key: 'origin', label: 'ORIGEM DO MAPEAMENTO', width: 190 },
  { key: 'duration', label: 'DURAÇÃO MÉD.', width: 120 },
  { key: 'status', label: 'STATUS', width: 140 },
  { key: 'chevron', label: '', width: 40 },
];

function DataflowTable({
  rows,
  onOpen,
}: {
  rows: DataflowSummary[];
  onOpen: (id: number, tag: string) => void;
}) {
  const { studio } = useTheme();
  return (
    <Box sx={{ px: 0.5, pt: 1.75, overflowX: 'auto' }}>
      <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
        <Box component="thead">
          <Box component="tr">
            {COLUMNS.map((column) => (
              <Box
                component="th"
                key={column.key}
                sx={{
                  width: column.width,
                  textAlign: 'left',
                  fontWeight: 500,
                  color: studio.muted,
                  fontSize: 11.5,
                  letterSpacing: '.03em',
                  px: 1.5,
                  pb: 1,
                  borderBottom: `1px solid ${studio.border}`,
                  whiteSpace: 'nowrap',
                }}
              >
                {column.label}
              </Box>
            ))}
          </Box>
        </Box>
        <Box component="tbody">
          {rows.map((row) => (
            <Box
              component="tr"
              key={row.id}
              role="button"
              tabIndex={0}
              aria-label={`Abrir o grafo de ${row.tag}`}
              onClick={() => onOpen(row.id, row.tag)}
              onKeyDown={(event: KeyboardEvent) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onOpen(row.id, row.tag);
                }
              }}
              sx={{
                cursor: 'pointer',
                '&:hover td': { bgcolor: studio.blueDim },
                '&:focus-visible td': { bgcolor: studio.blueDim },
                '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: -2 },
              }}
            >
              <Cell primary>
                <Mono sx={{ fontSize: 12.5 }}>{row.tag}</Mono>
              </Cell>
              <Cell>
                {row.transformations} transformações · {row.datasets} datasets
              </Cell>
              <Cell numeric>{row.versions}</Cell>
              <Cell numeric>{row.executions}</Cell>
              <Cell numeric>{row.tasks}</Cell>
              <Cell numeric>{formatLastRun(row.lastRun)}</Cell>
              <Cell>
                <OriginChip origin={row.origin} generator={row.generator} />
              </Cell>
              <Cell numeric>{formatDuration(row.avgDurationSeconds)}</Cell>
              <Cell>
                <StatusChip status={row.status} />
              </Cell>
              <Cell align="right">
                <Box component="span" sx={{ color: studio.muted }}>
                  <ChevronIcon size={13} />
                </Box>
              </Cell>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}

function Cell({
  children,
  primary,
  numeric,
  align,
}: {
  children: ReactNode;
  primary?: boolean;
  numeric?: boolean;
  align?: 'right';
}) {
  const { studio } = useTheme();
  return (
    <Box
      component="td"
      sx={{
        px: 1.5,
        py: 1.125,
        borderBottom: `1px solid ${studio.border}`,
        color: primary ? studio.ink : studio.muted,
        fontWeight: primary ? 500 : 400,
        textAlign: align,
        whiteSpace: 'nowrap',
        fontVariantNumeric: numeric ? 'tabular-nums' : undefined,
      }}
    >
      {children}
    </Box>
  );
}

function StatusChip({ status }: { status: DataflowStatus }) {
  switch (status) {
    case 'RUNNING':
      return <Chip tone="blue">Em execução</Chip>;
    case 'FINISHED':
      return (
        <Chip tone="good" icon={<CheckIcon size={14} />}>
          Concluído
        </Chip>
      );
    case 'READY':
      return <Chip tone="warn">Aguardando</Chip>;
    default:
      return <Chip>Sem tasks</Chip>;
  }
}

function CardFooter({
  shown,
  total,
  page,
  hasMore,
  onPage,
}: {
  shown: number;
  total: number;
  page: number;
  hasMore: boolean;
  onPage: (page: number) => void;
}) {
  const { studio } = useTheme();
  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={1.25}
      sx={{
        flex: 'none',
        px: 2,
        py: 1.5,
        borderTop: `1px solid ${studio.border}`,
        fontSize: 12,
        color: studio.muted,
      }}
    >
      <Box sx={{ flexGrow: 1 }}>
        {shown} de {total} {total === 1 ? 'dataflow' : 'dataflows'}
      </Box>
      <Stack direction="row" spacing={0.75} sx={{ flex: 'none' }}>
        <GhostButton disabled={page === 0} onClick={() => onPage(Math.max(page - 1, 0))}>
          Anterior
        </GhostButton>
        <GhostButton disabled={!hasMore} onClick={() => onPage(page + 1)}>
          Próxima
        </GhostButton>
      </Stack>
    </Stack>
  );
}

function EmptyState({ hasAny }: { hasAny: boolean }) {
  const { studio } = useTheme();
  return (
    <Box sx={{ p: 6, textAlign: 'center' }}>
      <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>
        {hasAny ? 'Nenhum dataflow bate com o filtro' : 'Nenhum dataflow ainda'}
      </Box>
      <Box sx={{ fontSize: 12.5, color: studio.muted }}>
        {hasAny
          ? 'Ajuste o texto ou o status.'
          : 'Rode um script instrumentado com dfa-lib-python para registrar o primeiro.'}
      </Box>
    </Box>
  );
}

function LoadingRows() {
  return (
    <Stack spacing={1.25} sx={{ p: 2 }} aria-busy="true" aria-label="Carregando dataflows">
      {[0, 1, 2, 3, 4].map((row) => (
        <Skeleton key={row} variant="rounded" height={30} />
      ))}
    </Stack>
  );
}

/** `yyyy-MM-dd HH:mm:ss` → `dd/MM · HH:mm`. */
function formatLastRun(value: string | null): string {
  if (!value) return '—';
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(value);
  return match ? `${match[3]}/${match[2]} · ${match[4]}:${match[5]}` : value;
}

function formatDuration(seconds: number | null): string {
  if (seconds === null || Number.isNaN(seconds)) return '—';
  if (seconds < 1) return `${Math.round(seconds * 1000)} ms`;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${String(Math.round(seconds % 60)).padStart(2, '0')}s`;
}
