import type { ExecutionSummary } from '../api/catalog';

export type Period = 'all' | '7d' | '24h';

export const PERIOD_LABEL: Record<Period, string> = {
  all: 'Todo o histórico',
  '7d': 'Últimos 7 dias',
  '24h': 'Últimas 24 horas',
};

const HOURS: Record<Exclude<Period, 'all'>, number> = { '7d': 24 * 7, '24h': 24 };

/** Execuções iniciadas no período; sem horário de início, só entram em "todo o histórico". */
export function withinPeriod(executions: ExecutionSummary[], period: Period, now: Date): ExecutionSummary[] {
  if (period === 'all') return executions;
  const cutoff = now.getTime() - HOURS[period] * 3600 * 1000;
  return executions.filter((execution) => {
    if (!execution.startTime) return false;
    // O banco guarda o horário sem fuso; é lido como horário local.
    const started = new Date(execution.startTime.replace(' ', 'T')).getTime();
    return !Number.isNaN(started) && started >= cutoff;
  });
}
