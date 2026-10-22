import { Alert, Box, Skeleton, Stack, useTheme } from '@mui/material';
import { useMemo, useState } from 'react';
import {
  useDatasetSnapshot,
  type ExecutionSummary,
  type QueryRequest,
  type QueryResult,
} from '../api/catalog';
import { formatCount } from './format';
import { MONO } from '../theme/tokens';

interface DatasetOption {
  tag: string;
  attributes: { name: string; type: string | null }[];
}

/** Linhas lidas por execução. */
const SNAPSHOT_SIZE = 200;

/** Compara atributos por resumo agregado, porque o esquema não tem chave para parear linhas. */
export function AttributeCompare({
  dataflowId,
  datasets,
  execA,
  execB,
}: {
  dataflowId: number;
  datasets: DatasetOption[];
  execA: ExecutionSummary;
  execB: ExecutionSummary;
}) {
  const { studio } = useTheme();
  const [datasetTag, setDatasetTag] = useState<string | null>(datasets[0]?.tag ?? null);
  const dataset = datasets.find((d) => d.tag === datasetTag) ?? null;

  const requestA = useMemo(
    () => buildRequest(dataset, execA.id, execA.version),
    [dataset, execA.id, execA.version],
  );
  const requestB = useMemo(
    () => buildRequest(dataset, execB.id, execB.version),
    [dataset, execB.id, execB.version],
  );
  const snapshotA = useDatasetSnapshot(dataflowId, requestA);
  const snapshotB = useDatasetSnapshot(dataflowId, requestB);

  if (datasets.length === 0) {
    return (
      <Box sx={{ fontSize: 12.5, color: studio.muted }}>
        Nenhum dataset com atributos neste dataflow.
      </Box>
    );
  }

  return (
    <Box sx={{ minWidth: 0 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.25 }}>
        <Box sx={{ fontSize: 11.5, color: studio.muted }}>Dataset</Box>
        <Box
          component="select"
          value={datasetTag ?? ''}
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setDatasetTag(e.target.value)}
          sx={{
            height: 28, borderRadius: '7px', border: `1px solid ${studio.border}`,
            bgcolor: studio.page, color: studio.ink, fontSize: 11.5, fontFamily: 'inherit', px: 1,
          }}
        >
          {datasets.map((d) => (
            <option key={d.tag} value={d.tag}>{d.tag}</option>
          ))}
        </Box>
        {snapshotA.data && snapshotB.data && (
          <Box sx={{ fontSize: 11, color: studio.muted, ml: 'auto' }}>
            #{execA.id}: {formatCount(snapshotA.data.rows.length)}
            {snapshotA.data.hasMore ? '+' : ''} linhas · #{execB.id}:{' '}
            {formatCount(snapshotB.data.rows.length)}{snapshotB.data.hasMore ? '+' : ''} linhas
          </Box>
        )}
      </Stack>

      {(snapshotA.isError || snapshotB.isError) && (
        <Alert severity="error">
          {snapshotA.error instanceof Error ? snapshotA.error.message
            : snapshotB.error instanceof Error ? snapshotB.error.message
              : 'Não foi possível comparar este dataset.'}
        </Alert>
      )}

      {(snapshotA.isPending || snapshotB.isPending) && !snapshotA.isError && !snapshotB.isError && (
        <Skeleton variant="rounded" height={160} />
      )}

      {snapshotA.data && snapshotB.data && (
        <AttributeTable a={snapshotA.data} b={snapshotB.data} execA={execA} execB={execB} />
      )}
    </Box>
  );
}

function buildRequest(
  dataset: DatasetOption | null,
  execution: number,
  version: number,
): QueryRequest | null {
  if (!dataset || dataset.attributes.length === 0) return null;
  return {
    // Sem a versão, o backend usa a mais recente e recusa execução de outra versão.
    version,
    mapping: 'LOGICAL',
    sources: [dataset.tag],
    targets: [],
    projections: dataset.attributes.map((a) => `${dataset.tag}.${a.name}`),
    selections: [],
    execution,
    page: 0,
    size: SNAPSHOT_SIZE,
  };
}

function AttributeTable({
  a, b, execA, execB,
}: {
  a: QueryResult; b: QueryResult; execA: ExecutionSummary; execB: ExecutionSummary;
}) {
  const { studio } = useTheme();
  const rows = a.columns.map((column, i) => ({
    attribute: column.name.split('.').slice(1).join('.') || column.name,
    a: summarize(a, i),
    b: summarize(b, i),
  }));

  return (
    <Box sx={{ overflowX: 'auto', minWidth: 0 }}>
      <Box component="table" sx={{ width: '100%', minWidth: 420, borderCollapse: 'collapse', fontSize: 12.5 }}>
      <Box component="thead">
        <Box component="tr">
          {['ATRIBUTO', `#${execA.id}`, `#${execB.id}`].map((h, i) => (
            <Box
              component="th" key={h}
              sx={{
                textAlign: i === 0 ? 'left' : 'left', fontWeight: 500, color: studio.muted,
                fontSize: 11.5, letterSpacing: '.03em', pb: 0.75, pr: 1.5,
                borderBottom: `1px solid ${studio.border}`,
              }}
            >
              {h}
            </Box>
          ))}
        </Box>
      </Box>
      <Box component="tbody">
        {rows.map((row) => {
          const changed = row.a !== row.b;
          return (
            <Box component="tr" key={row.attribute}>
              <Box
                component="td"
                sx={{
                  py: 0.875, pr: 1.5, fontFamily: MONO, fontSize: 11.5,
                  color: studio.ink, fontWeight: 500, borderBottom: `1px solid ${studio.border}`,
                  whiteSpace: 'nowrap',
                }}
              >
                {row.attribute}
              </Box>
              <Box
                component="td"
                sx={{ py: 0.875, pr: 1.5, color: studio.muted, borderBottom: `1px solid ${studio.border}` }}
              >
                {row.a}
              </Box>
              <Box
                component="td"
                sx={{
                  py: 0.875, pr: 1.5, borderBottom: `1px solid ${studio.border}`,
                  color: changed ? studio.orange : studio.muted,
                  fontWeight: changed ? 500 : 400,
                }}
              >
                {row.b}
              </Box>
            </Box>
          );
        })}
      </Box>
      </Box>
    </Box>
  );
}

/** Resumo de uma coluna: mín/média/máx se toda numérica, senão moda + distintos. */
function summarize(result: QueryResult, columnIndex: number): string {
  const values = result.rows.map((row) => row[columnIndex]).filter((v): v is string => v != null);
  if (values.length === 0) return '—';

  const numbers = values.map(Number);
  const allNumeric = numbers.every((n) => Number.isFinite(n));
  if (allNumeric) {
    const min = Math.min(...numbers);
    const max = Math.max(...numbers);
    const avg = numbers.reduce((a, b) => a + b, 0) / numbers.length;
    return min === max
      ? formatNumber(min)
      : `${formatNumber(avg)} méd. (${formatNumber(min)}–${formatNumber(max)})`;
  }

  const counts = new Map<string, number>();
  for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1);
  const distinct = counts.size;
  const [mode] = [...counts.entries()].sort((x, y) => y[1] - x[1])[0];
  return distinct === 1 ? mode : `${mode} (+${distinct - 1} outros)`;
}

function formatNumber(n: number): string {
  return Number.isInteger(n) ? n.toLocaleString('pt-BR') : n.toFixed(2).replace('.', ',');
}
