import { describe, expect, it } from 'vitest';
import type { ExecutionSummary } from '../api/catalog';
import { withinPeriod } from './period';

const execution = (id: number, startTime: string | null) => ({ id, startTime }) as ExecutionSummary;
const now = new Date('2026-10-09T18:00:00');
const all = [
  execution(1, '2026-10-09 14:18:57'),
  execution(2, '2026-10-04 21:06:00'),
  execution(3, '2026-09-20 09:00:00'),
  execution(4, null),
];

describe('recorte de execuções por período', () => {
  it('todo o histórico devolve tudo, inclusive execução sem horário', () => {
    expect(withinPeriod(all, 'all', now).map((e) => e.id)).toEqual([1, 2, 3, 4]);
  });

  it('sete dias corta pelo início e deixa de fora a que não tem horário', () => {
    expect(withinPeriod(all, '7d', now).map((e) => e.id)).toEqual([1, 2]);
  });

  it('vinte e quatro horas', () => {
    expect(withinPeriod(all, '24h', now).map((e) => e.id)).toEqual([1]);
  });
});
