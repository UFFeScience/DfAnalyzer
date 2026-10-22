import { Box, useTheme } from '@mui/material';
import { useState } from 'react';
import type { IngestionThroughput } from '../api/catalog';
import { Chip } from '../components/primitives';

const WIDTH = 288;
const HEIGHT = 64;

/** Derivado de performance.endtime. */
export function Throughput({ data }: { data: IngestionThroughput }) {
  const { studio } = useTheme();
  const points = polylinePoints(data.hourlyTaskCounts);
  const [asTable, setAsTable] = useState(false);
  const hours = data.hourlyTaskCounts.length;

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mt: 1.25 }}>
        <Box sx={{ fontSize: 30, fontWeight: 600, letterSpacing: '-.02em' }}>
          {data.tasksPerMinute.toFixed(1).replace('.', ',')}
        </Box>
        <Box sx={{ fontSize: 12.5, color: studio.muted }}>tasks/min</Box>
        {data.changePercent != null && (
          <Chip tone={data.changePercent >= 0 ? 'good' : 'warn'}>
            {data.changePercent >= 0 ? '+' : ''}
            {data.changePercent.toFixed(0)}% vs. 12h antes
          </Chip>
        )}
      </Box>

      <Box component="svg" width={WIDTH} height={HEIGHT} sx={{ mt: 1.5, overflow: 'visible' }}>
        <polyline
          points={points}
          fill="none"
          stroke={studio.blue}
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {points && (
          <circle
            cx={points.split(' ').at(-1)?.split(',')[0]}
            cy={points.split(' ').at(-1)?.split(',')[1]}
            r={4}
            fill={studio.blue}
          />
        )}
      </Box>

      <Box
        sx={{
          display: 'flex', justifyContent: 'space-between', fontSize: 10.5,
          color: studio.muted, mt: 0.75, fontVariantNumeric: 'tabular-nums',
        }}
      >
        <span>24h atrás</span>
        <span>12h atrás</span>
        <span>agora</span>
      </Box>

      <Box
        component="button"
        type="button"
        aria-expanded={asTable}
        onClick={() => setAsTable((v) => !v)}
        sx={{
          mt: 1.25, p: 0, border: 0, bgcolor: 'transparent', color: studio.blue, fontFamily: 'inherit',
          fontSize: 12, cursor: 'pointer', '&:hover': { textDecoration: 'underline' },
        }}
      >
        {asTable ? 'Ocultar tabela' : 'Ver tabela'}
      </Box>
      {asTable && (
        <Box
          component="table"
          sx={{
            mt: 1, width: '100%', borderCollapse: 'collapse', fontSize: 11.5, fontVariantNumeric: 'tabular-nums',
            '& th': { textAlign: 'left', color: studio.muted, fontWeight: 500, pb: 0.5 },
            '& td, & th': { borderBottom: `1px solid ${studio.border}`, py: 0.375 },
            '& td:last-of-type, & th:last-of-type': { textAlign: 'right' },
          }}
        >
          <thead>
            <tr>
              <th>Hora</th>
              <th>Tasks concluídas</th>
            </tr>
          </thead>
          <tbody>
            {data.hourlyTaskCounts.map((count, index) => (
              <tr key={index}>
                <td>{hours - 1 - index === 0 ? 'última hora' : `${hours - 1 - index}h atrás`}</td>
                <td>{count.toLocaleString('pt-BR')}</td>
              </tr>
            ))}
          </tbody>
        </Box>
      )}
    </Box>
  );
}

function polylinePoints(hourly: number[]): string {
  if (hourly.length === 0) return '';
  const max = Math.max(...hourly, 1);
  const stepX = WIDTH / (hourly.length - 1 || 1);
  return hourly
    .map((value, i) => {
      const x = Math.round(i * stepX);
      const y = Math.round(HEIGHT - (value / max) * (HEIGHT - 8) - 4);
      return `${x},${y}`;
    })
    .join(' ');
}
