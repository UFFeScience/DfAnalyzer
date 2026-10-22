import { Box, Tooltip, useTheme } from '@mui/material';
import type { TimelineRow } from '../api/catalog';
import { formatCount, formatSeconds } from './format';
import { MONO } from '../theme/tokens';

const LABEL_WIDTH = 132;
const VALUE_WIDTH = 60;

/** Uma barra por transformação, a partir do início da execução. */
export function Timeline({ rows }: { rows: TimelineRow[] }) {
  const { studio } = useTheme();

  if (rows.length === 0) {
    return (
      <Box sx={{ fontSize: 12.5, color: studio.muted, py: 2 }}>
        Nenhuma task com registro de desempenho nesta execução.
      </Box>
    );
  }

  const span = Math.max(...rows.map((r) => r.startOffsetSeconds + r.durationSeconds), 1);
  const ticks = buildTicks(span);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
      <Box sx={{ display: 'flex', pl: `${LABEL_WIDTH + 8}px`, pr: `${VALUE_WIDTH}px` }}>
        <Box sx={{ position: 'relative', flexGrow: 1, height: 14 }}>
          {ticks.map((t) => (
            <Box
              key={t}
              sx={{
                position: 'absolute',
                left: `${(t / span) * 100}%`,
                transform: 'translateX(-50%)',
                fontSize: 10.5,
                color: studio.muted,
                fontVariantNumeric: 'tabular-nums',
                whiteSpace: 'nowrap',
              }}
            >
              {formatSeconds(t)}
            </Box>
          ))}
        </Box>
      </Box>

      {rows.map((row) => {
        const left = (row.startOffsetSeconds / span) * 100;
        const width = Math.max((row.durationSeconds / span) * 100, 0.6);
        const bad = row.errorTaskCount > 0;
        return (
          <Box key={row.transformationTag} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box
              sx={{
                width: LABEL_WIDTH,
                flex: 'none',
                textAlign: 'right',
                fontFamily: MONO,
                fontSize: 11.5,
                color: studio.muted,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
              title={row.transformationTag}
            >
              {row.transformationTag}
            </Box>
            <Box sx={{ position: 'relative', flexGrow: 1, height: 20 }}>
              <Tooltip
                title={`${row.transformationTag} · ${formatCount(row.taskCount)} tasks · ${formatSeconds(row.durationSeconds)}${bad ? ` · ${row.errorTaskCount} com erro` : ''}`}
              >
                <Box
                  sx={{
                    position: 'absolute',
                    top: 0,
                    height: 20,
                    borderRadius: '4px',
                    left: `${left}%`,
                    width: `${width}%`,
                    bgcolor: bad ? studio.crit : studio.blue,
                    cursor: 'default',
                  }}
                />
              </Tooltip>
            </Box>
            <Box
              sx={{
                width: VALUE_WIDTH,
                flex: 'none',
                fontSize: 11,
                color: studio.muted,
                fontVariantNumeric: 'tabular-nums',
                whiteSpace: 'nowrap',
              }}
            >
              {formatSeconds(row.durationSeconds)}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}

/** Cerca de cinco marcas redondas no eixo. */
function buildTicks(span: number): number[] {
  const step = niceStep(span / 5);
  const ticks: number[] = [];
  for (let t = 0; t <= span; t += step) {
    ticks.push(Math.round(t * 100) / 100);
  }
  return ticks;
}

function niceStep(raw: number): number {
  if (raw <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const normalized = raw / magnitude;
  const rounded = normalized < 1.5 ? 1 : normalized < 3 ? 2 : normalized < 7 ? 5 : 10;
  return rounded * magnitude;
}
