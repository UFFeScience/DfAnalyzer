import { describe, expect, it } from 'vitest';
import type { QueryRequest } from '../api/catalog';
import { queryToNotebook } from './notebook';

const request: QueryRequest = {
  mapping: 'LOGICAL',
  sources: ['vendas_brutas'],
  targets: ['metricas_agregadas'],
  projections: ['vendas_brutas.total', 'metricas_agregadas.categoria'],
  selections: ["metricas_agregadas.categoria = 'D''Avila'", 'vendas_brutas.total >= 10'],
  execution: 24,
  page: 3,
  size: 50,
};

function notebook(extra: Partial<Parameters<typeof queryToNotebook>[0]> = {}) {
  return JSON.parse(
    queryToNotebook({
      baseUrl: 'http://localhost:22000',
      dataflowId: 9,
      dataflowTag: 'vendastest',
      request,
      exportedAt: '04/10/2026 18:00',
      ...extra,
    }),
  );
}

function source(cell: { source: string[] }): string {
  return cell.source.join('');
}

describe('exportação da consulta como notebook', () => {
  it('é um notebook nbformat 4 com células de markdown e código', () => {
    const nb = notebook();
    expect(nb.nbformat).toBe(4);
    expect(nb.cells.map((c: { cell_type: string }) => c.cell_type)).toEqual(['markdown', 'code', 'code']);
    for (const cell of nb.cells.filter((c: { cell_type: string }) => c.cell_type === 'code')) {
      expect(cell.outputs).toEqual([]);
      expect(cell.execution_count).toBeNull();
    }
  });

  it('carrega a consulta exatamente como a tela enviou, sem a paginação da tela', () => {
    const config = source(notebook().cells[1]);
    const embedded = /CONSULTA = json\.loads\(r"""([\s\S]*)"""\)/.exec(config)?.[1];
    const { page: _page, size: _size, ...expected } = request;
    expect(JSON.parse(embedded!)).toEqual(expected);
  });

  it('aponta para o dataflow e o servidor de onde foi exportada', () => {
    const config = source(notebook().cells[1]);
    expect(config).toContain('DFA_URL = "http://localhost:22000"');
    expect(config).toContain('DATAFLOW_ID = 9');
  });

  it('refaz a consulta pela API v2 e pagina até o fim', () => {
    const run = source(notebook().cells[2]);
    expect(run).toContain('/api/v2/dataflows/{DATAFLOW_ID}/query');
    expect(run).toContain('if not dados["hasMore"]');
  });

  it('inclui a SQL da tela só quando houve execução', () => {
    expect(notebook().cells).toHaveLength(3);
    const withSql = notebook({ sql: 'SELECT a FROM b' });
    expect(source(withSql.cells[3])).toContain('SELECT a FROM b');
  });
});
