import { Box, useTheme } from '@mui/material';
import type { ExecutionSummary, ExecutionTimeline } from '../api/catalog';
import { formatCount, formatPercentDelta, formatSeconds } from './format';
import { MONO } from '../theme/tokens';

/** Compara contadores e duração de duas execuções, no total e por transformação. */
export function ExecutionCompare({
  a,
  b,
  timelineA,
  timelineB,
}: {
  a: ExecutionSummary;
  b: ExecutionSummary;
  timelineA: ExecutionTimeline | undefined;
  timelineB: ExecutionTimeline | undefined;
}) {
  const { studio } = useTheme();

  const rows = [
    { label: 'Tasks', va: a.tasks, vb: b.tasks, fmt: formatCount },
    { label: 'Tasks com erro', va: a.errorTasks, vb: b.errorTasks, fmt: formatCount },
    { label: 'Tasks em execução', va: a.runningTasks, vb: b.runningTasks, fmt: formatCount },
    { label: 'Duração', va: a.durationSeconds, vb: b.durationSeconds, fmt: formatSeconds },
  ];

  const transformations = mergeTransformations(timelineA, timelineB);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25, minWidth: 0 }}>
      <Table
        head={['MÉTRICA', `#${a.id} · ${a.tag}`, `#${b.id} · ${b.tag}`, 'Δ']}
        rows={rows.map((row) => [
          row.label,
          row.fmt(row.va),
          row.fmt(row.vb),
          deltaCell(row.va, row.vb, row.fmt === formatSeconds),
        ])}
      />

      {transformations.length > 0 && (
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ fontSize: 11.5, color: studio.muted, fontWeight: 500, mb: 0.75 }}>
            Duração por transformação
          </Box>
          <Table
            head={['TRANSFORMAÇÃO', `#${a.id}`, `#${b.id}`, 'Δ']}
            rows={transformations.map((row) => [
              row.tag,
              formatSeconds(row.durationA),
              formatSeconds(row.durationB),
              formatPercentDelta(row.durationA, row.durationB) ??
                (row.durationA == null || row.durationB == null ? 'só numa execução' : 'sem variação'),
            ])}
            mono
          />
        </Box>
      )}
    </Box>
  );
}

function deltaCell(va: number | null, vb: number | null, isDuration: boolean): string {
  if (va == null || vb == null) return '—';
  const pct = formatPercentDelta(va, vb);
  if (isDuration) return pct ?? '—';
  if (va === vb) return 'igual';
  const diff = vb - va;
  return pct ?? `${diff > 0 ? '+' : ''}${diff}`;
}

function mergeTransformations(a: ExecutionTimeline | undefined, b: ExecutionTimeline | undefined) {
  const tags = new Set<string>();
  (a?.rows ?? []).forEach((r) => tags.add(r.transformationTag));
  (b?.rows ?? []).forEach((r) => tags.add(r.transformationTag));
  return Array.from(tags)
    .sort()
    .map((tag) => ({
      tag,
      durationA: a?.rows.find((r) => r.transformationTag === tag)?.durationSeconds ?? null,
      durationB: b?.rows.find((r) => r.transformationTag === tag)?.durationSeconds ?? null,
    }));
}

function Table({ head, rows, mono }: { head: string[]; rows: string[][]; mono?: boolean }) {
  const { studio } = useTheme();
  return (
    <Box sx={{ overflowX: 'auto', minWidth: 0 }}>
      <Box component="table" sx={{ width: '100%', minWidth: 420, borderCollapse: 'collapse', fontSize: 12.5 }}>
      <Box component="thead">
        <Box component="tr">
          {head.map((h, i) => (
            <Box
              component="th"
              key={h}
              sx={{
                textAlign: i === 0 ? 'left' : 'right',
                fontWeight: 500,
                color: studio.muted,
                fontSize: 11.5,
                letterSpacing: '.03em',
                pb: 0.75,
                pr: i === head.length - 1 ? 0 : 1.5,
                borderBottom: `1px solid ${studio.border}`,
              }}
            >
              {h}
            </Box>
          ))}
        </Box>
      </Box>
      <Box component="tbody">
        {rows.map((row, ri) => (
          <Box component="tr" key={ri}>
            {row.map((cell, ci) => (
              <Box
                component="td"
                key={ci}
                sx={{
                  py: 0.875,
                  pr: ci === row.length - 1 ? 0 : 1.5,
                  textAlign: ci === 0 ? 'left' : 'right',
                  borderBottom: `1px solid ${studio.border}`,
                  color: ci === 0 ? studio.ink : studio.muted,
                  fontWeight: ci === 0 ? 500 : 400,
                  fontFamily: mono && ci === 0 ? MONO : 'inherit',
                  fontVariantNumeric: ci > 0 ? 'tabular-nums' : undefined,
                  whiteSpace: 'nowrap',
                }}
              >
                {cell}
              </Box>
            ))}
          </Box>
        ))}
      </Box>
    </Box>
    </Box>
  );
}
